(defproject noir-async "1.0.0-ALPHA1"
  :description "Async support for noir. Based on Aleph"
  ;:warn-on-reflection true
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [noir "1.3.0-beta2"]
                 [aleph "0.2.1-beta1"]]
  :dev-dependencies [[midje "1.3.1"]
                     [lein-midje "1.0.7"]
                     [lein-clojars "0.7.0"]])