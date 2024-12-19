(ns br.dev.zz.pedestal-container-tests.response-headers-test
  (:require [br.dev.zz.pedestal-container-tests :as pct]
            [clojure.edn :as edn]
            [clojure.test :refer [deftest is]]))

(deftest response-with-text-plain
  (with-open [server (pct/start-enter-interceptor
                       (fn [ctx]
                         (assoc ctx :response {:headers {"Content-Type" "text/plain"}
                                               :status  202})))]
    (is (= "text/plain"
          (-> (pct/send server {})
            :headers
            (get "content-type"))))))

(deftest response-with-text-plain-and-more
  (with-open [server (pct/start-enter-interceptor
                       (fn [ctx]
                         (assoc ctx :response {:headers {"Content-Type" "text/plain;charset=UTF-16;version=1"}
                                               :status  202})))]
    (is (= "text/plain;charset=UTF-16;version=1"
          (-> (pct/send server {})
            :headers
            (get "content-type"))))))

(deftest echo-on-headers
  (with-open [server (pct/start-enter-interceptor
                       (fn [{:keys [request] :as ctx}]
                         (assoc ctx :response {:headers {"request-map" (str (dissoc request
                                                                              :body
                                                                              :servlet
                                                                              :headers
                                                                              :context-path
                                                                              :servlet-request
                                                                              :servlet-response
                                                                              :servlet-context
                                                                              :async-supported?
                                                                              :pedestal.http.impl.servlet-interceptor/protocol
                                                                              :pedestal.http.impl.servlet-interceptor/async-supported?))}
                                               :status  202})))]
    (is (= {:path-info        "/foo/bar/baz"
            :protocol         "HTTP/1.1"
            :query-string     "surname=jones&age=123"
            :remote-addr      "127.0.0.1"
            :request-method   :get
            :scheme           :http
            :server-name      "localhost"
            :server-port      8080
            :uri              "/foo/bar/baz"}
          (-> (pct/send server {:uri          "/foo/bar/baz"
                                :query-string "surname=jones&age=123"})

            :headers
            (get "request-map")
            edn/read-string
            (select-keys [:protocol :remote-addr :server-port :path-info :uri :server-name :query-string :scheme :request-method]))))))
