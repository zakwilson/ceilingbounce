(ns com.flashlightdb.ceilingbounce.runtime
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
              [com.flashlightdb.ceilingbounce.csv :as csv]
              [com.flashlightdb.ceilingbounce.common :as common]
              )
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
             java.io.File
             neko.App
             android.graphics.Bitmap
             android.graphics.Canvas
             com.github.mikephil.charting.charts.LineChart))

(def battery-dead-notification
  {:ticker-text "Battery dead"
   :content-title "Battery dead"
   :content-text "Got 0 lux"
   :action
   [:activity "com.flashlightdb.ceilingbounce.MainActivity"]})

(def battery-low-notification
  {:ticker-text "Battery low"
   :content-title "Battery low"
   :content-text "Got < 10 lux"
   :action [:activity "com.flashlightdb.ceilingbounce.MainActivity"]})

(defn chart-runtime [runtime-data]
  (comment let [minutes (map #(/ (first %) 600.0) runtime-data) ; FIXME with new lib
        raw-outputs (map second runtime-data)
        max-output (max (apply max raw-outputs) 1) ; let's not div0
        max-minute (apply max minutes)
        outputs (map #(* 100.0 (/ % max-output)) raw-outputs)
        chart-data (partition 2 (interleave minutes outputs))
        chart (charts/xy-plot :width 1400 :height 1050
                        :xmin 0
                        :ymin 0
                        :xmax (Math/ceil (* max-minute 1.1))
                        :ymax (Math/ceil (* max-output 1.1)))]

    (charts/add-points chart
                       chart-data
                       :size 2)))


(defn write-png [path chart]
  (with-open [out-file (io/output-stream path)]
    (.compress (.getChartBitmap chart)
               android.graphics.Bitmap$CompressFormat/PNG)
    90
    out-file))


(defn runtime-test [_evt]
  (.mkdirs (File. common/storage-dir))
  (let [start-time (. System nanoTime)
        activity @common/main-activity
        dirname (.getText (find-view activity
                                     (common/main-tag "filename")))
        dirname (if (empty? dirname)
                  "test"
                  dirname) ; TODO - this, more elegantly
        path (str common/storage-dir dirname "/")
        csv-path (str path start-time ".csv")
        png-path (str path start-time ".png")
        output (atom [])
        writer-chan (chan 30)
        battery-dead (atom false)
        battery-low (atom false)
        sm (cast SensorManager (.getSystemService ^Activity activity "sensor"))
        light-sensor (.getDefaultSensor ^SensorManager sm
                                        (Sensor/TYPE_LIGHT))
        sensor-listener
        (reify SensorEventListener
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
                                 (notify/notification battery-dead-notification))
                    (compare-and-set! battery-dead false true))
                  (do (notify/cancel :battery-dead)
                      (compare-and-set! battery-dead true false)))
                (if (> 10 (first (.values evt)))
                  (when-not @battery-low
                    (notify/cancel :battery-dead)
                    (notify/fire :battery-low
                                 (notify/notification battery-low-notification))
                    (compare-and-set! battery-low false true))
                  (do (notify/cancel :battery-low)
                      (compare-and-set! battery-low true false))))
              (catch Exception e
                (log/e "ERROR!" :exception e))))
          
          (onAccuracyChanged [activity s a]
            (common/do-nothing)))
        ]

    (ui/config (find-view activity
                          (common/main-tag "runtime-test"))
               :text "Test starting, please wait..."
               :on-click common/do-nothing)
    
    (.mkdirs (File. path))
    
    (go
      (while true
        (csv/write-csv-line (<! writer-chan) csv-path)))
    
    (defn stop-runtime-test [_evt]
      (.unregisterListener sm sensor-listener)
      (future
        (comment let [chart nil] ; FIXME
          (write-png png-path chart)))
      (ui/config (find-view activity
                            (common/main-tag "runtime-test"))
              :text "Start runtime test"
              :on-click #'runtime-test))

    (ui/config (find-view activity
                          (common/main-tag "runtime-test"))
            :text "Stop test"
            :on-click #'stop-runtime-test)
    
    (.registerListener sm
                       sensor-listener
                       light-sensor
                       (SensorManager/SENSOR_DELAY_NORMAL))))
