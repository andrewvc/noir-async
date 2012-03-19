(ns noir-async.core
  (:require [lamina.core :as l])
  (:use aleph.http
        noir.core))

(defprotocol Closable
  "Represents a closable connection"
  (close [this]))

(defprotocol MessageStreamPattern
  "Protocol for a bidirectional message-based stream"
  (send-message [this message])
  (on-receive [this handler])
  (on-close   [this handler]))

(defrecord WebSocketConnection [request-channel ring-request]
  MessageStreamPattern
  (send-message [this message] 
    (l/enqueue request-channel message))
  (on-receive [this handler]
    (l/receive-all request-channel handler))
  (on-close [this handler]
    (l/on-closed request-channel handler))
  Closable
  (close [this]
    (l/close request-channel)))

(defn websocket-connection [request-channel ring-request]
  (WebSocketConnection. request-channel ring-request))

(defprotocol RequestReplyPattern
  "A protocol suitable for an asynchronous connection
   that will receive a single response"
  (respond [this response]))

(defrecord PageConnection
  [request-channel ring-request]
  RequestReplyPattern
  (respond [this response]
    (l/enqueue-and-close request-channel
        (cond (string? response) {:status 200 :body response}
              :else              response)))
  Closable
  (close [this]
    (l/close request-channel)))

(defn page-connection [request-channel ring-request]
  (PageConnection. request-channel ring-request))

(defprotocol ChunkedReplyPattern
  "A protocol suitable for a chunked reply"
  (send-header [this headers])
  (send-chunk [this chunk])
  (send-last-chunk [this chunk]))

(defrecord ChunkedConnection [request-channel ring-request response-channel]
  ChunkedReplyPattern
  (send-header [this headers]
    (l/enqueue response-channel (assoc headers :body response-channel)))
  (send-chunk [this chunk]
    (l/enqueue response-channel chunk))
  Closable
  (close [this]
    (l/close request-channel)
    (l/close response-channel)))

(defn create-chunked-connection [request-channel ring-request]
  (ChunkedConnection. request-channel ring-request (l/channel)))

(defmacro defasync-route
  "Base for handling an asynchronous route."
  [conn-fn path request-bindings conn-binding & body]
  `(custom-handler ~path {~request-bindings :params}
     (wrap-aleph-handler
       (fn [ch# ring-request#]
         (let [~conn-binding (~conn-fn ch# ring-request#)]
           ~@body)))))

(defprotocol RequestReplyPattern
  "A protocol suitable for an asynchronous connection
   that will receive a single response"
  (respond [this response]))

(defmacro defpage-async
  "Defines an asynchronous page with a single non-streaming response.
   Responses to the request must be answered with the 'respond' function,
   which can be called at any point in the future
   
   The following async functions are available in its body: respond
  
  Example:
    (defpage-async \"/foo/bar/:baz\" [baz :baz]
      (respond {:status 200 :body \"ohai\"))"
  [path request-bindings conn-binding & body]
  `(defasync-route page-connection ~path ~request-bindings ~conn-binding ~@body))

(defmacro defpage-chunked
  [path request-bindings conn-binding & body]
  `(defasync-route chunked-connection ~path ~request-bindings ~conn-binding ~@body))

(defmacro defwebsocket
  "Defines a path for use as a websocket.
   The function body is executed 'on-open'.
    
   The following async functions are available in its body:
     (on-receive (fn [msg]))
     (on-close   (fn []))
     (send-message \"some data\")
  
  Example:
    (defwebsocket \"/path/to/websocket\"
      (println \"Socket now open...\")
      (on-receive (fn [msg] (send-msg \"Pong for:  \" msg)))
      (on-close (fn [] (println \"Socket closed!\"))))"
  [path request-bindings & body]
  `(defasync-route websocket-connection ~path ~request-bindings ~@body))
