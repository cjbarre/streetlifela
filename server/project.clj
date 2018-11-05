(defproject lastreetlife "0.1.0"
  :description ""
  :url ""
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/data.json "0.2.6"]
                 [http-kit "2.3.0"]
                 [bidi "2.1.4"]
                 [ring/ring-core "1.7.1"]
                 [com.taoensso/sente "1.13.1"]
                 [mount "0.1.14"]
                 [clj-http "3.9.1"]]
  :main ^:skip-aot lastreetlife.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
