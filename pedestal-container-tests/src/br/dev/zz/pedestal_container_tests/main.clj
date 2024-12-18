(ns br.dev.zz.pedestal-container-tests.main
  (:require [clojure.test :as test]
            [br.dev.zz.pedestal-container-tests.status-code-test]))

(defn -main
  [& _]
  (test/run-tests 'br.dev.zz.pedestal-container-tests.status-code-test))
