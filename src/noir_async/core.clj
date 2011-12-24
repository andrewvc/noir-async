(ns noir-async.core
  (:use aleph.http
        noir.core
        lamina.core))

(defprotocol MessageStreamPattern
  "Protocol for a bidirectional message-based stream"
  (send-message [this message])
  (on-receive [this handler])
  (on-close   [this handler]))

(defrecord WebSocketConnection [request-channel]
  MessageStreamPattern
  (send-message [this message] 
    (enqueue request-channel message))
  (on-receive [this handler]
    (receive-all request-channel handler))
  (on-close [this handler]
    (on-closed request-channel handler)))

(defn create-websocket-connection [request-channel]
  (WebSocketConnection. request-channel))

(defprotocol RequestReplyPattern
  "A protocol suitable for an asynchronous connection
   that will receive a single response"
  (respond [this response]))

(defrecord PageConnection [request-channel]
  RequestReplyPattern
  (respond [this response]
    (enqueue-and-close request-channel
        (cond (string? response) {:status 200 :body response}
              :else              response))))

(defn create-page-connection [request-channel]
  (PageConnection. request-channel))

(defmacro defasync-route
  "Base for handling an asynchronous route."
  [conn-class path request-bindings conn-binding & body]
  `(custom-handler ~path ~request-bindings
     (wrap-aleph-handler
       (fn [ch# _#]
         (let [~conn-binding (new ~conn-class ch#)]
           ~@body)))))

(defmacro defpage-async
  "Defines an asynchronous page with a single non-streaming response.
   Responses to the request must be answered with the 'respond' function,
   which can be called at any point in the future
   
   The following async functions are available in its body: respond
  
  Example:
    (defpage-async \"/foo/bar/:baz\" [baz :baz]
      (respond {:status 200 :body \"ohai\"))"
  [path request-bindings conn-binding & body]
  `(defasync-route PageConnection ~path ~request-bindings ~conn-binding ~@body))

 
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
  `(defasync-route WebSocketConnection ~path ~request-bindings ~@body))
