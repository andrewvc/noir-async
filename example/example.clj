(ns noir-async-example
  (:use aleph.http
        noir-async.core)
  (:require
    [noir.server :as nr-server] ))

                                        ;
; Note, same syntax as noir's defpage, but with "conn" parameter
(defpage-async "/route" [] conn
  (async-push conn {:status 404 :body "Couldn't find it!"}))

(defpage-async "/echo" [] conn
  (on-receive conn (fn echo-cb [m] (async-push conn m))))

(defpage-async "/always-chunky" [] conn
  ;; Sending the header explicitly indicates a chunked response
  (async-push conn {:status 200 :chunked true})
  (async-push conn "chunk one")
  (async-push conn "chunk two")
  (close conn))

(defn start-server [& m]
  (let [mode (keyword (or (first m) :dev))
        port (Integer. (get (System/getenv) "PORT" "3000"))
        noir-handler (nr-server/gen-handler {:mode mode})]
    (start-http-server
      (wrap-ring-handler noir-handler)
      {:port port :websocket true})))