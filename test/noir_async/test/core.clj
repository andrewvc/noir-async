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