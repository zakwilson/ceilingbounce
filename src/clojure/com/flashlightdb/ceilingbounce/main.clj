(ns com.flashlightdb.ceilingbounce.main
    (:require [neko.activity :refer [defactivity set-content-view!]]
              [neko.debug :refer [*a]]
              [neko.notify :as notify]
              [neko.resource :as res]
              [neko.find-view :refer [find-view]]
              [neko.threading :refer [on-ui]]
              [neko.log :as log]
              [neko.ui :as ui]
              [clojure.data.csv :as csv]
              [clojure.java.io :as io]
              [clojure.core.async
               :as a
               :refer [>! <! >!! <!! go chan buffer close! thread
                       alts! alts!! timeout]])
    (:import android.widget.EditText
             [android.hardware
              SensorManager
              SensorEventListener
              Sensor
              SensorEvent
              SensorListener]
             [android.app
              Activity
              Notification]
             neko.App))


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

(defn do-nothing [])

(defn ^{:dynamic true} runtime-test [_evt]
  (ui/config (find-view (*a :main)
                        ::runtime-test)
            :text "Test starting..."
            :on-click do-nothing)
  (Thread/sleep 30000)
  (let [start-time (. System nanoTime)
        activity (*a :main)
        filename (.getText (find-view activity
                                      ::filename))
        path (str "/storage/emulated/0/scratch/" filename "." start-time)
        output (atom [])
        write-csv (fn [output-value]
                    (with-open [out-file (io/writer (str path ".csv"))]
                      (csv/write-csv out-file output-value)))
        write-output (fn [output-value]
                       (spit path output-value :append true))
        writer-chan (chan 30)
        battery-dead (atom false)
        battery-low (atom false)
        sm (cast SensorManager (.getSystemService ^Activity activity "sensor"))
        light-sensor (.getDefaultSensor ^SensorManager sm
                                        (Sensor/TYPE_LIGHT))
        sensor-listener (reify SensorEventListener
                          (onSensorChanged [activity evt]
                            (try
                              (let [pair
                                    [(-> (. System nanoTime)
                                          (- start-time)
                                          (/ 100000000.0)
                                          Math/floor
                                          int)
                                     (first (.values evt))]]
                                (>!! writer-chan pair)
                                (swap! output
                                       conj
                                       pair)
                                (if (> 1 (first (.values evt)))
                                  (when-not @battery-dead
                                    (notify/cancel :battery-low)
                                    (notify/fire :battery-dead
                                                 (notify/notification {:ticker-text "Battery dead"
                                                                       :content-title "Battery dead"
                                                                       :content-text "Got 0 lux"
                                                                       :action [:activity "com.flashlightdb.ceilingbounce.MainActivity"]}))
                                    (compare-and-set! battery-dead false true))
                                  (do (notify/cancel :battery-dead)
                                      (compare-and-set! battery-dead true false)))
                                (if (> 10 (first (.values evt)))
                                  (when-not @battery-low
                                    (notify/cancel :battery-dead)
                                    (notify/fire :battery-low
                                                 (notify/notification {:ticker-text "Battery low"
                                                                       :content-title "Battery low"
                                                                       :content-text "Got < 10 lux"
                                                                       :action [:activity "com.flashlightdb.ceilingbounce.MainActivity"]}))
                                    (compare-and-set! battery-low false true))
                                  (do (notify/cancel :battery-low)
                                      (compare-and-set! battery-low true false))))
                              (catch Exception e
                                (log/e "ERROR!" :exception e))))
                          (onAccuracyChanged [activity s a]
                                        ;do nothing
                            ))]

    (go
      (while true
        (write-output (<! writer-chan))))
    
    (defn ^{:dynamic true} stop-runtime-test [_evt]
      (.unregisterListener sm sensor-listener)
      (future (write-csv @output))
      (ui/config (find-view activity
                         ::runtime-test)
              :text "Run test"
              :on-click runtime-test))

    (ui/config (find-view activity
                       ::runtime-test)
            :text "Stop test"
            :on-click stop-runtime-test)
    
    (.registerListener sm
                       sensor-listener
                       light-sensor
                       (SensorManager/SENSOR_DELAY_NORMAL))))

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
             :on-click runtime-test}]
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
