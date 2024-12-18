(ns br.dev.zz.pedestal-container-tests.status-code-test
  (:require [br.dev.zz.pedestal-container-tests :as pct]
            [clojure.test :refer [deftest is]]))

(deftest simple-202-status
  (with-open [server (pct/start (fn [_]
                                  {:status 202}))]
    (is (= (:status (pct/send server {}))
          202))))
