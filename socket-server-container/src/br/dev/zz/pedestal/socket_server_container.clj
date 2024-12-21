(ns br.dev.zz.pedestal.socket-server-container
  (:require [clojure.core.async :as async]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [io.pedestal.http :as http]
            [io.pedestal.http.container :as container]
            [io.pedestal.log :as log])
  (:import (jakarta.servlet Servlet ServletInputStream ServletOutputStream)
           (jakarta.servlet.http HttpServletRequest HttpServletResponse)
           (java.io BufferedReader InputStream OutputStream)
           (java.lang AutoCloseable)
           (java.net InetAddress ServerSocket Socket)
           (java.time Instant LocalDateTime ZoneId ZoneOffset)
           (java.time.format DateTimeFormatter)
           (java.util Collections)))

(set! *warn-on-reflection* true)

(defn ->http-servlet-request
  [client-socket *async-context]
  (let [in (BufferedReader. (io/reader (Socket/.getInputStream client-socket)))
        [req-line & header-lines] (loop [headers []]
                                    (let [line (.readLine in)]
                                      (if (string/blank? line)
                                        headers
                                        (recur (conj headers line)))))
        [_ request-method path protocol] (re-find #"([^\s]+)\s([^\s]+)\s([^\s]+)" req-line)
        [path query-string] (string/split path #"\?" 2)
        headers (into {}
                  (map (fn [header-line]
                         (let [[k v] (string/split header-line #":\s{0,}" 2)]
                           [(string/lower-case k)
                            v])))
                  header-lines)
        *attributes (atom {})]
    (reify HttpServletRequest
      (getProtocol [_this] protocol)
      (getMethod [_this] request-method)
      (getContentLengthLong [_this] (or (some-> headers
                                          (get "content-length")
                                          parse-long)
                                      -1))
      (getContentType [this] (get headers "content-type"))
      (setAttribute [_this name o]
        (swap! *attributes assoc name o))
      (getAttribute [_this name]
        (get @*attributes name))
      (getCharacterEncoding [_this] nil)
      (getInputStream [_this]
        (proxy [ServletInputStream] []
          (read
            ([b]
             (InputStream/.read in b))
            ([b off len]
             (InputStream/.read in b off len)))
          (close []
            (AutoCloseable/.close in))))
      #_(getAsyncContext [_this]
          (reify AsyncContext
            ;; TODO
            (setTimeout [_this _timeout])
            (complete [_this]
              (AutoCloseable/.close http-exchange))))
      (getRequestURI [_this] path)
      (getQueryString [_this] query-string)
      (getScheme [_this]
        "http")
      (getServerName [this]
        (get headers "host"))
      (getContextPath [_this]
        ""
        #_(let [context-path (.getPath (HttpExchange/.getHttpContext http-exchange))]
            (case context-path
              "/" ""
              context-path)))
      (isAsyncSupported [_this] false)
      #_(startAsync [this] @*async-context
          (.getAsyncContext this))
      (isAsyncStarted [_this] (realized? *async-context))
      (getRemoteAddr [_this] "localhost")
      (getServerPort [_this] 8080)
      (getHeaderNames [_this] (Collections/enumeration (keys headers)))
      (getHeaders [_this name] (Collections/enumeration [(get headers name)]))
      (getHeader [_this name] (get headers name)))))

(defn date-header
  [inst]
  (.format DateTimeFormatter/RFC_1123_DATE_TIME
    (.atOffset (LocalDateTime/ofInstant inst
                 (ZoneId/of "GMT"))
      ZoneOffset/UTC)))

(defn ->http-servlet-response
  [client-socket *async-context]
  (let [*status (atom 200)
        *headers (atom {})
        *content-length (atom 0)
        *out (delay
               (let [out (Socket/.getOutputStream client-socket)]
                 (.write out (.getBytes (str "HTTP/1.1 " @*status " OK\r\n")))
                 (doseq [[k v] (assoc @*headers "Date" [(date-header (Instant/now))])])
                 (.write out (.getBytes (str "\r\n")))
                 (.write out (.getBytes (str "\r\n")))
                 out))]
    (reify HttpServletResponse
      (getOutputStream [_]
        (proxy [ServletOutputStream] []
          (write [b off len]
            (OutputStream/.write @*out b off len))
          (close []
            (AutoCloseable/.close @*out))))
      (setStatus [_ status] (reset! *status status))
      (getStatus [_] @*status)
      (getBufferSize [_] 0)
      (setHeader [_ header value]
        (swap! *headers assoc header [value]))
      (addHeader [_ header value]
        (swap! *headers update header (fnil conj []) value))
      (setContentType [_ content-type]
        (when content-type
          (swap! *headers assoc "Content-Type" [content-type])))
      (setContentLength [_ content-length]
        (reset! *content-length (long content-length)))
      (setContentLengthLong [_ content-length]
        (reset! *content-length (long content-length)))
      (flushBuffer [_]
        (OutputStream/.flush @*out)
        (OutputStream/.close @*out)
        #_(try
            (OutputStream/.flush @*response-body)
            (catch IOException _))
        #_(when-not (realized? *async-context)
            (AutoCloseable/.close http-exchange)))
      (isCommitted [_]
        (realized? *out))
      container/WriteNIOByteBody)))

