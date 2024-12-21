(ns br.dev.zz.pedestal-container-tests.response-body-test
  (:require [br.dev.zz.pedestal-container-tests :as pct]
            [clojure.test :refer [deftest is]]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route])
  (:import (java.net.http HttpRequest$BodyPublishers)
           (java.nio ByteBuffer)
           (java.nio.channels Pipe)))

(comment
  (System/setProperty "br.dev.zz.pedestal-container-tests.type" ":jetty")
  (System/setProperty "br.dev.zz.pedestal-container-tests.type" "br.dev.zz.pedestal.jdk-httpserver/server"))

(deftest http-roundtrip
  (with-open [server (pct/start-enter-interceptor
                       (fn [ctx]
                         (assoc ctx :response {:status  200
                                               :headers {"Content-Type" "text/plain"}
                                               :body    "Hello World"})))]
    (is (= {:body    "Hello World"
            :headers {"content-type"      "text/plain"
                      "transfer-encoding" "chunked"}
            :status  200}
          (-> server
            (pct/send {:request-method :post
                       :body           (HttpRequest$BodyPublishers/ofString "hello")})
            (update :headers dissoc "date"))))))
(deftest request-translation
  (with-open [server (pct/start-enter-interceptor
                       (fn [ctx]
                         (assoc ctx :response {:status 200
                                               :body   (-> ctx :request :body)})))]
    (is (= {:body    "hello"
            :headers {"content-type"      "application/octet-stream"
                      "transfer-encoding" "chunked"}
            :status  200}
          (-> server
            (pct/send {:request-method :post
                       :body           (HttpRequest$BodyPublishers/ofString "hello")})
            (update :headers dissoc "date"))))))



(deftest supports-nio-async-via-byte-buffers
  (with-open [server (pct/start-enter-interceptor
                       (fn [ctx]
                         (assoc ctx :response {:status  200
                                               :headers {"Content-Type" "text/plain"}
                                               :body    (ByteBuffer/wrap (.getBytes "Hello World" "UTF-8"))})))]
    (is (= {:body    "Hello World"
            :headers {"content-type"   "text/plain"}
            :status  200}
          (-> server
            (pct/send {})
            (update :headers dissoc "date" "content-length"   "transfer-encoding"))))))

(deftest supports-nio-async-via-readable-byte-channel
  (with-open [server (pct/start-enter-interceptor
                       (fn [ctx]
                         (assoc ctx :response (let [p (Pipe/open)
                                                    b (ByteBuffer/wrap (.getBytes "Hello World" "UTF-8"))
                                                    sink (.sink p)]
                                                (.write sink b)
                                                (.close sink)
                                                {:status  200
                                                 :headers {"Content-Type" "text/plain"}
                                                 :body    (.source p)}))))]
    (is (= {:body    "Hello World"
            :headers {"content-type"   "text/plain"}
            :status  200}
          (-> server
            (pct/send {})
            (update :headers dissoc "date" "content-length"   "transfer-encoding"))))))

(deftest test-run-jetty-custom-context-with-servletcontext
  (let [routes #{["/hello" :get (fn [_]
                                  {:body   (route/url-for :hello)
                                   :status 200})
                  :route-name :hello]}
        url-for-no-ctx (route/url-for-routes (route/expand-routes routes))
        url-for-with-ctx (route/url-for-routes (route/expand-routes routes)
                           :context "/context")]
    (with-open [server (pct/start
                         {::http/routes routes})]
      (is (= "/hello"
            (-> server
              (pct/send {:uri "/hello"})
              :body)))
      (is (= 404
            (-> server
              (pct/send {:uri "/context/hello"})
              :status))))
    (with-open [server (pct/start
                         {::http/routes            routes
                          ::http/container-options {:context-path "/context"}})]
      (is (= 404
            (-> server
              (pct/send {:uri "/hello"})
              :status)))
      (is (= "/context/hello"
            (-> server
              (pct/send {:uri "/context/hello"})
              :body))))
    (is (= "/hello"
          (url-for-no-ctx :hello)))
    (is (= "/context/hello"
          (url-for-with-ctx :hello)))))
