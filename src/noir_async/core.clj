(ns noir-async.core
  (require [lamina.core :as lc]
           [aleph.http :as ah]
           [noir.core :as nc]))

(defn- format-one-shot
  "Format responses for a one shot response."
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
  (= :regular (:type conn)))

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

(defn- writable-channel
  "Returns the a connections writable channel."
  [{:keys [request-channel response-channel] :as conn}]
  (cond (websocket? conn) request-channel
        (regular? conn) (if (chunked? conn) @response-channel request-channel)
        :else nil))

(defn async-push-header
  "Explicitly sends a chunked header, the same as
   (async-push conn {:chunked true})"
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
  "Pushes data to the client.
   If it's a websocket this sends a message.
   If it's a normal connection, it sends an entire response in one shot.
   To start a multi-part connection, send a header with the body set to :chunked
     ex: (async-push conn {:chunked true})
   If this is a multipart connection (a chunked header has been sent been called earlier) this sends a new body chunk."
  [conn data]
  (if (and (map? data) (:chunked data))
    (async-push-header conn (dissoc data :chunked))
    (lc/enqueue (writable-channel conn)
                (if (one-shot? conn) (format-one-shot data) data))))

(defn close
  "Closes the connection. No more data can be sent / received after this"
  [{:keys [request-channel response-channel]}]
  (if (lc/channel? request-channel)
    (lc/close request-channel)
    (async-push {:status 500 :body "Internal Error. Attempted explicit connection close on non-stream in noir-sync"}))
  (when-let [rc @response-channel] (lc/close rc)))

(defn on-close
  "Sets a callback to handle a closed connection.
   Callback takes no arguments."
  [{:keys [request-channel]} handler]
  ;; We distinguish between Plain HTTP Channels (result channels)
  ;; and more socket oriented spliced ones
  (if (lc/channel? request-channel)
    (lc/on-closed request-channel handler)))

(defn on-receive
  "Sets a callback to handle websocket messages.
   Callback takes one argument, a string containing the message."
  [{:keys [request-channel]} handler]
  (if (lc/channel? request-channel)
    (lc/receive-all request-channel handler)
    (lc/on-realized request-channel handler nil)))

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
  "Returns an fn suitable for raw use with aleph + a ring request.
   Works much like defpage-async, just with fewer bindings. Look
   at the docs for defpage-async for more details."
  [conn-binding & body]
  `(fn na-aleph-handle-inner [ch# ring-request#]
     (let [~conn-binding (connection ch# ring-request#)]
       (on-close ~conn-binding (fn na-aleph-on-close [] (close ~conn-binding)))
       ~@body
       ~conn-binding)))

(defmacro defpage-async
  "Creates an asynchronous noir route. This route can handle both
   regular HTTP and Websocket connections. For regular HTTP
   responses can be delivered in one shot, or in chunked mode.
"
  [route request-bindings conn-binding & body]
  `(nc/custom-handler ~route {~request-bindings :params}
                      (ah/wrap-aleph-handler
                       (defaleph-handler ~conn-binding ~@body))))