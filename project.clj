(defproject noir-async "1.0.0-ALPHA1"
  :description "Async support for noir. Based on Aleph"
  ;:warn-on-reflection true
  :dependencies [[org.clojure/clojure "1.3.0"]
                 ;; [noir "1.3.0-beta2" :exclusions [hiccup]]
                 [noir "1.3.0-beta2" :exclusions [hiccup]]
                 [aleph "0.2.1-SNAPSHOT"]]
  :dev-dependencies [[lein-marginalia "0.8.0-SNAPSHOT"]])

