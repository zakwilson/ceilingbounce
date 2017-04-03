(ns com.flashlightdb.ceilingbounce.main
    (:require [neko.activity :refer [defactivity set-content-view!]]
              [neko.notify :as notify]
              [neko.resource :as res]
              [neko.find-view :refer [find-view]]
              [neko.threading :refer [on-ui]]
              [neko.action-bar :refer [setup-action-bar tab-listener]]
              [neko.log :as log]
              [neko.ui :as ui]
              neko.tools.repl
              [clojure.java.io :as io]
              [clojure.core.async
               :as a
               :refer [>! <! >!! <!! go chan buffer close! thread
                       alts! alts!! timeout]]
              [com.flashlightdb.ceilingbounce.runtime :as runtime]
              [com.flashlightdb.ceilingbounce.lumens :as lumens]
              [com.flashlightdb.ceilingbounce.throw :as throw]
              [com.flashlightdb.ceilingbounce.common
               :as common
               :refer [identity* config main-activity do-nothing]])
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

(defactivity com.flashlightdb.ceilingbounce.MainActivity
  :key :main
  (onCreate [this bundle]
    (swap! main-activity
           identity* this)
    (common/read-config)
    (def sm (cast SensorManager (.getSystemService ^Activity this "sensor")))
    (def light-sensor (.getDefaultSensor ^SensorManager sm
                                         (Sensor/TYPE_LIGHT)))
    (def sensor-listener
      (let [activity this]
           (reify SensorEventListener
             (onSensorChanged [this evt]
               (let [lux (first (.values evt))]
                 (swap! common/lux identity* lux)))
             (onAccuracyChanged [this s a]
               (do-nothing)))))
                
    (.superOnCreate this bundle)
    (neko.debug/keep-screen-on this)
    (runtime/setup-chart)
    (on-ui
     (try ; FIXME why does this error?
       (setup-action-bar this
                         {:navigation-mode :tabs
                          :id ::action-bar
                          :tabs [[:tab {:text "Lumens"
                                        :tab-listener (tab-listener
                                                       :on-tab-selected @#'lumens/activate-tab
                                                       :on-tab-unselected @#'lumens/deactivate-tab)}]
                                 [:tab {:text "Throw"
                                        :tab-listener (tab-listener
                                                       :on-tab-selected @#'throw/activate-tab
                                                       :on-tab-unselected @#'throw/deactivate-tab)}]
                                 [:tab {:text "Runtime"
                                        :tab-listener (tab-listener
                                                       :on-tab-selected @#'runtime/activate-tab
                                                       :on-tab-unselected @#'runtime/deactivate-tab)}]]})
       (catch Exception e nil))))

  (onResume [this]
            (.registerListener sm
                               sensor-listener
                               light-sensor
                               (SensorManager/SENSOR_DELAY_NORMAL))
            (.superOnResume this))
  (onPause [this]
           (.unregisterListener sm sensor-listener)
           (.superOnPause this)))
