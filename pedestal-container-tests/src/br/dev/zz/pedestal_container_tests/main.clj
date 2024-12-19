(ns br.dev.zz.pedestal-container-tests.main
  (:require [clojure.test :as test]))

(def namespaces
  '[br.dev.zz.pedestal-container-tests.status-code-test])

(defn -main
  [& _]
  (apply serialized-require namespaces)
  (apply test/run-tests namespaces))
