(ns com.flashlightdb.ceilingbounce.throw
    (:require [neko.activity :refer [defactivity set-content-view!]]
              [neko.notify :as notify]
              [neko.resource :as res]
              [neko.find-view :refer [find-view]]
              [neko.threading :refer [on-ui]]
              [neko.log :as log]
              [neko.ui :as ui]
              [clojure.java.io :as io]
              [clojure.core.async
               :as a
               :refer [>! <! >!! <!! go chan buffer close! thread
                       alts! alts!! timeout]]
              [com.flashlightdb.ceilingbounce.common
               :as common
               :refer [identity*
                       config
                       main-activity
                       do-nothing
                       update-ui
                       read-field
                       at-pool
                       average]]
              [amalloy.ring-buffer :refer [ring-buffer]]
              )
    (:use overtone.at-at)
    (:import android.widget.EditText
             [android.app
              Activity
              Notification]
             neko.App))

(def peak-cd (atom 0))

(defn update-conversion [& _args]
  (try
    (let [conversion (Float/parseFloat (read-field @main-activity ::conversion))]
      (swap! config assoc :effective-distance conversion))
    (catch Exception e nil))
  (update-ui @main-activity ::conversion
             :text (str (@config :effective-distance))))

(defn lux-to-cd [lux]
  (Math/round
   (* lux (Math/pow (@config :effective-distance) 2))))

(defn cd-to-m [cd]
  (Math/round (Math/sqrt (* 4 cd))))

(def peak-cd-thirty (atom 0))

(defn update-thirty [cd]
  (swap! peak-cd-thirty max cd))

(defn handle-thirty [cd]
  (update-ui @main-activity ::candela-thirty
             :text (str cd))
  (update-ui @main-activity ::throw-thirty
             :text (str (cd-to-m cd))))

(def watching-thirty (atom false))

(defn watch-thirty []
  (when-not @watching-thirty
    (swap! watching-thirty identity* true)
    (common/play-notification)
    (add-watch common/lux
               :thirty-watch
               (fn [_key _ref _old new]
                 (update-thirty (lux-to-cd new))))
    (after (* 10 1000)
           (fn []
             (swap! watching-thirty identity* false)
             (remove-watch common/lux :thirty-watch))
           at-pool)))

(defn reset-peak [_evt]
  (swap! peak-cd min 0)
  (swap! peak-cd-thirty min 0)
  (common/reset-peak))

(defn handle-lux [activity lux]
  (let [cd (lux-to-cd lux)
        m (cd-to-m cd)]
    (update-ui activity ::candela-now
               :text (str cd))
    (swap! peak-cd max cd)))

(defn handle-peak [activity cd]
  (update-ui activity ::candela-peak
             :text (str cd))
  (update-ui activity ::throw-peak
             :text (str (cd-to-m cd))))

(def throw-layout
  [:linear-layout (merge common/linear-layout-opts
                         {:def `throw-layout-handle})
   [:linear-layout {:layout-width :fill
                    :layout-height :wrap
                    :orientation :horizontal}
    [:text-view {:id ::conversion-label
                 :text "Calculated distance: "}]
    [:edit-text {:id ::conversion
                 :text (str (@config :effective-distance))
                 :layout-weight 1
                 }]
    [:button {:text "Update"
              :id ::update-button
              :on-click #'update-conversion}]]
   [:text-view {:id ::candela-now
                :text-size [48 :dip]}]

      [:button {:id ::reset-button
             :text "Reset"
             :on-click #'reset-peak}]
   
   [:relative-layout {:layout-width :fill
                    :layout-height :wrap}
    [:text-view {:text "Peak cd: "
                 :id ::candela-peak-label}]
    [:text-view {:id ::candela-peak
                 :layout-to-right-of ::candela-peak-label
                 :text "0"}]
    [:text-view {:text " | "
                 :id ::candela-separator
                 :layout-to-right-of ::candela-peak}]
    [:text-view {:text "30s cd: "
                 :id ::candela-thirty-seconds
                 :layout-to-right-of ::candela-separator}]
    [:text-view {:text "0"
                 :id ::candela-thirty
                 :layout-to-right-of ::candela-thirty-seconds}]]
   
   [:relative-layout {:layout-width :fill
                      :layout-height :wrap
                      :layout-gravity 1}
    [:text-view {:text "Peak m: "
                 :id ::throw-peak-label}]
    [:text-view {:id ::throw-peak
                 :layout-to-right-of ::throw-peak-label
                 :text "0"}]
    [:text-view {:text " | "
                 :id ::throw-separator
                 :layout-to-right-of ::throw-peak}]
    [:text-view {:text "30s m: "
                 :id ::throw-thirty-seconds
                 :layout-to-right-of ::throw-separator}]
    [:text-view {:text "0"
                 :id ::throw-thirty
                 :layout-to-right-of ::throw-thirty-seconds}]]
   ])

(defn activate-tab [& _args]
  (on-ui
   (set-content-view! @main-activity
                      throw-layout))
  (update-ui @main-activity ::conversion
             :text (str (@config :effective-distance)))
  (add-watch common/lux
             :throw-instant
             (fn [_key _ref _old new]
               (handle-lux @main-activity new)))
  (add-watch peak-cd
             :cd-peak
             (fn [_key _ref _old new]
               (handle-peak @main-activity new)))
  (add-watch peak-cd-thirty
             :thirty-watch
             (fn [_key _ref _old new]
               (handle-thirty new)))
  (common/set-30s watch-thirty))

(defn deactivate-tab [& _args]
  (remove-watch common/lux :throw-instant)
  (remove-watch peak-cd :cd-peak)
  (remove-watch common/lux :thirty-watch)
  (remove-watch peak-cd-thirty :thirty-watch)
  (common/set-30s nil))
