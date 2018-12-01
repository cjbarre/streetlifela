(ns lastreetlife.core
  (:require-macros [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [rum.core :as rum]
            [cljs.core.async :as async :refer (<! >! put! chan)]
            [taoensso.sente :as sente :refer (cb-success?)]
            [cljsjs.leaflet]
            [cljsjs.leaflet-locatecontrol]))



(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/api/chsk" {:host "localhost:80"}
       {:type :auto})]
  (def chsk       chsk) 
  (def ch-chsk    ch-recv)
  (def chsk-send! send-fn)
  (def chsk-state state))

(enable-console-print!)

(defonce app-state (atom {:map {:current-position-marker (.circleMarker js/L #js [0,0])}}))

(declare app)

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)

;; define your app data so that it doesn't get over-written on reload 

(rum/defc mapc < {:did-mount (fn [state]
                               (let [mymap (.map js/L "map")
                                     locate-control (js/L.control.locate)]
                                 (.addTo (.tileLayer js/L
                                                     "https://api.mapbox.com/styles/v1/cjbarre/cjonhdun32u8k2st5vzr60gah/tiles/{z}/{x}/{y}?access_token={accessToken}"
                                                     (clj->js
                                                      {:maxZoom 20
                                                       :id "cjbarre.safe-parking-streets"
                                                       :accessToken "pk.eyJ1IjoiY2piYXJyZSIsImEiOiJjam9haXV6bXAwOWk0M3BvenFva3Z1MHphIn0.d4MKkC61nQ9QS6h-49rWlw"}))
                                         mymap)
                                 (.setView mymap #js [34.053667, -118.245624] 13)
                                 (.addTo locate-control mymap))
                               state)}
  []
  [:div#map {:style {:height "100vh"
                     :width "100vw"}}])

(rum/defc app []
  [:div {}
   (mapc)])

(rum/mount (app)
           (. js/document (getElementById "app")))

