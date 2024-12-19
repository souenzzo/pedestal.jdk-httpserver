(ns br.dev.zz.pedestal-container-tests.main
  (:require [clojure.test :as test]))

(def namespaces
  '[br.dev.zz.pedestal-container-tests.status-code-test])

(defn -main
  [& _]
  (run! requiring-resolve namespaces)
  (apply test/run-tests namespaces))
