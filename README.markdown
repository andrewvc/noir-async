# noir-async

![build status](https://secure.travis-ci.org/andrewvc/noir-async.png)

## Seamless, concise, async webservices for clojure. Based on noir and aleph

noir-async integrates the aleph async library into noir allowing asynchronous programming with minimal syntax.

##  Examples:

A decent websocket example can be found at [noir-async-chat](https://github.com/andrewvc/noir-async-chat)

Note: this syntax is the same as noir's but with an additional conn (connection) parameter used to send responses through.
   
An example route that responds in one shot. On standard HTTP requests you can only respond with one message.

     (defpage-asyc "/route" [] conn
      (async-push {:status 404 :body \"Couldn't find it!\"}))

An example route that handles a websocket

     (defpage-async "/echo" [] conn
      (on-receive (fn [m] (async-push conn m))))

Using async-push-header will start a multipart response

     (defpage-asyc "/always-chunky" [] conn
       (async-push-header conn {:status 200})
       (async-push conn \"chunk one\")
       (async-push conn \"chunk two\")
       (close conn))

Since it uses an identical interface for both websockets
and regular HTTP, if you want to handle them differently be
sure to use the websocket? and regular? functions to discern them.

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


