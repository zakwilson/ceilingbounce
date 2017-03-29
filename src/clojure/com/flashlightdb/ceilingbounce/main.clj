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

(defn identity* [_ replacement]
  replacement)

(defactivity com.flashlightdb.ceilingbounce.MainActivity
  :key :main
  (onCreate [this bundle]
    (swap! common/main-activity
           identity* this)
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
               (common/do-nothing)))))
                
    (.superOnCreate this bundle)
    (neko.debug/keep-screen-on this)
    (on-ui
      (set-content-view! this
                         runtime/runtime-layout)))

  (onResume [this]
            (.registerListener sm
                               sensor-listener
                               light-sensor
                               (SensorManager/SENSOR_DELAY_NORMAL))
            (.superOnResume this))
  (onPause [this]
           (.unregisterListener sm sensor-listener)
           (.superOnPause this))
  )
