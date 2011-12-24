(ns noir-async.core
  (:use aleph.http
        noir.core
        lamina.core))

(def ^:dynamic *route-type* nil)
(def ^:dynamic *request-channel* nil)
(def ^:dynamic *response-channel* nil)

(defn- enforce-route-type! [& types]
  "Make sure we're in the correct type of handler, or raise an exception"
  (cond (not (some #{*route-type*} types))
        (throw (Exception. (str "This async function is only available within "
                                (apply str types) " not " *route-type*)))))

(defn respond [response]
  "Responds to a route declared with defpage-async.
   Must be used within defpage-async."
  (enforce-route-type! :page)
  (enqueue-and-close *request-channel*
    (cond (string? response) {:status 200 :body response}
          :else              response)))

(defmacro defasync-route
  "Base for handling an asynchronous route."
  [route-type path request-bindings & body]
  `(custom-handler ~path ~request-bindings
    (wrap-aleph-handler
      (fn [ch# _#]
        (binding [*request-channel* ch#
                  *route-type*      ~route-type]
           ~@body)))))

(defmacro defpage-async
  "Defines an asynchronous page with a single non-streaming response.
   Responses to the request must be answered with the 'respond' function,
   which can be called at any point in the future
   
   The following async functions are available in its body: respond
  
  Example:
    (defpage-async \"/foo/bar/:baz\" [baz :baz]
      (respond {:status 200 :body \"ohai\"))"
  [path request-bindings & body]
  `(defasync-route :page ~path ~request-bindings ~@body))

(defn on-receive 
  "Calls handler taking a message as an argument on message receipt."
  [handler]
  (enforce-route-type! :websocket)
  (receive-all *request-channel* handler))

(defn on-close
  "Calls handler when the current websocket closes"
  [handler]
  (enforce-route-type! :websocket)
  (on-closed *request-channel* handler))

(defn send-message
  "Sends a message to the client across the current websocket connection"
  [message]
  (enforce-route-type! :websocket)
  (enqueue *request-channel* message))
  
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
  `(defasync-route :websocket ~path ~request-bindings ~@body))
