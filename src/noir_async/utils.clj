(ns noir-async.utils
  "Helpful tools for asynchronous apps"
  (:import [java.util Timer TimerTask concurrent.TimeUnit]))

(def default-timer (Timer. true))

(defn- fn->timer-task [func]
  (proxy [TimerTask] []
        (run [] (func))))

(defn set-timeout
  "Run a function after a delay"
  ([millis func] (set-timeout default-timer millis func))
  ([timer millis func]
    (.schedule timer (fn->timer-task func) (long millis))))

(defn set-interval
  "Repeatedly run a function"
  ([millis func] (set-interval default-timer millis func))
  ([timer millis func]
    (.schedule timer (fn->timer-task func) (long 0) (long millis))))
