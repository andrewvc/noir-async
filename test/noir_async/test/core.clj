(ns noir-async.test.core
  (:use noir.core
        noir.util.test
        lamina.core
        clojure.test)
  (:require [noir-async.core :as na]
            [noir.server :as nr-server] ))

(def good-response-str "ohai!")
(def good-response-map {:status 200 :body good-response-str})

(defn- make-request [route & [params]]
  (let [[method uri] (if (vector? route)
                       route
                       [:get route])]
    {:uri uri :request-method method :params params}))

(defmacro deftesthandler
  "A fake handler suitable for testing."
  [conn-binding & body]
  `(let [handler# (na/defaleph-handler ~conn-binding ~@body)]
     (fn [t-route# params#]
       (let [ch# (channel)
             request# (make-request t-route# params#)]
         ; Execute the actual handler
         (handler# ch# request#)
         ; Return a fake connection to test against
         (na/connection ch# request#)))))

(defn simple-resp-exec [resp]
  "Just return the queued async response please!"
  (let [handler (deftesthandler conn (na/async-push conn resp))]
    (wait-for-message (:request-channel (handler "/foo" {})))))

(defn is-a-map [x] (testing "is a map" (is (map? x))))

(deftest oneshot-responses
  (testing "string response"
    (let [r (simple-resp-exec "ohai")]
      (is-a-map r)
      (testing "body" (is (= (:body r) "ohai")))
      (testing "status" (is (= (:status r) 200)))))
  (testing "full response"
    (let [r (simple-resp-exec {:status 404
                               :body "complex"
                               :x-whatever "fancy"})]
      (is-a-map r)
      (testing "status" (is (= (:status r) 404)))
      (testing "body" (is (= (:body r) "complex")))
      (testing "custom headers" (is (= (:x-whatever r) "fancy"))))))