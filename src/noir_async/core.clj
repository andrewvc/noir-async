(ns noir-async.core
  (require [lamina.core :as lc]
           [aleph.http :as ah]
           [noir.core :as nc]))

(defn- format-response
  "Format responses for a one shot response"
  [response]
  (cond (string? response) {:status 200 :body response}
        :else              response))

(defn- downstream-ch
  [{:keys [request-channel response-channel]}]
  "Returns the downstream channel for a connection"
  (or response-channel request-channel))

(defn regular?
  "Is this a regular HTTP connection? I.E. Not a websocket"
  [conn]
  (= (:regular (:type conn))))

(defn chunked?
  "Is this connection in chunked mode?"
  [conn]
  (and (regular? conn)
       @(:headers-sent conn)))

(defn websocket?
  "Is the connection opened from the client as a websocket?"
  [conn]
  (= (:websocket (:type conn))))

(defn closed?
  "Is this connection closed?"
  [conn]
  (lc/closed? (:request-channel conn)))

(defn close
  "Closes the connection. No more data can be sent / received after this"
  [{:keys [request-channel response-channel]}]
  (lc/close request-channel)
  (when-let [rc @response-channel] (lc/close rc)))

(defn deliver-header
  "Delivers a map as the header only. Used to initiate a chunked
   connection."
  [conn header-map]
  (when (not (map? header-map))
        (throw "Expected a map of header options!"))
  (when (not (compare-and-set! (:headers-sent conn) false true))
    (throw "Attempted to send a chunked header, but header already sent!"))
  (let [resp-ch (lc/channel)
        orig-body (:body header-map)
        resp (assoc header-map :body resp-ch)]
    ;; If teh user included a body in the map, we can deliver it here
    (when orig-body
      (lc/enqueue resp-ch) orig-body)
    (compare-and-set! (:response-channel conn) nil resp-ch)
    (lc/enqueue (:request-channel conn) resp)))
    
(defn deliver
  "Respond with data"
  [conn data]
  (cond (websocket? conn)
          (lc/enqueue (:request-channel conn) data)
        (regular? conn)
          (if (chunked? conn)
            (lc/enqueue @(:response-channel conn) data)
            ;; Lamina only lets you respond once unless chunked anyway
            (lc/enqueue-and-close (:request-channel conn) data))))

(defn on-close
  "Callback to invoke on connection close."
  [{:keys [request-channel]} handler]
  (lc/on-closed (request-channel) handler))

(defn on-receive
  "Callback to invoke on receipt of a websocket message."
  [{:keys [request-channel]} handler]
  (lc/receive-all request-channel handler))

(defn- connection
  [request-channel ring-request]
  {:request-channel request-channel
   :ring-request ring-request
   :response-channel (atom nil)
   :headers-sent? (atom false)
   :type (if (:websocket ring-request) :websocket :regular) })

(defmacro defasync-route
  "Base for handling an asynchronous route."
  [conn-fn path request-bindings conn-binding & body]
  `(custom-handler ~path {~request-bindings :params}
     (wrap-aleph-handler
       (fn [ch# ring-request#]
         (let [~conn-binding (connection ch# ring-request#)]
           ~@body)))))