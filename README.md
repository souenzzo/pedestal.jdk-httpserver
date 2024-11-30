# pedestal-jdk-http

> A pedestal backend
> using [jdk.httpserver](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.httpserver/module-summary.html)

# Goals

Develop a low-dependency http(s) backend for pedestal, using the build-in JDK server.

# Usage

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
