# noir-async

![build status](https://secure.travis-ci.org/andrewvc/noir-async.png)

*NOTE: This module is currently being refactored. The README below applies to the released jar, but a new version, with a new, improved API will be out soon. Check out the source to this branch for more details.*

Support for asynchronous requests / responses in Noir using Aleph

## Usage

noir-async supports a simplified API for asynchronous requests and websockets.
Support for chunked responses, and parsing streaming request bodies is coming
soon.

It's easy to install using leiningen: `[noir-async "0.1.2"]`

Examples (for a fuller example see: [noir-async-chat](https://github.com/andrewvc/noir-async-chat ) )

```clojure
; Defining an asynchronous page with a single response
(defpage-async "/page/:foo" {:keys [foo]} conn
  (respond conn (str "ohai! " foo)))

; Defining a websocket
(defwebsocket "/sockets/:sname" {:keys [sname]} conn
  (send-message conn "Hello client!")
  (on-receive conn (fn [msg] (println "Got a mesage!")))
  (on-close conn (fn [] (println "Socket down!"))))

; Retreiving the raw channel and header information from aleph
; in an async handler
(let [rc (:request-channel conn)
      rh (:ring-request conn)] ...)
```

To run tests `lein midje`

## Setting up server.clj

In your server.clj, you'll want to use aleph as a server explicitly.
Be sure to replace noir-async-chat with the appropriate namespace.

```clojure
(ns noir-async-chat.server
  (:use aleph.http
        noir.core
        lamina.core)
  (:require
    [noir.server :as nr-server] ))

(nr-server/load-views "src/noir_async_chat/views/")

(defn -main [& m]
  (let [mode (keyword (or (first m) :dev))
        port (Integer. (get (System/getenv) "PORT" "3000"))
        noir-handler (nr-server/gen-handler {:mode mode})]
    (start-http-server
      (wrap-ring-handler noir-handler)
      {:port port :websocket true})))
```

## License

Copyright (C) 2011 Andrew Cholakian

Distributed under the Eclipse Public License, the same as Clojure.


