(ns com.flashlightdb.ceilingbounce.main
    (:require [neko.activity :refer [defactivity set-content-view!]]
              [neko.notify :as notify]
              [neko.resource :as res]
              [neko.find-view :refer [find-view]]
              [neko.threading :refer [on-ui]]
              [neko.log :as log]
              [neko.ui :as ui]
              neko.tools.repl
              [clojure.java.io :as io]
              [clojure.core.async
               :as a
               :refer [>! <! >!! <!! go chan buffer close! thread
                       alts! alts!! timeout]]
              [com.flashlightdb.ceilingbounce.runtime :as runtime]
              [com.flashlightdb.ceilingbounce.common :as common])
    (:import android.widget.EditText
             [android.hardware
              SensorManager
              SensorEventListener
              Sensor
              SensorEvent
              SensorListener]
             [android.app
              Activity]
             neko.App))


;; We execute this function to import all subclasses of R class. This gives us
;; access to all application resources.
(res/import-all)

(declare ^android.app.Activity com.flashlightdb.ceilingbounce.MainActivity)

(def peak-lux (atom 0))
(defn reset-peak [_evt]
  (swap! peak-lux min 0))

(def main-layout
  [:linear-layout {:orientation :vertical
                   :layout-width :fill
                   :layout-height :wrap
                   :def `layout-handle}
   [:edit-text {:id ::filename
                :hint "Name output file"
                :layout-width :fill}]
   [:button {:id ::runtime-test
             :text "Start runtime test"
             :on-click #'runtime/runtime-test}]
   ;; [:view {;:background "#eeeeeeeeee"
   ;;         :layout-width :fill
   ;;         :layout-height [1 :dip]
   ;;         :layout-margin-top [5 :dip]
   ;;         :layout-margin-bottom [5 :dip]}]
   [:text-view {:id ::lux-now
                :text-size [48 :dip]}]
   [:relative-layout {:layout-width :fill
                      :layout-height :fill}
    [:text-view {:text "Peak: "
                 ;:layout-width :fill
                 :id ::peak-label}]
    [:text-view {:id ::lux-peak
                 ;:layout-width :fill
                 :layout-to-right-of ::peak-label
                 :text "0"}]
    [:button {:id ::reset-button
              :text "Reset peak"
              :layout-below ::lux-peak
              :on-click #'reset-peak}]]
   ])

(defactivity com.flashlightdb.ceilingbounce.MainActivity
  :key :main
  (onCreate [this bundle]
    (swap! common/main-activity
           (fn [_ replacement]
             replacement)
           this)
    (def sm (cast SensorManager (.getSystemService ^Activity this "sensor")))
    (def light-sensor (.getDefaultSensor ^SensorManager sm
                                         (Sensor/TYPE_LIGHT)))
    (def sensor-listener
      (let [activity this]
           (reify SensorEventListener
             (onSensorChanged [this evt]
               (let [lux (first (.values evt))]
                 (ui/config (find-view activity
                                       ::lux-now)
                            :text (str lux))
                 (swap! peak-lux max lux)))
             (onAccuracyChanged [this s a]
               (common/do-nothing)))))
                
    (.superOnCreate this bundle)
    (neko.debug/keep-screen-on this)
    (on-ui
      (set-content-view! this
                         main-layout)))

  (onResume [this]
            (.registerListener sm
                               sensor-listener
                               light-sensor
                               (SensorManager/SENSOR_DELAY_NORMAL))
            (add-watch peak-lux :peak-ui (fn [_key _ref _old new]
                                           (ui/config (find-view this
                                                                 ::lux-peak)
                                                      :text (str new))))
            (.superOnResume this))
  (onPause [this]
           (.unregisterListener sm sensor-listener)
           (.superOnPause this))
  )
