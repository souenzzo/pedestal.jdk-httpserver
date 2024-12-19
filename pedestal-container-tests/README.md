# pedestal-container-tests

## Usage

### Running with our test runner

```clojure
:aliases {:pedestal-container-tests {:extra-deps {br.dev.zz/pedestal-container-tests {:git/url   "https://github.com/souenzzo/pedestal.jdk-httpserver"
                                                                                      :deps/root "pedestal-container-tests"
                                                                                      :git/sha   "77f135f63f7e9839dabb4fe07b91a877857366e7"}}
                                     :jvm-opts   ["-Dbr.dev.zz.pedestal-container-tests.type=my.container/server"]
                                     :main-opts  ["-m" "br.dev.zz.pedestal-container-tests.main"]}}
```

### Running programmatically

```clojure
(ns my.container.test-runner
  (:require [br.dev.zz.pedestal-container-tests.main :as pct.main]
            [clojure.test :as test]))
(defn -main
  [& _]
  (System/setProperty "br.dev.zz.pedestal-container-tests.type" "my.container/server")
  (run! requiring-resolve pct.main/namespaces)
  (apply test/run-tests pct.main/namespaces))
```
