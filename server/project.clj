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
                 [clj-http "3.9.1"]
                 [org.clojure/java.jdbc "0.7.8"]
                 [org.postgresql/postgresql "42.2.5"]
                 [hikari-cp "1.8.3"]
                 [iapetos "0.1.8"]
                 [prismatic/schema "1.1.9"]
                 [com.taoensso/timbre "4.10.0"]
                 [environ "1.1.0"]]
  :main ^:skip-aot lastreetlife.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
