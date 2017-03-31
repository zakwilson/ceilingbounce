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
              [com.flashlightdb.ceilingbounce.common :as common
               :refer [identity*
                       config
                       main-activity
                       do-nothing
                       update-ui
                       read-field
                       update-main]]
              )
    (:import android.widget.EditText
             [android.app
              Activity]
             java.io.File
             neko.App))

(def peak-lux (atom 0))

(defn handle-lux [lux]
  (update-main ::lux-now
               :text (str lux))
  (swap! peak-lux max lux))

(defn handle-peak [lux]
  (update-main ::lux-peak
               :text (str lux)))

(defn reset-peak [_evt]
  (common/reset-peak)
  (swap! peak-lux min 0))

(declare runtime-test)

(def output (atom []))

(defn nanos-since [start-time]
  (-> (. System nanoTime)
      (- start-time)))

(defn minutes-since [start-time]
  (float (/ (nanos-since start-time) 60000000000)))

(defn handle-lux-rt [lux start-time]
  (let [offset (minutes-since start-time)]
    (swap! output conj [lux offset])))

(def lux-30s (atom 0))

(defn write-line [lux minutes csv-path]
  (csv/write-csv-line [lux minutes (float (* 100 (/ lux @lux-30s)))]
                      csv-path))

(defn handle-output [output-vec path csv-path]
  (let [pair (last output-vec)
        lux (first pair)
        minutes (second pair)]
    (when (and minutes ; NPE
               (>= minutes 0.5))      
      (when-not (.exists (io/as-file path))
        (.mkdirs (File. path)))
      (when-not (.exists (io/as-file csv-path))
        (swap! lux-30s identity* lux)
        (common/play-notification)
        (doseq [p @output]
          (write-line (first p) (second p) csv-path)))
      (write-line lux minutes csv-path))))

(defn stop-runtime-test [_evt]
  (remove-watch common/lux :runtime-watch)
  ; TODO do things with output before it's cleared
  (swap! output identity* [])
  (update-main ::runtime-test
             :text "Start runtime test"
             :on-click #'runtime-test))

(defn runtime-test [_evt]
  (.mkdirs (File. common/storage-dir))
  (let [start-time (. System nanoTime)
        dirname (read-field @main-activity ::filename)
        dirname (if (empty? dirname)
                  "test"
                  dirname) ; TODO - this, more elegantly
        path (str common/storage-dir dirname "/")
        csv-path (str path dirname "-" start-time ".csv")]
    (add-watch common/lux :runtime-watch
               (fn [_key _ref _old new]
                 (handle-lux-rt new start-time)))
    (add-watch output :runtime-watch
               (fn [_key _ref _old new]
                 (handle-output new path csv-path)))

    (update-main ::runtime-test
            :text "Stop test"
            :on-click #'stop-runtime-test)))

(def runtime-layout
  [:linear-layout (merge common/linear-layout-opts
                         {:def `runtime-layout-handle})
   [:edit-text {:id ::filename
                :hint "Name output file"
                :layout-width :fill}]
   [:button {:id ::runtime-test
             :text "Start runtime test"
             :on-click #'runtime-test}]
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

(defn activate-tab [& _args]
  (on-ui
   (set-content-view! @main-activity
                      runtime-layout))
  (add-watch common/lux
             :lux-instant-runtime
             (fn [_key _ref _old new]
               (handle-lux new)))
  (add-watch peak-lux
             :lux-peak-runtime
             (fn [_key _ref _old new]
               (handle-peak new))))

(defn deactivate-tab [& _args]
  (remove-watch common/lux :lux-instant-runtime)
  (remove-watch peak-lux :lux-peak-runtime)
  (common/set-30s nil))
