(ns noir-async.core
  (:use aleph.http
        noir.core
        lamina.core))

(def ^:dynamic *handler-type* nil)
(def ^:dynamic *request-channel* nil)
(def ^:dynamic *response-channel* nil)

(defn- enforce-handler-type! [& types]
  "Make sure we're in the correct type of handler, or raise an exception"
  (cond (not (some #{*handler-type*} types))
        (throw (Exception. (str "This async function is only available within "
                                (apply str types) " not " *handler-type*)))))
                                 
(defn respond [response]
  (enforce-handler-type! :defpage-async)
  (enqueue-and-close *request-channel*
    (cond (string? response) {:status 200 :body response}
          :else              response)))

(defmacro defasync-route
  "Base for handling an asynchronous route."
  [path request-bindings & body]
  `(custom-handler ~path ~request-bindings
    (wrap-aleph-handler
      (fn [ch# _#]
        (binding [*request-channel* ch#] ~@body)))))

(defmacro defpage-async
  "Defines an asynchronous page with a single non-streaming response.
   Responses to the request must be answered with the 'respond' function,
   which can be called at any point in the future
   
   The following functions are available in its body: respond
  
  Example:
    (defpage-async \"/foo/bar/:baz\" [baz :baz]
      (respond {:status 200 :body \"ohai\"))"
  [path request-bindings & body]
  `(defasync-route ~path ~request-bindings 
    (binding [*handler-type* :defpage-async] ~@body)))
