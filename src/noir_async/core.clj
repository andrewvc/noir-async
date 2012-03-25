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

(defn websocket?
  "Is the connection opened from the client as a websocket?"
  [{r :ring-request}]
  (boolean (:websocket r)))

(defn close
  "Closes the connection. No more data can be sent / received after this"
  [{:keys [request-channel response-channel]}]
  (lc/close request-channel)
  (when response-channel
    (lc/close response-channel)))

(defn resp
  "Respond to a request in one shot"
  [conn response]
  (if (and (not (websocket? conn)) 
    (lc/enqueue-and-close (:request-channel conn) (format-response response))
    (throw (Exception. "Attempted to call 'resp' on a websocket or chunked resp."))))

(defn- valid-header-map?
  [header-map]
  (and
   (map? header-map)
   (not (:body header-map))))

(defn resp-part
  "Sends a message across a websocket or chunked connection"
  [{:keys [request-channel response-channel state]} msg]
  (if (websocket? conn)
    (lc/enqueue request-channel msg)
    (do
      (cond
       (not (compare-and-set! state :initialized :header-sent))
         (lc/enqueue 
       :else
         (do 
           (compare-and-set! response-channel nil (channel))
           (if (valid-header-map? msg)
             (lc/enqueue request-channel (assoc msg :body @response-channel))
             (throw (Exception. "First message should be a valid header map")))

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
   :state (atom :initialized)
   :type (if (:websocket ring-request) :websocket :standard) })

(defmacro defasync-route
  "Base for handling an asynchronous route."
  [conn-fn path request-bindings conn-binding & body]
  `(custom-handler ~path {~request-bindings :params}
     (wrap-aleph-handler
       (fn [ch# ring-request#]
         (let [~conn-binding (connection ch# ring-request#)]
           ~@body)))))