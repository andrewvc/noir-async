(ns noir-async.test.core
  (:use noir.core
        noir.util.test
        lamina.core
        clojure.test)
  (:require [noir-async.core :as na]
            [noir.server :as nr-server]
            [aleph.http :as http])
  (:import java.net.ConnectException))

(def good-response-str "ohai!")
(def good-response-map {:status 200 :body good-response-str})

(defn- make-request [route & [params]]
  (let [[method uri] (if (vector? route)
                       route
                       [:get route])]
    {:uri uri :request-method method :params params}))

(defmacro deftesthandler
  "Define a route that generates handlers suitable for testing"
  [conn-binding request-merge & body]
  `(let [handler# (na/defaleph-handler ~conn-binding ~@body)]
     (fn [t-route# params#]
       (let [ch# (channel)
             request# (merge (make-request t-route# params#) ~request-merge)]
         ; Execute the actual handler
         (handler# ch# request#)))))

(defn conn-tester []
  ((deftesthandler conn {}) "/foo" {}))

(defn websocket-conn-tester []
  ((deftesthandler conn {:websocket true}) "/foo" {}))

(defn simple-resp-tester [resp]
  "Just return the queued async response please!"
  (let [handler (deftesthandler conn {} (na/async-push conn resp))]
    (wait-for-message (:request-channel (handler "/foo" {})) 100)))

(defn is-a-map [x] (testing "is a map" (is (map? x))))

(def types-sample-requests
     {:websocket (na/connection (channel)
                  (assoc (make-request "/w") :websocket true))
      :one-shot (na/connection (channel) (make-request "/o"))
      :chunked (assoc (na/connection (channel) (make-request "/c"))
                 :chunked-initiated? (atom true)
                 :response-channel (atom (channel)))})

(deftest typechecks-against-websockets
  (let [c (:websocket types-sample-requests)]
    (are [x] (= true x)
         (na/websocket? c)
         (not (na/regular? c))
         (not (na/one-shot? c))
         (not (na/chunked? c)))))

(deftest typechecks-against-one-shots
  (let [c (:one-shot types-sample-requests)]
    (are [x] (= true x)
         (not (na/websocket? c))
         (na/regular? c)
         (na/one-shot? c)
         (not (na/chunked? c)))))

(deftest typechecks-against-chunked
  (let [c (:chunked types-sample-requests)]
    (are [x] (= true x)
         (not (na/websocket? c))
         (na/regular? c)
         (not (na/one-shot? c))
         (na/chunked? c))))

(deftest server-side-closes
  (let [c (conn-tester)]
    (na/close-connection c)
    (is (closed? (:request-channel c)))))

(deftest client-side-closes
  (let [c (conn-tester)
        caught-close (atom false)]
    ;; we want to test w/ both channels
    (na/async-push c {:status 201 :chunked true})
    (na/on-close c (fn [] (compare-and-set! caught-close false true)))
    (close (:request-channel c))
    (testing "should execute the on-close body"
      (is @caught-close))
    (testing "should close the response-channel"
      (is (closed? @(:response-channel c))))))

(deftest received-messages
  (let [c (conn-tester)
        recvd-msg (atom nil)]
    (na/on-receive c #(compare-and-set! recvd-msg nil %1))
    (enqueue (:request-channel c) "ohai")
    (testing "text matches"
      (is (= "ohai" @recvd-msg)))))

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
        (let [sent-header {:status 500 :x-rand-hdr 123 :chunked true}
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
            (na/close-connection c)
            (testing "should close the response channel"
              (is (closed? @(:response-channel c))))))))))

(defn test-message-receipt
  [results num contents]
  (testing "should increment the rcvd-count"
    (is (= num @(:rcount results))))
  (testing "should see the message"
    (is (= (last @(:rmsgs results)) contents))))

;; Note, in a real server sent messages don't loop back
;; But this works fine-ish for testing
(deftest websocket-connections
  (testing "a simple message exchange"
    (let [c          (websocket-conn-tester)
          req-ch     (:request-channel c)
          results    {:rcount (atom 0) :rmsgs (atom [])}]
      (na/on-receive c (fn [m]
                         (swap! (:rcount results) inc)
                         (swap! (:rmsgs results) #(conj %1 m))))
      (testing "the rcvd first message"
        (enqueue req-ch "first-rcvd-message")
        (test-message-receipt results 1 "first-rcvd-message"))
      (testing "the first sent message"
        (na/async-push c "first-sent-message")
        (test-message-receipt results 2 "first-sent-message"))
      (testing "server-side closes"
        (na/close-connection c)
        (is (closed? (:request-channel c)))))))

(deftest server-start
  (testing "Server start"
    (let [s-port 15882
          srv (na/start-server :prod s-port)
          resp @(http/http-request
                 {:url (str "http://localhost:" s-port) :method :get})]
      (testing "request status"
        (is (= 404 (:status resp))))
      (testing "stoping the server"
        (is (= :lamina/realized @(srv))) ; Not sure of this stopping it... hence the next test
        (is (= java.net.ConnectException
                  (try
                    @(http/http-request {:timeout 300 :url (str "http://localhost:" s-port) :method :get})
                    (catch Exception e (class e)))))))))