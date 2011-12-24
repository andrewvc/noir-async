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

(fact "responses with strings get properly converted to 200 OK maps"
  (send-request "/request-str") => anything
  (provided
    (enqueue-and-close anything good-response-map) => truthy))

(async/defpage-async "/request-map" {}
  (async/respond good-response-map))

(fact "responses with maps are passed through to the channel"
  (send-request "/request-map") => anything
  (provided
    (enqueue-and-close anything good-response-map) => truthy))
