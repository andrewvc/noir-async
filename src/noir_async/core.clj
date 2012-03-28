(ns noir-async.core
  (require [lamina.core :as lc]
           [aleph.http :as ah]
           [noir.core :as nc]))

(defn- format-one-shot
  "Format responses for a one shot response"
  [response]
  (cond (string? response) {:status 200 :body response}
        :else              response))

(defn- downstream-c4h
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
       @(:chunked-initiated? conn)))

(defn one-shot?
  "Is this connection able a oneshot response?"
  [conn]
  (and (regular? conn) (not (chunked? conn))))

(defn websocket?
  "Is the connection opened from the client as a websocket?"
  [conn]
  (= :websocket (:type conn)))

(defn closed?
  "Is this connection closed?"
  [conn]
  (lc/closed? (:request-channel conn)))

(defn close
  "Closes the connection. No more data can be sent / received after this"
  [{:keys [request-channel response-channel]}]
  (lc/close request-channel)
  (when-let [rc @response-channel] (lc/close rc)))


(defn- writable-channel
  "Returns the writable channel in a connection"
  [{:keys [request-channel response-channel] :as conn}]
  (cond (websocket? conn) request-channel
        (regular? conn) (if (chunked? conn) @response-channel request-channel)
        :else nil))

(defn async-push-header
  "Delivers a map as the header only. Used to initiate a chunked
   connection. Chunks may be delivered via apush"
  [conn header-map]
  (when (not (map? header-map))
        (throw "Expected a map of header options!"))
  (when (not (compare-and-set! (:chunked-initiated? conn) false true))
    (throw "Attempted to send a chunked header, but header already sent!"))
  (let [resp-ch (lc/channel)
        orig-body (:body header-map)
        resp (assoc header-map :body resp-ch)]
    ;; If the user included a body in the map, we can deliver it here
    (when orig-body
      (lc/enqueue resp-ch) orig-body)
    (compare-and-set! (:response-channel conn) nil resp-ch)
    (lc/enqueue (:request-channel conn) resp)))

(defn async-push
  "Push data to the client.
   If it's a websocket this sends a message.
   If it's a normal connection, it sends an entire response in one shot.
   If this is a multipart connection (apush-header has been called earlier)
   this sends a new body chunk."
  [conn data]
  (lc/enqueue (writable-channel conn)
              (if (one-shot? conn) (format-one-shot data) data)))

(defn on-close
  "Callback to invoke on connection close."
  [{:keys [request-channel]} handler]
  (lc/on-closed request-channel handler))

(defn on-receive
  "Callback to invoke on receipt of a websocket message."
  [{:keys [request-channel]} handler]
  (lc/receive-all request-channel handler))

(defn connection
  "Generates a new map representing a connection.
   Generally for internal use only."
  [request-channel ring-request]
  {:request-channel request-channel
   :ring-request ring-request
   :response-channel (atom nil)
   :chunked-initiated? (atom false)
   :type (if (:websocket ring-request) :websocket :regular) })

(defmacro defaleph-handler
  "Returns an fn suitable for raw use with aleph + a ring request."
  [conn-binding & body]
  `(fn [ch# ring-request#]
     (let [~conn-binding (connection ch# ring-request#)]
       (on-close ~conn-binding (fn [] (close ~conn-binding)))
       ~@body
       ~conn-binding)))

(defmacro defpage-async
  "Base for handling an asynchronous route."
  [route request-bindings conn-binding & body]
  `(nc/custom-handler ~route {~request-bindings :params}
                      (ah/wrap-aleph-handler
                       (defaleph-handler ~conn-binding ~@body))))