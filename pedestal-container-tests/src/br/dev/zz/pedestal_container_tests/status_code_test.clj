(ns br.dev.zz.pedestal-container-tests.status-code-test
  (:require [br.dev.zz.pedestal-container-tests :as pct]
            [clojure.test :refer [deftest is]]))

(comment
  (System/setProperty "br.dev.zz.pedestal-container-tests.type" ":jetty"))

(deftest simple-202-status
  (with-open [server (pct/start-enter-interceptor
                       (fn [ctx]
                         (assoc ctx :response {:status 202})))]
    (is (= (:status (pct/send server {}))
          202))))
