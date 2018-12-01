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

;; Postgis Database Setup ;;

(m/defstate postgis-database-conn :start (make-datasource postgis-datasource)
                                  :stop (close-datasource postgis-database-conn))

;; Event Handling ;;

(defmulti incoming-message-handler
  (fn [{:keys [event] :as msg}] (first event)))

(defmethod incoming-message-handler :default
  [msg]
  (println (:event msg)))

(a/go-loop [in-msg (a/<! ch-chsk)]
  (when in-msg
    (try
      (time (incoming-message-handler in-msg))
      (catch clojure.lang.ExceptionInfo e
        (clojure.pprint/pprint e)))
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
