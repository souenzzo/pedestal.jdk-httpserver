# pedestal-jdk-http

> A pedestal backend
> using [jdk.httpserver](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.httpserver/module-summary.html)

# Goals

Develop a low-dependency http(s) backend for pedestal, using the build-in JDK server.

# Usage

```clojure
br.dev.zz/pedestal.jdk-httpserver {:git/url "https://github.com/souenzzo/pedestal.jdk-httpserver"
                                   :git/sha "77f135f63f7e9839dabb4fe07b91a877857366e7"}
```

```clojure
(require '[br.dev.zz.pedestal.jdk-httpserver :as jh]
  '[io.pedestal.http :as http])
(-> {::http/type   jh/server
     ::http/port   8080
     ::http/join?  false
     ::http/routes ...}
  http/default-interceptors
  http/create-server
  http/start)
```

# Developing

## Kondo

```shell
clojure -M:dev:kondo
```

## Test

```shell
clojure -M:dev:test-runner
```

# TODO

- [x] Async support
- [ ] HTTPS
- [ ] WS
