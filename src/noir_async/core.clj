(ns noir-async.core
  "The main namespace to require when building an app with noir-async."
  (require [lamina.core :as lc]
           [aleph.http :as ah]
           [aleph.formats :as af]
           [noir.core :as nc]
           [noir.server :as n-srv]))

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
  "true if this is a regular HTTP connection, I.E. not a websocket."
  [conn]
  (= :regular (:type conn)))

(defn chunked?
  "Is this connection in chunked mode?"
  [conn]
  (and (regular? conn)
       @(:chunked-initiated? conn)))

(defn one-shot?
  "true if the connection is able to send a oneshot response, I.E. not chunked or a websocket."
  [conn]
  (and (regular? conn) (not (chunked? conn))))

(defn websocket?
  "True if the connection was is a websocket"
  [conn]
  (= :websocket (:type conn)))

(defn closed?
  "Returns true if the connection is already closed"
  [conn]
  (lc/closed? (:request-channel conn)))

(defn writable-channel
  "Returns the connection's writable channel. Useful if you need to directly pipe using lamina."
  [{:keys [request-channel response-channel] :as conn}]
  (cond (websocket? conn) request-channel
        (regular? conn) (if (chunked? conn) @response-channel request-channel)
        :else nil))

(defn readable-channel
  "Returns the connection's readable channel. Useful if you need to directly pipe using lamina."
  [{:keys [request-channel]}]
   request-channel)

(defn async-push-header
  "Explicitly sends a chunked header. Should not be used directly, instead use (async-push {:chunked true})."
  {:no-doc true}
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
  "This should be the only method you need to send data.
   All pushes to the client through this are asynchronous.
   If it's a websocket this sends a message.
   If it's a normal connection, it sends the entire response in one shot and closes the connection.
   To start a multi-part connection, send a header with the the option :chunked set to true
     ex: (async-push conn {:chunked true})
   If this is a multipart connection (a chunked header has been sent been called earlier) this sends a new body chunk."
  [conn data]
  (if (and (map? data) (:chunked data))
    (async-push-header conn (dissoc data :chunked))
    (lc/enqueue (writable-channel conn)
                (if (one-shot? conn) (format-one-shot data) data))))

(defn request-body-str
  "Retrieves the request body as a string."
  [conn]
  (af/bytes->string (:body (:ring-request conn))))

(defn request-body-byte-buffer
  "Retrieves the request body as a ByteBuffer"
  [conn]
  (af/bytes->byte-buffer (:body (:ring-request conn))))

(defn close-connection
  "Closes the connection. No more data can be sent / received after this"
  [conn]
  (when-let [resp-ch @(:response-channel conn)]
    (lc/close resp-ch))
  (when-let [req-ch (:request-channel conn)]
    (when (lc/channel? req-ch) (lc/close req-ch))))

(defn on-close
  "Sets a callback to handle a closed connection. Callback takes no arguments."
  [{:keys [request-channel response-channel]} handler]
  (if (lc/channel? request-channel)
    (lc/on-closed request-channel handler)
    (lc/on-realized request-channel
                    (fn [_] (when-let [rc @response-channel] (lc/on-closed @response-channel handler)))
                    #(throw %))))

(defn on-receive
  "Sets a callback to handle websocket messages.
   Callback takes one argument, a string containing the message."
  [{:keys [request-channel]} handler]
  (if (lc/channel? request-channel)
    (lc/receive-all request-channel handler)
    (lc/on-realized request-channel handler nil)))

(defn connection
  "Generates a new map representing a connection."
  {:no-doc true}  
  [request-channel ring-request]
  {:request-channel request-channel
   :ring-request ring-request
   :response-channel (atom nil)
   :chunked-initiated? (atom false)
   :type (if (:websocket ring-request) :websocket :regular) })

(defmacro defaleph-handler
  "Returns an fn suitable for raw use with aleph + a ring request. Generally it is preferred to use defpage-async"
  {:no-doc true}
  [conn-binding & body]
  `(fn na-aleph-handle-inner [ch# ring-request#]
     (let [~conn-binding (connection ch# ring-request#)]
       (on-close ~conn-binding (fn na-aleph-on-close [] (close-connection ~conn-binding)))
       ~@body
       ~conn-binding)))

(defmacro defpage-async
  "Creates an asynchronous noir route. This route can handle both
   regular HTTP and Websocket connections. For regular HTTP
   responses can be delivered in one shot, or in chunked mode."
  [route request-bindings conn-binding & body]
  `(nc/custom-handler ~route {~request-bindings :params}
                      (ah/wrap-aleph-handler
                       (defaleph-handler ~conn-binding ~@body))))

(defn start-server
  "Starts a new noir/aleph server in the given mode and port. If the views arg is passed in, it will load views from that path via noir-server/load-views"
  ([mode port]
     (start-server mode port nil))
  ([mode port views]
     (when views (n-srv/load-views views))
     (ah/start-http-server
      (ah/wrap-ring-handler (n-srv/gen-handler {:mode mode}))
      {:port port :websocket true})))