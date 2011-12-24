(ns noir-async.test.core
  (:use noir.core
        noir.util.test
        aleph.http
        lamina.core
        midje.sweet)
  (:require [noir-async.core :as async]))

(def good-response-str "ohai!")
(def good-response-map {:status 200 :body good-response-str})

(async/defpage-async "/request-str" {} conn
  (async/respond conn good-response-str))

(fact "page responses with strings get properly converted to 200 OK maps"
  (send-request "/request-str") => anything
  (provided
    (enqueue-and-close anything good-response-map) => truthy))

(async/defpage-async "/request-map" {} conn
  (async/respond conn good-response-map))

(fact "page responses with maps are passed through to the channel"
  (send-request "/request-map") => anything
  (provided
    (enqueue-and-close anything good-response-map) => truthy))

(let [ws-body-call-count (atom 0)]
  (async/defwebsocket "/websocket-open" {} conn
    (swap! ws-body-call-count inc))

  (facts "websocket opens should execute the defwebsocket body"
    (send-request "/websocket-open") => anything
    (deref ws-body-call-count) => 1))

(let [test-ch (channel)
      conn (async/create-websocket-connection test-ch)
      ws-messages-recvd (atom [])
      close-call-count (atom 0)]
   
  (async/on-receive conn (fn [msg] (swap! ws-messages-recvd conj msg)))
  (async/on-close   conn (fn [] (swap! close-call-count inc)))

  (fact "on-receive should trigger callback for all messages"
    (do (enqueue test-ch "amsg")
        (some #{"amsg"} @ws-messages-recvd)) => truthy)
 
  (fact "send-message should enqueue the message onto the channel"
    (let [msg "send-message-msg"]
      (async/send-message conn msg)
      (some #{msg} @ws-messages-recvd)) => truthy)
 
  (fact "on-close should be triggered when the channel closes"
    (do (close test-ch) @close-call-count) => 1))
