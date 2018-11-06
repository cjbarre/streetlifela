(ns lastreetlife.core
  (:require [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer (get-sch-adapter)]
            [bidi.ring :refer (make-handler)]
            [org.httpkit.server :refer (run-server)]
            [clj-http.client :as http]
            [ring.middleware.params :refer (wrap-params)]
            [ring.middleware.keyword-params :refer (wrap-keyword-params)]
            [mount.core :as m]
            [clojure.core.async :as a]
            [clojure.data.json :as json])
  (:gen-class))

(let [{:keys [ch-recv send-fn connected-uids
              ajax-post-fn ajax-get-or-ws-handshake-fn]}
      (sente/make-channel-socket! (get-sch-adapter) {})]
  (def ring-ajax-post                ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk                       ch-recv)
  (def chsk-send!                    send-fn)
  (def connected-uids                connected-uids))


(def api-url "https://services5.arcgis.com/7nsPwEMP38bSkCjy/arcgis/rest/services/TEST_PYTHON_ZONING/FeatureServer/0/query")

(def red-zone-query {:outFields "*"
                     :geometryType "esriGeometryPoint"
                     :returnGeometry false
                     :where "ZONE_SMRY='OPEN SPACE' OR ZONE_SMRY='PUBLIC FACILITY'"
                     :inSR "4326"
                     :spatialRel "esriSpatialRelIntersects"
                     :distance 500
                     :units "esriSRUnit_Foot"
                     :outSR "4326"
                     :f "json"})

(def green-yellow-query {:outFields "*"
                         :geometryType "esriGeometryPoint"
                         :returnGeometry false
                         :inSR "4326"
                         :spatialRel "esriSpatialRelIntersects"
                         :distance 50
                         :units "esriSRUnit_Foot"
                         :outSR "4326"
                         :f "json"})

(defn execute-zone-query! [params [x y :as lat-lon]]
  (json/read-str (:body (http/get api-url {:query-params (merge params {:geometry (format "%s,%s" y x)})})) :key-fn keyword))

(def execute-zone-query! (memoize execute-zone-query!))

(defn get-zone [{:keys [latitude longitude] :as position}]
  (println position)
  (if (not= 0 (-> (execute-zone-query! red-zone-query [latitude longitude]) :features count))
    {:zone :red}
    (let [zones (execute-zone-query! green-yellow-query [latitude longitude])]
      (if (= 0 (->> zones
                    :features
                    (filter #(= "RESIDENTIAL" (-> % :attributes :ZONE_SMRY)))
                    count))
        {:zone :green}
        {:zone :yellow}))))

(a/go-loop [in-msg (a/<! ch-chsk)]
  (when in-msg
    (cond (= :app/user-position-update (:id in-msg)) (do
                                                       (println "request received")
                                                       (time ((:?reply-fn in-msg) (get-zone (:?data in-msg))))
                                                       (println "request answered")))
    (recur (a/<! ch-chsk))))

(def routes ["/api/" {"chsk" {:get #(ring-ajax-get-or-ws-handshake %)
                              :post #(ring-ajax-post %)}
                      "liveness" (fn [req] {:status 200 :body "I'm alive!"})}])

(def app (-> (make-handler routes)
             wrap-keyword-params
             wrap-params))

(m/defstate http-server :start (run-server app {:port 80})
                        :stop  (http-server))


(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Initializing...")
  (m/start))
