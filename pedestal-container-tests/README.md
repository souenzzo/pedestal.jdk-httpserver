# pedestal-container-tests

A suite to test if the pedestal container is fully implemented.

```clojure
br.dev.zz/pedestal-container-tests {:git/url   "https://github.com/souenzzo/pedestal.jdk-httpserver"
                                    :deps/root "pedestal-container-tests"
                                    :git/sha   "82bc041f68abb44b6fd0eb6ac319cd146c4924c5"}}
```

## Usage

The test suite will spawn the server defined in `br.dev.zz.pedestal-container-tests.type` property.

It can be eiter a keyword, like `:jetty`, or a symbol, like `io.pedestal.http.jetty/server`.


### Simple - use our test runner

```clojure
:aliases {:pedestal-container-tests {:extra-deps {br.dev.zz/pedestal-container-tests {...}}
                                     :jvm-opts   ["-Dbr.dev.zz.pedestal-container-tests.type=my.container/server"]
                                     :main-opts  ["-m" "br.dev.zz.pedestal-container-tests.runner"]}}
```

### Advanced - use our own test runner

```clojure
(ns my.container.test-runner
  (:require [br.dev.zz.pedestal-container-tests.runner :as pct.runner]
            [clojure.test :as test]))
(defn -main
  [& _]
  (System/setProperty "br.dev.zz.pedestal-container-tests.type" "my.container/server")
  (run! requiring-resolve pct.main/namespaces)
  (apply test/run-tests pct.main/namespaces))
```
