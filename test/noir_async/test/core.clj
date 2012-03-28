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

(defn conn-tester []
  ((deftesthandler conn) "/foo" {}))

(defn simple-resp-tester [resp]
  "Just return the queued async response please!"
  (let [handler (deftesthandler conn (na/async-push conn resp))]
    (wait-for-message (:request-channel (handler "/foo" {})) 100)))

(defn is-a-map [x] (testing "is a map" (is (map? x))))

(deftest oneshot-responses
  (testing "string response"
    (let [r (simple-resp-tester "ohai")]
      (is-a-map r)
      (testing "body" (is (= (:body r) "ohai")))
      (testing "status" (is (= (:status r) 200)))))
  (testing "full response"
    (let [r (simple-resp-tester {:status 404
                                 :body "complex"
                                 :x-whatever "fancy"})]
      (is-a-map r)
      (testing "status" (is (= (:status r) 404)))
      (testing "body" (is (= (:body r) "complex")))
      (testing "custom headers" (is (= (:x-whatever r) "fancy"))))))

(defn chunk-receive [header]
  (wait-for-message (:body header) 100))

(deftest chunked-responses
  (testing "three chunks"
    (let [c (conn-tester)]
      (testing "with a header"
        (let [sent-header {:status 500 :x-rand-hdr 123}
              _ (na/async-push-header c sent-header)
              rcvd-header (wait-for-message (:request-channel c) 100)]
          (is (= sent-header (dissoc rcvd-header :body)))
          (testing "on the first chunk"
            (na/async-push c "chunk-one")
            (is (= "chunk-one" (chunk-receive rcvd-header))))
          (testing "on the second and third chunks, grouped"
            (na/async-push c "chunk-two")
            (na/async-push c "chunk-three")
            (is (= "chunk-two" (chunk-receive rcvd-header)))
            (is (= "chunk-three" (chunk-receive rcvd-header))))
          (testing "closing"
            (na/close c)
            (testing "should close the request channel"
              (= true (closed? (:request-channel c))))
            (testing "should close the response channel"
              (= true (closed? @(:response-channel c))))))))))
