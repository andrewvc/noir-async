# noir-async

Support for asynchronous requests / responses in Noir using Aleph

## Usage

To run tests `lein midje`

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


