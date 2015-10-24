(ns com.flashlightdb.ceilingbounce.main
    (:require [neko.activity :refer [defactivity set-content-view!]]
              [neko.debug :refer [*a]]
              [neko.notify :refer [toast]]
              [neko.resource :as res]
              [neko.find-view :refer [find-view]]
              [neko.threading :refer [on-ui]]
              [neko.log :as log]
              [neko.ui :as ui]
              [clojure.data.csv :as csv]
              [clojure.java.io :as io])
    (:import android.widget.EditText
             [android.hardware
              SensorManager
              SensorEventListener
              Sensor
              SensorEvent
              SensorListener]
             android.app.Activity))


;; We execute this function to import all subclasses of R class. This gives us
;; access to all application resources.
(res/import-all)

(declare ^android.widget.LinearLayout layout-handle)
(declare ^android.app.Activity com.flashlightdb.ceilingbounce.MainActivity)

(defmacro update-ui [tag & body]
  `(on-ui (-> layout-handle
              .getTag
              ~tag
              ~@body)))

(defn ^{:dynamic true} run-test [_evt]
  (let [start-time (. System nanoTime)
        activity (*a :main)
        filename (.getText (find-view activity
                                      ::filename))
        path (str "/storage/emulated/0/scratch/" filename "." start-time ".csv")
        output (atom [])
        write-output (fn [output-value]
                       (with-open [out-file (io/writer path)]
                         (csv/write-csv out-file output-value)))
        sm (cast SensorManager (.getSystemService ^Activity activity "sensor"))
        light-sensor (.getDefaultSensor ^SensorManager sm
                                        (Sensor/TYPE_LIGHT))
        sensor-listener (reify SensorEventListener
                          (onSensorChanged [activity evt]
                            (try
                              (swap! output
                                     conj
                                     [(-> (. System nanoTime)
                                          (- start-time)
                                          (/ 100000000.0)
                                          Math/floor
                                          int)
                                      (first (.values evt))])
                              (catch Exception e
                                (log/e "ERROR!" :exception e))))
                          (onAccuracyChanged [activity s a]
                                        ;do nothing
                            ))]
    
    (defn ^{:dynamic true} stop-test [_evt]
      (.unregisterListener sm sensor-listener)
      (ui/config (find-view activity
                         ::run-test)
              :text "Run test"
              :on-click run-test))

    (ui/config (find-view activity
                       ::run-test)
            :text "Stop test"
            :on-click stop-test)
    
    (.registerListener sm
                       sensor-listener
                       light-sensor
                       (SensorManager/SENSOR_DELAY_NORMAL))
    (add-watch output nil
               (fn [_key _ref _old-value new-value]
                 (write-output new-value)))
    ))

(def main-layout
  [:linear-layout {:orientation :vertical
                   :layout-width :fill
                   :layout-height :wrap
                   :def `layout-handle}
   [:edit-text {:id ::filename
                :hint "Name output file"
                :layout-width :fill}]
   [:button {:id ::run-test
             :text "Run test"
             :on-click run-test}]
   ])

(defactivity com.flashlightdb.ceilingbounce.MainActivity
  :key :main
  (onCreate [this bundle]

    ;; (def sm (cast SensorManager (.getSystemService ^Activity this "sensor")))
    ;; (def light-sensor (.getDefaultSensor ^SensorManager sm
    ;;                                      (Sensor/TYPE_LIGHT)))

    ;; (def sensor-listener
    ;;   (reify SensorEventListener
    ;;     (onSensorChanged [this evt]
    ;;       (neko.log/i (first (.values evt))))
    ;;     (onAccuracyChanged [this s a]
    ;;       ;do nothing
    ;;       )))
                
    (.superOnCreate this bundle)
    (neko.debug/keep-screen-on this)
    (on-ui
      (set-content-view! this
                         main-layout)))

  ;; (onResume [this]
  ;;           (.registerListener sm
  ;;                              sensor-listener
  ;;                              light-sensor
  ;;                              (SensorManager/SENSOR_DELAY_NORMAL))
  ;;           (.superOnResume this))
  ;; (onPause [this]
  ;;          (.unregisterListener sm sensor-listener)
  ;;          (.superOnPause this))
  )
