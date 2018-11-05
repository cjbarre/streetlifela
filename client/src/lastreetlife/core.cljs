(ns lastreetlife.core
  (:require-macros [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [rum.core :as rum]
            [cljs.core.async :as async :refer (<! >! put! chan)]
            [taoensso.sente :as sente :refer (cb-success?)]))



(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/api/chsk" {:host "localhost"}
       {:type :auto})]
  (def chsk       chsk)
  (def ch-chsk    ch-recv)
  (def chsk-send! send-fn)
  (def chsk-state state))

(enable-console-print!)

(def zone-colors {:red "#FF0000"
                  :yellow "#FFFF00"
                  :green "#00FF00"})

(defonce app-state (atom {:color "#FFFFFF"}))

(rum/defc zone-indicator []
  [:div {:style {:background-color (:color @app-state)
                 :width "100vw"
                 :height "100vh"
                 :margin "0"
                 :padding "0"}}])

(rum/mount (zone-indicator)
           (. js/document (getElementById "app")))

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)

(.watchPosition navigator.geolocation
                (fn [pos]
                  (println "Sending request")
                  (println (str pos.coords.latitude "," pos.coords.longitude))
                       (chsk-send! [:app/user-position-update
                                    {:latitude pos.coords.latitude
                                     :longitude pos.coords.longitude}]
                                   5000
                                   (fn [reply]
                                     (println "Request Answered")
                                     (println reply)
                                     (if (cb-success? reply)
                                       (do (swap! app-state assoc :color (get zone-colors (:zone reply)))
                                           (rum/mount (zone-indicator) (. js/document (getElementById "app"))))
                                       (println reply))))))
 
(println "This text is printed from src/lastreetlife/core.cljs. Go ahead and edit it and see reloading in action.")

;; define your app data so that it doesn't get over-written on reload 

