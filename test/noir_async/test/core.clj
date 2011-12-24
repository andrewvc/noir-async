(ns noir-async.test.core
  (:use noir.core
        noir.util.test
        aleph.http
        lamina.core
        midje.sweet)
  (:require [noir-async.core :as async]))

(def good-response-str "ohai!")
(def good-response-map {:status 200 :body good-response-str})

(async/defpage-async "/request-str" {}
  (async/respond good-response-str))

(fact "page responses with strings get properly converted to 200 OK maps"
  (send-request "/request-str") => anything
  (provided
    (enqueue-and-close anything good-response-map) => truthy))

(async/defpage-async "/request-map" {}
  (async/respond good-response-map))

(fact "page responses with maps are passed through to the channel"
  (send-request "/request-map") => anything
  (provided
    (enqueue-and-close anything good-response-map) => truthy))

(let [ws-body-call-count (atom 0)]
  (async/defwebsocket "/websocket-open" {}
    (swap! ws-body-call-count inc))

  (facts "websocket opens should execute the defwebsocket body"
    (send-request "/websocket-open") => anything
    (deref ws-body-call-count) => 1))

(let [mock-ch (channel)
      ws-messages-recvd (atom [])
      close-call-count (atom 0)]
   
  (async/defwebsocket "/websocket-receiver" {}
    (binding [async/*request-channel* mock-ch]
      (async/on-receive (fn [msg] (swap! ws-messages-recvd conj msg)))
      (async/on-close   (fn [] (swap! close-call-count inc)))
      (async/send-message "ohai!")))

  (send-request "/websocket-receiver")
   
  (fact "on-receive should trigger callback for all messages"
    (do (enqueue mock-ch "amsg")
        (some #{"amsg"} @ws-messages-recvd)) => truthy)
  
  (fact "on-close should be triggered when the channel closes"
    (do (close mock-ch) @close-call-count) => 1))
