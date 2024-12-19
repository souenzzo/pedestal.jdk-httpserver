(ns br.dev.zz.pedestal-container-tests.runner
  (:require [clojure.test :as test]))

(def namespaces
  '[br.dev.zz.pedestal-container-tests.status-code-test
    br.dev.zz.pedestal-container-tests.response-headers-test])

(defn -main
  [& _]
  (doseq [test-ns namespaces]
    (requiring-resolve (symbol (str test-ns) "_")))
  (try
    (let [{:keys [fail error]} (apply test/run-tests namespaces)]
      (System/exit (if (zero? (+ fail error)) 0 1)))
    (finally
      (shutdown-agents))))
