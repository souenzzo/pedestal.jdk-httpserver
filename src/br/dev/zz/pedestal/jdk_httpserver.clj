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

#_org.eclipse.jetty.server.Request
(defn http-exchange->http-servlet-request
  [http-exchange]
  (reify HttpServletRequest
    (getProtocol [this] (HttpExchange/.getProtocol http-exchange))
    (getMethod [this] (HttpExchange/.getRequestMethod http-exchange))
    (getContentLengthLong [this] (or (some-> this (.getHeader "Content-Length") parse-long)
                                   -1))
    (getContentType [this] (.getHeader this "Content-Type"))
    (getAttribute [this _] nil)
    (getCharacterEncoding [this] nil)
    (getInputStream [this]
      (let [*in (delay (HttpExchange/.getRequestBody http-exchange))]
        (proxy [ServletInputStream] []
          (read [b off len]
            (InputStream/.read @*in b off len))
          (close []
            ;; maybe close here?
            #_(AutoCloseable/.close http-exchange)
            (AutoCloseable/.close @*in)))))
    (getRequestURI [this] (.getPath (HttpExchange/.getRequestURI http-exchange)))
    (getQueryString [this] (.getQuery (HttpExchange/.getRequestURI http-exchange)))
    (getScheme [this]
      (if (instance? HttpsExchange http-exchange)
        "https"
        "http"))
    (getServerName [this]
      (str (or (.getHost (.getRequestURI http-exchange))
             (some-> this (.getHeader "Host") (string/split #":") first))))
    (getContextPath [this]
      (let [context-path (.getPath (HttpExchange/.getHttpContext http-exchange))]
        (case context-path
          "/" ""
          context-path)))
    (isAsyncSupported [this] false)
    (isAsyncStarted [this] false)
    (getRemoteAddr [this] (.getHostAddress (.getAddress (HttpExchange/.getRemoteAddress http-exchange))))
    (getServerPort [this] (.getPort (.getAddress (.getServer (HttpExchange/.getHttpContext http-exchange)))))
    (getHeaderNames [this] (Collections/enumeration (.keySet (HttpExchange/.getRequestHeaders http-exchange))))
    (getHeaders [this name] (Collections/enumeration (.get (HttpExchange/.getRequestHeaders http-exchange) name)))
    (getHeader [this name] (first (.get (HttpExchange/.getRequestHeaders http-exchange) name)))))

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
                               (OutputStream/.write @*response-body b off len))
                             #_(close []
                                 ;; never called
                                 #_(AutoCloseable/.close http-exchange)
                                 (AutoCloseable/.close @*response-body))))
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
  [servlet {:keys [host port websockets container-options]}]
  (let [http-handler (reify HttpHandler
                       (handle [_this http-exchange]
                         (Servlet/.service servlet
                           (http-exchange->http-servlet-request http-exchange)
                           (http-exchange->http-servlet-response http-exchange))))
        addr (InetSocketAddress. port)
        server (HttpServer/create addr 0 (:context-path container-options "/")
                 http-handler
                 (into-array Filter []))]
    server))

#_io.pedestal.http.jetty/server
(defn server
  [{::http/keys [servlet]} {:keys [join?] :as options}]
  (let [server (create-server servlet options)]
    {:server   server
     :start-fn (fn []
                 (log/info :version :dev)
                 (HttpServer/.start server)
                 (log/info :started :server)
                 (when join?
                   #_(.join server)))
     :stop-fn  (fn []
                 (HttpServer/.stop server 0)
                 (log/info :stopped :server))}))
