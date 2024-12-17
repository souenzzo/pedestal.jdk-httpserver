(ns br.dev.zz.pedestal.jdk-httpserver-test
  (:require [br.dev.zz.pedestal.jdk-httpserver :as jh]
            [br.dev.zz.pedestal.jdk-httpserver.testing-tools :as tt]
            [clojure.core.async :as async]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [io.pedestal.http.impl.servlet-interceptor :as servlet-interceptor]
            [clojure.test :refer [deftest is]]
            [ring.core.protocols])
  (:import (java.net.http HttpRequest$BodyPublishers)
           (java.nio ByteBuffer)
           (java.nio.channels Pipe)
           (java.nio.charset StandardCharsets)))

(set! *warn-on-reflection* true)

(deftest empty-202-response
  (with-open [server (tt/open :jetty "/hello"
                       (fn [ctx]
                         (assoc ctx :response {:status 202})))]
    (is (= {:body    ""
            :headers {"transfer-encoding"                 "chunked"
                      "content-security-policy"           "object-src 'none'; script-src 'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:;"
                      "strict-transport-security"         "max-age=31536000; includeSubdomains"
                      "x-content-type-options"            "nosniff"
                      "x-download-options"                "noopen"
                      "x-frame-options"                   "DENY"
                      "x-permitted-cross-domain-policies" "none"
                      "x-xss-protection"                  "1; mode=block"}
            :status  202}
          (-> {:scheme         :http
               :server-name    "localhost"
               :server-port    8080
               :uri            "/hello"
               :protocol       "HTTP/1.1"
               :request-method :get}
            tt/send
            tt/clean-headers
            #_(doto clojure.pprint/pprint)))))
  (with-open [server (tt/open jh/server "/hello"
                       (fn [ctx]
                         (assoc ctx :response {:status 202})))]
    (is (= {:body    ""
            :headers {"transfer-encoding"                 "chunked"
                      "content-security-policy"           "object-src 'none'; script-src 'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:;"
                      "strict-transport-security"         "max-age=31536000; includeSubdomains"
                      "x-content-type-options"            "nosniff"
                      "x-download-options"                "noopen"
                      "x-frame-options"                   "DENY"
                      "x-permitted-cross-domain-policies" "none"
                      "x-xss-protection"                  "1; mode=block"}
            :status  202}
          (-> {:scheme         :http
               :server-name    "localhost"
               :server-port    8080
               :uri            "/hello"
               :protocol       "HTTP/1.1"
               :request-method :get}
            tt/send
            tt/clean-headers
            #_(doto clojure.pprint/pprint))))))

(deftest std-404-response
  (with-open [server (tt/open :jetty "/hello"
                       (fn [ctx]
                         (assoc ctx :response {:status 202})))]
    (is (= {:body    "Not Found"
            :headers {"content-type"      "text/plain"
                      "transfer-encoding" "chunked"}
            :status  404}
          (-> {:scheme         :http
               :server-name    "localhost"
               :server-port    8080
               :uri            "/world"
               :protocol       "HTTP/1.1"
               :request-method :get}
            tt/send
            tt/clean-headers
            #_(doto clojure.pprint/pprint)))))
  (with-open [server (tt/open jh/server "/hello"
                       (fn [ctx]
                         (assoc ctx :response {:status 202})))]
    (is (= {:body    "Not Found"
            :headers {"content-type"      "text/plain"
                      "transfer-encoding" "chunked"}
            :status  404}
          (-> {:scheme         :http
               :server-name    "localhost"
               :server-port    8080
               :uri            "/world"
               :protocol       "HTTP/1.1"
               :request-method :get}
            tt/send
            tt/clean-headers
            #_(doto clojure.pprint/pprint))))))

(deftest simple-http-post
  (with-open [server (tt/open :jetty "/hello"
                       (fn [{:keys [request] :as ctx}]
                         (assoc ctx :response {:body   (slurp (:body request))
                                               :status 202})))]
    (is (= {:body    "world"
            :headers {"content-security-policy"           "object-src 'none'; script-src 'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:;"
                      "content-type"                      "text/plain"
                      "strict-transport-security"         "max-age=31536000; includeSubdomains"
                      "transfer-encoding"                 "chunked"
                      "x-content-type-options"            "nosniff"
                      "x-download-options"                "noopen"
                      "x-frame-options"                   "DENY"
                      "x-permitted-cross-domain-policies" "none"
                      "x-xss-protection"                  "1; mode=block"}
            :status  202}
          (-> {:scheme         :http
               :server-name    "localhost"
               :server-port    8080
               :uri            "/hello"
               :request-method :post
               :protocol       "HTTP/1.1"
               :body           (HttpRequest$BodyPublishers/ofString "world")}
            tt/send
            tt/clean-headers
            #_(doto clojure.pprint/pprint)))))
  (with-open [server (tt/open jh/server "/hello"
                       (fn [{:keys [request] :as ctx}]
                         (assoc ctx :response {:body   (slurp (:body request))
                                               :status 202})))]
    (is (= {:body    "world"
            :headers {"content-security-policy"           "object-src 'none'; script-src 'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:;"
                      "content-type"                      "text/plain"
                      "strict-transport-security"         "max-age=31536000; includeSubdomains"
                      "transfer-encoding"                 "chunked"
                      "x-content-type-options"            "nosniff"
                      "x-download-options"                "noopen"
                      "x-frame-options"                   "DENY"
                      "x-permitted-cross-domain-policies" "none"
                      "x-xss-protection"                  "1; mode=block"}
            :status  202}
          (-> {:scheme         :http
               :server-name    "localhost"
               :server-port    8080
               :uri            "/hello"
               :request-method :post
               :protocol       "HTTP/1.1"
               :body           (HttpRequest$BodyPublishers/ofString "world")}
            tt/send
            tt/clean-headers
            #_(doto clojure.pprint/pprint))))))

(deftest with-query-string
  (with-open [server (tt/open :jetty "/hello"
                       (fn [{:keys [request] :as ctx}]
                         (assoc ctx :response {:body   (pr-str (:query-params request))
                                               :status 200})))]
    (is (= {:body    "{:foo? \"bar\", nil \"33\"}"
            :headers {"content-security-policy"           "object-src 'none'; script-src 'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:;"
                      "content-type"                      "text/plain"
                      "strict-transport-security"         "max-age=31536000; includeSubdomains"
                      "transfer-encoding"                 "chunked"
                      "x-content-type-options"            "nosniff"
                      "x-download-options"                "noopen"
                      "x-frame-options"                   "DENY"
                      "x-permitted-cross-domain-policies" "none"
                      "x-xss-protection"                  "1; mode=block"}
            :status  200}
          (-> {:scheme         :http
               :server-name    "localhost"
               :server-port    8080
               :query-string   "foo?=bar&33"
               :uri            "/hello"
               :protocol       "HTTP/1.1"
               :request-method :get}
            tt/send
            tt/clean-headers
            #_(doto clojure.pprint/pprint)))))
  (with-open [server (tt/open jh/server "/hello"
                       (fn [{:keys [request] :as ctx}]
                         (assoc ctx :response {:body   (pr-str (:query-params request))
                                               :status 200})))]
    (is (= {:body    "{:foo? \"bar\", nil \"33\"}"
            :headers {"content-security-policy"           "object-src 'none'; script-src 'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:;"
                      "content-type"                      "text/plain"
                      "strict-transport-security"         "max-age=31536000; includeSubdomains"
                      "transfer-encoding"                 "chunked"
                      "x-content-type-options"            "nosniff"
                      "x-download-options"                "noopen"
                      "x-frame-options"                   "DENY"
                      "x-permitted-cross-domain-policies" "none"
                      "x-xss-protection"                  "1; mode=block"}
            :status  200}
          (-> {:scheme         :http
               :server-name    "localhost"
               :server-port    8080
               :query-string   "foo?=bar&33"
               :protocol       "HTTP/1.1"
               :uri            "/hello"
               :request-method :get}
            tt/send
            tt/clean-headers
            #_(doto clojure.pprint/pprint))))))



(deftest body-as-writeable-body
  (with-open [server (tt/open :jetty "/hello"
                       (fn [{:keys [request] :as ctx}]
                         (assoc ctx :response {:body   (reify servlet-interceptor/WriteableBody
                                                         (default-content-type [this])
                                                         (write-body-to-stream [this output-stream]
                                                           (with-open [w (io/writer output-stream)]
                                                             (.append w "hello")
                                                             (.append w "\n")
                                                             (.append w "world"))))
                                               :status 200})))]
    (is (= {:body    "hello\nworld"
            :headers {"content-security-policy"           "object-src 'none'; script-src 'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:;"
                      "strict-transport-security"         "max-age=31536000; includeSubdomains"
                      "transfer-encoding"                 "chunked"
                      "x-content-type-options"            "nosniff"
                      "x-download-options"                "noopen"
                      "x-frame-options"                   "DENY"
                      "x-permitted-cross-domain-policies" "none"
                      "x-xss-protection"                  "1; mode=block"}
            :status  200}
          (-> {:scheme         :http
               :server-name    "localhost"
               :server-port    8080
               :uri            "/hello"
               :protocol       "HTTP/1.1"
               :request-method :get}
            tt/send
            tt/clean-headers
            #_(doto clojure.pprint/pprint)))))
  (with-open [server (tt/open jh/server "/hello"
                       (fn [{:keys [request] :as ctx}]
                         (assoc ctx :response {:body   (reify servlet-interceptor/WriteableBody
                                                         (default-content-type [this])
                                                         (write-body-to-stream [this output-stream]
                                                           (with-open [w (io/writer output-stream)]
                                                             (.append w "hello")
                                                             (.append w "\n")
                                                             (.append w "world"))))
                                               :status 200})))]
    (is (= {:body    "hello\nworld"
            :headers {"content-security-policy"           "object-src 'none'; script-src 'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:;"
                      "strict-transport-security"         "max-age=31536000; includeSubdomains"
                      "transfer-encoding"                 "chunked"
                      "x-content-type-options"            "nosniff"
                      "x-download-options"                "noopen"
                      "x-frame-options"                   "DENY"
                      "x-permitted-cross-domain-policies" "none"
                      "x-xss-protection"                  "1; mode=block"}
            :status  200}
          (-> {:scheme         :http
               :server-name    "localhost"
               :server-port    8080
               :uri            "/hello"
               :protocol       "HTTP/1.1"
               :request-method :get}
            tt/send
            tt/clean-headers
            #_(doto clojure.pprint/pprint))))))

(deftest supports-nio-async-via-byte-buffers
  (with-open [server (tt/open jh/server "/hello"
                       (fn [context]
                         (assoc context
                           :response {:status  200
                                      :headers {"Content-Type" "text/plain"}
                                      :body    (ByteBuffer/wrap (String/.getBytes "Hello World" StandardCharsets/UTF_8))})))]
    (is (= {:body    "Hello World"
            :headers {"content-type"                      "text/plain"
                      "content-security-policy"           "object-src 'none'; script-src 'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:;"
                      "strict-transport-security"         "max-age=31536000; includeSubdomains"
                      "transfer-encoding"                 "chunked"
                      "x-content-type-options"            "nosniff"
                      "x-download-options"                "noopen"
                      "x-frame-options"                   "DENY"
                      "x-permitted-cross-domain-policies" "none"
                      "x-xss-protection"                  "1; mode=block"}
            :status  200}
          (-> {:scheme         :http
               :server-name    "localhost"
               :server-port    8080
               :uri            "/hello"
               :protocol       "HTTP/1.1"
               :request-method :get}
            tt/send
            tt/clean-headers
            #_(doto clojure.pprint/pprint))))))

(deftest supports-nio-async-via-byte-buffers
  (with-open [server (tt/open jh/server "/hello"
                       (fn [context]
                         (let [p (Pipe/open)
                               b (ByteBuffer/wrap (.getBytes "Hello World" "UTF-8"))
                               sink (.sink p)]
                           (.write sink b)
                           (.close sink)
                           (assoc context
                             :response
                             {:status  200
                              :headers {"Content-Type" "text/plain"}
                              :body    (.source p)}))))]
    (is (= {:body    "Hello World"
            :headers {"content-type"                      "text/plain"
                      "content-security-policy"           "object-src 'none'; script-src 'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:;"
                      "strict-transport-security"         "max-age=31536000; includeSubdomains"
                      "transfer-encoding"                 "chunked"
                      "x-content-type-options"            "nosniff"
                      "x-download-options"                "noopen"
                      "x-frame-options"                   "DENY"
                      "x-permitted-cross-domain-policies" "none"
                      "x-xss-protection"                  "1; mode=block"}
            :status  200}
          (-> {:scheme         :http
               :server-name    "localhost"
               :server-port    8080
               :uri            "/hello"
               :protocol       "HTTP/1.1"
               :request-method :get}
            tt/send
            tt/clean-headers
            #_(doto clojure.pprint/pprint))))))

(deftest simple-async
  (with-open [server (tt/open jh/server "/hello"
                       (fn [context]
                         (async/go (assoc context
                                     :response
                                     {:status  200
                                      :headers {"Content-Type" "text/plain"}
                                      :body  "ok"}))))]
    (is (= {:body    "ok"
            :headers {"content-type"                      "text/plain"
                      "content-security-policy"           "object-src 'none'; script-src 'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:;"
                      "strict-transport-security"         "max-age=31536000; includeSubdomains"
                      "transfer-encoding"                 "chunked"
                      "x-content-type-options"            "nosniff"
                      "x-download-options"                "noopen"
                      "x-frame-options"                   "DENY"
                      "x-permitted-cross-domain-policies" "none"
                      "x-xss-protection"                  "1; mode=block"}
            :status  200}
          (-> {:scheme         :http
               :server-name    "localhost"
               :server-port    8080
               :uri            "/hello"
               :protocol       "HTTP/1.1"
               :request-method :get}
            tt/send
            tt/clean-headers
            #_(doto clojure.pprint/pprint)))))
  (with-open [server (tt/open jh/server "/hello"
                       (fn [context]
                         (assoc context
                           :response
                           {:status  200
                            :headers {"Content-Type" "text/plain"}
                            :body    (async/to-chan!! ["ok" "abc"])})))]
    (is (= {:body    "okabc"
            :headers {"content-type"                      "text/plain"
                      "content-security-policy"           "object-src 'none'; script-src 'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:;"
                      "strict-transport-security"         "max-age=31536000; includeSubdomains"
                      "transfer-encoding"                 "chunked"
                      "x-content-type-options"            "nosniff"
                      "x-download-options"                "noopen"
                      "x-frame-options"                   "DENY"
                      "x-permitted-cross-domain-policies" "none"
                      "x-xss-protection"                  "1; mode=block"}
            :status  200}
          (-> {:scheme         :http
               :server-name    "localhost"
               :server-port    8080
               :uri            "/hello"
               :protocol       "HTTP/1.1"
               :request-method :get}
            tt/send
            tt/clean-headers
            #_(doto clojure.pprint/pprint))))))

