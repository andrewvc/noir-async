(ns noir-async.test.core
  (:use noir.core
        noir.util.test
        aleph.http
        lamina.core
        midje.sweet)
  (:require [noir-async.core :as async]
            [noir.server :as nr-server] ))

(def good-response-str "ohai!")
(def good-response-map {:status 200 :body good-response-str})

(defn send-async-request
  "Sends a fake async request"
  [route & params]
  (let [n-req (make-request route params)]
    

(async/defpage-async "/as-test" {} conn
  (async/apush conn good-response-str))

(start-http-server
 (wrap-ring-handler (nr-server/gen-handler))
 {:port 3000 :websocket true})

(println "Server started")

(Thread/sleep 20000)

;(fact "page responses with strings get properly converted to 200 OK maps"
;  (println (send-request "/as-test")))
