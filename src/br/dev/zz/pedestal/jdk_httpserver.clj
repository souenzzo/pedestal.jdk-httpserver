(ns br.dev.zz.pedestal.jdk-httpserver
  ;; https://docs.oracle.com/en/java/javase/21/docs/api/jdk.httpserver/module-summary.html
  (:require [clojure.string :as string]
            [io.pedestal.http :as http]
            [io.pedestal.log :as log])
  (:import (com.sun.net.httpserver Filter Headers HttpExchange HttpHandler HttpServer HttpsExchange)
           (jakarta.servlet Servlet ServletInputStream ServletOutputStream)
           (jakarta.servlet.http HttpServletRequest HttpServletResponse)
           (java.io InputStream OutputStream)
           (java.lang AutoCloseable)
           (java.net InetSocketAddress)
           (java.util Collections Map)))

(set! *warn-on-reflection* true)

#_org.eclipse.jetty.server.Request
(defn http-exchange->http-servlet-request
  [http-exchange]
  (reify HttpServletRequest
    (getProtocol [_this] (HttpExchange/.getProtocol http-exchange))
    (getMethod [_this] (HttpExchange/.getRequestMethod http-exchange))
    (getContentLengthLong [this] (or (some-> this (.getHeader "Content-Length") parse-long)
                                   -1))
    (getContentType [this] (.getHeader this "Content-Type"))
    (getAttribute [_this _] nil)
    (getCharacterEncoding [_this] nil)
    (getInputStream [_this]
      (let [*in (delay (HttpExchange/.getRequestBody http-exchange))]
        (proxy [ServletInputStream] []
          (read [b off len]
            (InputStream/.read @*in b off len))
          (close []
            ;; maybe close here?
            #_(AutoCloseable/.close http-exchange)
            (log/info :close :stdin)
            (AutoCloseable/.close @*in)))))
    (getRequestURI [_this] (.getPath (HttpExchange/.getRequestURI http-exchange)))
    (getQueryString [_this] (.getQuery (HttpExchange/.getRequestURI http-exchange)))
    (getScheme [_this]
      (if (instance? HttpsExchange http-exchange)
        "https"
        "http"))
    (getServerName [this]
      (str (or (.getHost (HttpExchange/.getRequestURI http-exchange))
             (some-> this (.getHeader "Host") (string/split #":") first))))
    (getContextPath [_this]
      (let [context-path (.getPath (HttpExchange/.getHttpContext http-exchange))]
        (case context-path
          "/" ""
          context-path)))
    (isAsyncSupported [_this] false)
    (isAsyncStarted [_this] false)
    (getRemoteAddr [_this] (.getHostAddress (.getAddress (HttpExchange/.getRemoteAddress http-exchange))))
    (getServerPort [_this] (.getPort (.getAddress (.getServer (HttpExchange/.getHttpContext http-exchange)))))
    (getHeaderNames [_this] (Collections/enumeration (.keySet (HttpExchange/.getRequestHeaders http-exchange))))
    (getHeaders [_this name] (Collections/enumeration (.get (HttpExchange/.getRequestHeaders http-exchange) name)))
    (getHeader [_this name] (first (.get (HttpExchange/.getRequestHeaders http-exchange) name)))))

#_org.eclipse.jetty.server.Response
#_io.pedestal.http.impl.servlet-interceptor/send-response
(defn http-exchange->http-servlet-response
  [http-exchange]
  (let [*status (atom 200)
        *content-length (atom 0)
        *headers (delay (HttpExchange/.getResponseHeaders http-exchange))
        *response-body (delay
                         (HttpExchange/.sendResponseHeaders http-exchange @*status @*content-length)
                         (HttpExchange/.getResponseBody http-exchange))]
    (reify HttpServletResponse
      (getOutputStream [_] (proxy [ServletOutputStream] []
                             (write [b off len]
                               (OutputStream/.write @*response-body b off len))))
      (setStatus [_ status] (reset! *status status))
      (getStatus [_] @*status)
      (getBufferSize [_] 0)
      (setHeader [_ header value]
        (Map/.put @*headers header [value]))
      (addHeader [_ header value]
        (Headers/.add @*headers header value))
      (setContentType [_ content-type]
        (when content-type
          (Map/.put @*headers "Content-Type" [content-type])))
      (setContentLength [_ content-length]
        (reset! *content-length (long content-length)))
      (setContentLengthLong [_ content-length]
        (reset! *content-length (long content-length)))
      (flushBuffer [_]
        (OutputStream/.flush @*response-body)
        ;; TODO: Where to close?! - do not work with async!
        (AutoCloseable/.close http-exchange))
      (isCommitted [_]
        (realized? *response-body)))))

#_io.pedestal.http.jetty/create-server
(defn create-server
  [servlet {:keys [host port #_websockets container-options]}]
  (let [{:keys [context-path configurator]
         :or   {context-path "/"
                configurator identity}} container-options
        http-handler (reify HttpHandler
                       (handle [_this http-exchange]
                         (Servlet/.service servlet
                           (http-exchange->http-servlet-request http-exchange)
                           (http-exchange->http-servlet-response http-exchange))))
        addr (if (string? host)
               (InetSocketAddress. ^String host (int port))
               (InetSocketAddress. port))
        server (HttpServer/create addr 0)]
    (HttpServer/.createContext server context-path http-handler)
    (configurator server)))

#_io.pedestal.http.jetty/server
(defn server
  [{::http/keys [servlet]} {:keys [#_join?] :as options}]
  (let [server (create-server servlet options)]
    {:server   server
     :start-fn (fn []
                 (log/info :version :dev)
                 (HttpServer/.start server)
                 (log/info :started :server)
                 #_(when join?
                     (.join server)))
     :stop-fn  (fn []
                 (HttpServer/.stop server 0)
                 (log/info :stopped :server))}))