(defprotocol IServer
  :extend-via-metadata true
  (start [this])
  (stop [this]))

#_io.pedestal.http.jetty/create-server
(defn create-server
  [servlet {:keys [host port container-options]}]
  (let [{:keys [context-path configurator #_ssl-port #_insecure-ssl? #_keystore #_ssl?]
         :or   {context-path "/"
                configurator identity}} container-options
        *async-context (delay
                         #_(let [c (async/chan)]
                             (future
                               (loop []
                                 (when-let [{:keys [body resume-chan context ^OutputStream response-body]} (async/<!! c)]
                                   (try
                                     (if (instance? ByteBuffer body)
                                       (.write (Channels/newChannel response-body) body)
                                       ;; TODO: Review performance!
                                       (let [bb (ByteBuffer/allocate #_0x10000 65536)]
                                         (loop []
                                           (let [n (ReadableByteChannel/.read body bb)]
                                             (when (pos-int? n)
                                               (.write response-body (.array bb) 0 n)
                                               (.clear bb)
                                               (recur))))))
                                     (async/put! resume-chan context)
                                     (async/close! resume-chan)
                                     (catch Throwable error
                                       (async/put! resume-chan (assoc context :io.pedestal.impl.interceptor/error error))
                                       (async/close! resume-chan)))
                                   (recur))))
                             c))
        http-handler (fn [client-socket]
                       (try
                         (Servlet/.service servlet
                           (->http-servlet-request client-socket *async-context)
                           (->http-servlet-response client-socket *async-context))
                         (catch Throwable ex
                           (def _ex ex)
                           (throw ex))))
        *socket-server (delay
                         (let [server-socket
                               (if host
                                 (ServerSocket. port 0 (InetAddress/getByName host))
                                 (ServerSocket. port 0))]
                           (future
                             (loop []
                               (with-open [client-socket (.accept server-socket)]
                                 (http-handler client-socket))
                               (recur)))
                           server-socket))
        server (with-meta {:*socket-server *socket-server}
                 `{start ~(fn [{:keys [*socket-server]
                                :as   this}]
                            @*socket-server
                            this)
                   stop  ~(fn [{:keys [*socket-server] :as this}]
                            (some-> *socket-server deref AutoCloseable/.close)
                            (dissoc this :*socket-server))})]
    (configurator server)))

#_io.pedestal.http.jetty/server
(defn server
  [{::http/keys [servlet]} {:keys [#_join?] :as options}]
  (let [server (create-server servlet options)]
    {:server   server
     :start-fn (fn []
                 (log/info :version :dev)
                 (start server)
                 (log/info :started :server)
                 #_(when join?
                     (.join server)))
     :stop-fn  (fn []
                 (stop server)
                 (log/info :stopped :server))}))
