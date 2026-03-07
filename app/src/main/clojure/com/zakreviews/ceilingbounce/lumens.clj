(ns com.zakreviews.ceilingbounce.lumens
    (:require [neko.activity :refer [defactivity set-content-view!]]
              [neko.notify :as notify]
              [neko.resource :as res]
              [neko.find-view :refer [find-view]]
              [neko.threading :refer [on-ui]]
              [neko.log :as log]
              [neko.ui :as ui]
              [neko.ui.support.material]
              [neko.reactive :refer [cell cell=]]
              [clojure.java.io :as io]
              [clojure.core.async
               :as a
               :refer [>! <! >!! <!! go chan buffer close! thread
                       alts! alts!! timeout]]
              [com.zakreviews.ceilingbounce.common
               :as common
               :refer [identity*
                       config
                       main-activity
                       do-nothing
                       update-ui
                       read-field
                       at-pool
                       average
                       set-30s
                       ui-tree* root-view*]]
              [amalloy.ring-buffer :refer [ring-buffer]]
              )
    (:import android.widget.EditText
             [android.app
              Activity
              Notification]
             neko.App))

(defn update-conversion [& _args]
  (try
    (let [conversion (Float/parseFloat (read-field @main-activity ::conversion))]
      (swap! config assoc :lux-to-lumens conversion))
    (catch Exception e nil))
  (update-ui @main-activity ::conversion
             :text (str (@config :lux-to-lumens))))

(defn update-thirty []
  (update-ui @main-activity ::lumens-thirty
             :text (read-field @main-activity ::lumens-now))
  (common/play-notification))

(def peak-lumens (cell 0))

(defn reset-peak [_evt]
  (swap! peak-lumens min 0)
  (common/reset-peak))

(defn round [^Float n]
  (Math/round n))

(defn lumens [lux]
  (round (/ @common/lux (@config :lux-to-lumens))))

(add-watch common/lux
           :peak-lumens
           (fn [_key _ref _old new]
             (swap! peak-lumens max (lumens new))))

(def lumens-layout
  [:scroll-view {:id ::lumens
                 :layout-weight 1
                 :layout-width :fill}
   [:linear-layout {:layout-width :fill
                    :orientation :vertical}
    [:linear-layout {:layout-width :fill
                     :layout-height :wrap
                     :orientation :horizontal}
     [:text-view {:id ::conversion-label
                  :text "Lux per lumen: "}]
     [:edit-text {:id ::conversion
                  :text (cell= #(str (@config :lux-to-lumens)))
                  :layout-width :fill
                  :layout-weight 1
                  }]
     [:button {:text "Update"
               :on-click #'update-conversion}]]
    [:text-view {:id ::lumens-now
                 :text (cell= #(str (lumens @common/lux)))
                 :text-size [48 :dip]}]

    [:button {:id ::reset-button
              :text "Reset"
              :on-click #'reset-peak}]
    
    [:relative-layout {:layout-width :fill
                       :layout-height :fill}
     [:text-view {:text "Peak: "
                                        ;:layout-width :fill
                  :id ::peak-label}]
     [:text-view {:id ::lumens-peak
                                        ;:layout-width :fill
                  :layout-to-right-of ::peak-label
                  :text (cell= #(str @peak-lumens))}]
     [:text-view {:text " | "
                  :id ::lumens-separator
                  :layout-to-right-of ::lumens-peak}]
     [:text-view {:text "30s: "
                  :id ::thirty-seconds
                  :layout-to-right-of ::lumens-separator}]
     [:text-view {:text "0"
                  :id ::lumens-thirty
                  :layout-to-right-of ::thirty-seconds}]]]

   ])
