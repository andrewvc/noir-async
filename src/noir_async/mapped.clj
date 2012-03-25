(ns noir-async.mapped
  (require [lamina.core :as lc]
           [aleph.http :as ah]
           [noir-core :as nc]))

{:request-channel
 :ring-request
 :response-channel}

(defn close
  "Closes the connection. No more data can be sent / received after this"
  [{:keys [request-channel response-channel]}]
  (l/close request-channel)
  (when response-channel
    (l/close response-channel)))

(defn- format-response
  "Format responses for a one shot response"
  (cond (string? response) {:status 200 :body response}
        :else              response))

(defn- downstream-ch
  [{:keys [request-channel response-channel}]]
  "Returns the downstream channel for a connection"
  (or response-channel request-channel))

(defn websocket?
  "Is the connection opened from the client as a websocket?"
  [conn]
  false)

(defn resp
  "Respond to a request in one shot"
  [conn response]
  (if (not (websocket? conn))
    (l/enqueue-and-close (:request-channel conn) (format-response response))
    (throw (Exception. "Attempted to call 'respond' on a websocket"))))

(defn resp-part
  "Sends a message across a websocket or chunked connection"
  [conn msg]
  (if (websocket? conn)
    (l/enqueue (downstream-ch conn) msg)
    (do
      (l/enqueue (or (response-channel request-channel))))))

(defn respond-and-clos

(defn on-close
  "Callback to invoke on connection close."
  [{:keys [request-channel]} handler]
  (l/on-closed (request-channel) handler))

(defn on-receive
  "Callback to invoke on receipt of a websocket message."
  [{:keys [request-channel]} handler]
  (l/receive-all request-channel handler))

(defmacro defasync-route
  "Base for handling an asynchronous route."
  [conn-fn path request-bindings conn-binding & body]
  `(custom-handler ~path {~request-bindings :params}
     (wrap-aleph-handler
       (fn [ch# ring-request#]
         (let [~conn-binding (~conn-fn ch# ring-request#)]
           ~@body)))))

(defn- mono-connection
  [request-channel ring-request]
  {:request-channel request-channel
   :ring-request ring-request
   :conn-type (atom :http)})

(defn- duo-connection
  [request-channel ring-request]
  (assoc (mono-connection request-channel ring-request)
    :response-channel (channel)))

(defmacro defpage-websocket [path request-bindings & body]
  `(defasync-route mono-connection ~path ~request-bindings ~@body))

(defmacro defpage-async [path request-bindings & body]
  `(defasync-route mono-connection ~path ~request-bindings ~@body))

(defmacro defpage-chunked
  (defmacro defpage-async [path request-bindings & body]
    `(defasync-route duo-connection ~path ~request-bindings ~@body)))
