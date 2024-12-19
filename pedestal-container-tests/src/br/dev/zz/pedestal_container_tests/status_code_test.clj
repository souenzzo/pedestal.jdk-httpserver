(ns br.dev.zz.pedestal-container-tests.status-code-test
  (:require [br.dev.zz.pedestal-container-tests :as pct]
            [clojure.test :refer [deftest is]]))

(comment
  (System/setProperty "br.dev.zz.pedestal-container-tests.type" ":jetty"))

(deftest simple-202-status
  (with-open [server (pct/start-enter-interceptor
                       (fn [ctx]
                         (assoc ctx :response {:status 202})))]
    (is (= 202
          (:status (pct/send server {}))))))


(def all-http-statues
  "Not all, but a bunch for testing"
  #{100
    200 201 202 203 204 205 206
    300 301 302 303 304 305
    400 401 402 403 404 405 406 407 408 409 410 411 412 413 414 415
    500 501 502 503 504 505})

