(ns br.dev.zz.pedestal.jdk-httpserver.testing-tools
  (:refer-clojure :exclude [send])
  (:require [clojure.string :as string]
            [io.pedestal.http :as http]
            [ring.core.protocols])
  (:import (java.lang AutoCloseable)
           (java.net URI)
           (java.net.http HttpClient HttpClient$Builder HttpClient$Version HttpHeaders HttpRequest HttpResponse
                          HttpResponse$BodySubscribers HttpResponse$ResponseInfo)
           (java.nio.charset StandardCharsets)
           (java.time Duration)
           (java.util Optional)))

(set! *warn-on-reflection* true)

(def ^:dynamic *http-client
  (delay
    (-> (HttpClient/newBuilder)
      (HttpClient$Builder/.connectTimeout (Duration/ofSeconds 2))
      (HttpClient$Builder/.version HttpClient$Version/HTTP_1_1)
      .build)))

(defn http-request
  ^HttpRequest [{:keys [uri request-method headers scheme server-name server-port query-string protocol body]
                 :as   ring-request}]
  (let [uri-uri (URI. (name scheme) nil server-name server-port uri query-string nil)
        method (-> request-method name string/upper-case)
        version (when (contains? ring-request :protocol)
                  (HttpClient$Version/valueOf (string/replace protocol #"[^A-Z0-9]" "_")))
        header-map (into {}
                     (map (fn [k vs]
                            [k (if (coll? vs)
                                 (mapv str vs)
                                 [(str vs)])]))
                     headers)]
    (proxy [HttpRequest] []
      (method [] method)
      (uri [] uri-uri)
      (timeout [] #_(Optional/empty) (Optional/of (Duration/ofSeconds 1)))
      (headers [] (HttpHeaders/of header-map (constantly true)))
      (version [] (if version
                    (Optional/of version)
                    (Optional/empty)))
      (expectContinue [] false)
      (bodyPublisher [] (if (contains? ring-request :body)
                          (Optional/of body)
                          (Optional/empty))))))

(defn open
  ^AutoCloseable
  [type path handler]
  (let [server (-> {::http/type   type
                    ::http/port   8080
                    ::http/join?  false
                    ::http/routes `#{[~path :any {:name  ::handler
                                                  :enter ~handler}
                                      :route-name ::hello]}}
                 http/default-interceptors
                 http/create-server
                 http/start)]
    (reify AutoCloseable
      (close [this]
        (http/stop server)))))

(defn ring-response
  [http-response]
  {:body    (HttpResponse/.body http-response)
   :headers (into {}
              (map (fn [[k v]]
                     [k (if (next v)
                          (vec v)
                          (first v))]))
              (HttpHeaders/.map (HttpResponse/.headers http-response)))
   :status  (HttpResponse/.statusCode http-response)})

(defn clean-headers
  [{:keys [headers] :as response}]
  (let [headers' (dissoc headers #_"content-length" "date" ":status")]
    (if (empty? headers')
      (dissoc response :headers)
      (assoc response :headers headers'))))

(defn body-handler
  ([response-info]
   (let [maybe-content-type (HttpHeaders/.firstValue (HttpResponse$ResponseInfo/.headers response-info) "Content-Type")]
     (if (Optional/.isPresent maybe-content-type)
       (case (Optional/.get maybe-content-type)
         "text/plain"
         (HttpResponse$BodySubscribers/ofString StandardCharsets/UTF_8))
       (HttpResponse$BodySubscribers/ofString StandardCharsets/UTF_8)))))

(defn send
  [request]
  (-> @*http-client
    (HttpClient/.send (http-request request) body-handler)
    ring-response))
