(ns lastreetlife.core
  (:require-macros [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [rum.core :as rum]
            [cljs.core.async :as async :refer (<! >! put! chan)]
            [taoensso.sente :as sente :refer (cb-success?)]
            [cljsjs.leaflet]))



(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/api/chsk"
       {:type :auto})]
  (def chsk       chsk) 
  (def ch-chsk    ch-recv)
  (def chsk-send! send-fn)
  (def chsk-state state))

(enable-console-print!)

(def zone-colors {:red "#FF0000"
                  :yellow "#FFFF00"
                  :green "#00FF00"})

(defonce app-state (atom {:map {:current-position-marker (.circleMarker js/L #js [0,0])}
                          :zone-indicator {:color "#FFFFFF"}}))

(declare app)

(rum/defc zone-indicator < rum/reactive {:did-mount (fn [state]
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
                                                                                        (swap! app-state assoc-in [:zone-indicator :color] (get zone-colors (:zone reply)))
                                                                                        (println reply))))))
                                                      state)}
  []
  [:div {:style {:background-color (get-in (rum/react app-state) [:zone-indicator :color])
                 :width "100vw" 
                 :height "5vh"
                 :margin "0"
                 :padding "0"}}])

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)

;; define your app data so that it doesn't get over-written on reload 

(rum/defc mapc < {:did-mount (fn [state]
                               (let [mymap (.map js/L "map")]
                                 (.addTo (.tileLayer js/L
                                                     "https://api.tiles.mapbox.com/v4/{id}/{z}/{x}/{y}.png?access_token={accessToken}"
                                                     (clj->js
                                                      {:maxZoom 18
                                                       :id "mapbox.streets"
                                                       :accessToken "pk.eyJ1IjoiY2piYXJyZSIsImEiOiJjam9haXV6bXAwOWk0M3BvenFva3Z1MHphIn0.d4MKkC61nQ9QS6h-49rWlw"}))
                                         mymap)
                                 (.addTo (get-in @app-state [:map :current-position-marker]) mymap)
                                 (.watchPosition navigator.geolocation (fn [pos]
                                                                         (.setView mymap #js [pos.coords.latitude, pos.coords.longitude] 16)
                                                                         (.setLatLng (get-in @app-state [:map :current-position-marker]) #js [pos.coords.latitude, pos.coords.longitude]))))
                               state)}
  []
  [:div#map {:style {:height "95vh"
                     :width "100vw"}}])

(rum/defc app []
  [:div {}
   (zone-indicator)
   (mapc)])

(rum/mount (app)
           (. js/document (getElementById "app")))

