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
            [schema.core :as s]
            [hikari-cp.core :refer [make-datasource close-datasource]]
            [clojure.java.jdbc :as jdbc]
            [environ.core :refer [env]])
  (:gen-class))

;; Configuration ;;

(def public-http-server-port (Integer/parseInt (env :http-public-webserver-port-number)))

(def postgis-datasource
  {:adapter "postgresql"
   :read-only true
   :username (env :postgis-db-username)
   :password (env :postgis-db-password)
   :database-name (env :postgis-db-name)
   :server-name (env :postgis-db-server-name)
   :port-number (env :postgis-db-port-number)})

;; Sente / Websockets Setup ;;

(let [{:keys [ch-recv send-fn connected-uids
              ajax-post-fn ajax-get-or-ws-handshake-fn]}
      (sente/make-channel-socket! (get-sch-adapter) {})]
  (def ring-ajax-post                ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk                       ch-recv)
  (def chsk-send!                    send-fn)
  (def connected-uids                connected-uids))

;; Events ;;

(def Point
  {:latitude s/Num
   :longitude s/Num})

(def UserPositionChangedEvent
  {:event :UserPositionChanged
   :old-position Point
   :new-position Point})

;; Sente Message Handling ;;

(defprotocol ZoneDiscovery
  (get-zone [this Point radius]))

(def get-zone-query
  "with query_point as (
     select ST_Buffer(
              ST_Transform(
                ST_SetSRID(
                  ST_MakePoint(?, ?),
                4326),
              3857),
            ?) as geom
   )
   select
     sps.stname,
     sps.color
   from
     safe_parking_streets sps,
     query_point qp
   where
     qp.geom && sps.geom_3857
   group by
     1,2;")

(defn get-most-restrictive-zone [zones]
  (let [{:strs [Red Yellow Green]} (group-by :color zones)]
    (cond Red (first Red)
          Yellow (first Yellow)
          Green (first Green))))

;; Postgis Database Setup ;;

(m/defstate postgis-database-conn :start (make-datasource postgis-datasource)
                                  :stop (close-datasource postgis-database-conn))


(defrecord PostgisDatabase [db-conn]
  ZoneDiscovery
  (get-zone [this
             {:keys [latitude longitude] :as Point}
             radius]
    (cond (> radius 10) (throw (ex-info
                               "Radius too large, no zones found."
                               {:exception :NoZonesFoundException
                                :cause "Radius exceeded 5 meters without finding a zone."
                                :input {:point Point
                                        :radius radius}}))
          (< radius 0) (throw (ex-info
                               "Radius cannot be less than zero"
                               {:exception :NoZonesFoundException
                                :cause "Radius is less than zero."
                                :input {:point Point
                                        :radius radius}}))
          :else
          (let [zones (jdbc/with-db-connection [db-conn db-conn]
                        (jdbc/query db-conn [get-zone-query longitude latitude radius]))
                qty (count zones)]
            (cond (<= qty 0) (recur Point (+ radius 0.5))
                  (>  qty 1) (get-most-restrictive-zone zones)
                  :else (first zones))))))

;; Event Handling ;;

(defn normalize-zone [zone]
  (update zone :color #(-> % clojure.string/lower-case keyword)))

(defmulti incoming-message-handler
  (fn [{:keys [event] :as msg}] (first event)))

(defmethod incoming-message-handler :app/UserPositionChanged
  [{:keys [?reply-fn ?data get-zone] :as msg}]
  (when (and ?data ?reply-fn)
    (try
      (-> ?data (get-zone 1.0) normalize-zone ?reply-fn)
      (catch clojure.lang.ExceptionInfo e
        {:color :white :exception (ex-data e)}))))

(defmethod incoming-message-handler :default
  [msg]
  (println (:event msg)))

(a/go-loop [in-msg (a/<! ch-chsk)]
  (when in-msg
    (try
      (time (incoming-message-handler (assoc in-msg :get-zone (partial get-zone (->PostgisDatabase {:datasource postgis-database-conn})))))
      (catch clojure.lang.ExceptionInfo e (clojure.pprint/pprint e)))
    (recur (a/<! ch-chsk))))

;; Public Webserver Setup ;;

(def routes ["/api/" {"chsk" {:get #(ring-ajax-get-or-ws-handshake %)
                              :post #(ring-ajax-post %)}
                      "liveness" (fn [req] {:status 200 :body "I'm alive!"})}])


(def app (-> (make-handler routes)
             wrap-keyword-params
             wrap-params))

(m/defstate http-server :start (run-server app {:port public-http-server-port})
                        :stop  (http-server))


;; Initialization & Entry Point ;;

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Initializing...")
  (m/start))
