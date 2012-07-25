# noir-async [![Build Status](https://secure.travis-ci.org/andrewvc/noir-async.png?branch=master)](http://travis-ci.org/andrewvc/noir-async)

## Seamless, concise, async webservices for clojure.

noir-async integrates the [noir](https://github.com/ibdknox/noir) web framework with the [aleph](https://github.com/ztellman/aleph) async library with minimal syntax. With noir-async you can create well organized webapps, with both synchronous and asynchronous logic, and multiple asynchronous endpoints.

## How it works

The integration is fairly seamless. You will, however, need to setup a special `server.clj` file. Just modify the template included toward the bottom of this document.

For a running example a chatroom using a websocket can be found at [noir-async-chat](https://github.com/andrewvc/noir-async-chat). A larger project using noir-async is my [engulf](http://andrewvc.github.com/engulf/)

##  Some examples:

Add `[noir-async 1.1.0-beta7]` to your project.clj

Here's an example route that responds in one shot:

```clojure
; Note, same syntax as noir's defpage, but with "conn" parameter
(defpage-async "/route" [] conn
  (async-push conn {:status 404 :body "Couldn't find it!"}))
```

This is an example route that handles a websocket:

```clojure
(defpage-async "/echo" [] conn
  (on-receive conn (fn [m] (async-push conn m))))
```

Here's an example of responding in a chunked fashion:

```clojure
(defpage-async "/always-chunky" [] conn
  ;; Sending the header explicitly indicates a chunked response
  (async-push conn {:status 200 :chunked true})
  (async-push conn "chunk one")
  (async-push conn "chunk two")
  (close-connection conn))
```

Reading request bodies with aleph can be a little odd, so that interface is sugared:

```clojure
(defpage-async "/some-route" {} conn
  ;; You can also retrieve the body as a ByteBuffer. See full docs for details
  (async-push (str "Received body:" (request-body-str conn))))
```

Since it uses an identical interface for both websockets
and regular HTTP, if you want to handle them differently be
sure to use the websocket? and regular? functions to discern them.

## Using

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

Full Docs

## License

Copyright (C) 2011 Andrew Cholakian

Distributed under the Eclipse Public License, the same as Clojure.


