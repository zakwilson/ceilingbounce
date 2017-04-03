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
             neko.App
             [android.graphics
              Color]
             [android.view ViewGroup$LayoutParams]
             [org.achartengine.chart PointStyle]
             [org.achartengine.model XYSeries]
             [org.achartengine.model XYMultipleSeriesDataset]
             [org.achartengine.renderer
              XYMultipleSeriesRenderer
              XYSeriesRenderer]
             [org.achartengine ChartFactory]
             [android.graphics Bitmap$CompressFormat]))

(def peak-lux (atom 0))
(def test-time (atom 0))

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

(defn percent [lux]
  (if lux
    (float (* 100 (/ lux (max 1 @lux-30s))))
    0))

(defn write-line [lux minutes csv-path]
  (csv/write-csv-line [lux minutes (percent lux)]
                      csv-path))

(defn get-dir-name []
  (let [dirname (read-field @main-activity ::filename)]
    (if (empty? dirname)
      "test"
      dirname)))

(defn get-dir-path []
  (str common/storage-dir (get-dir-name) "/"))

(defn path [start-time ext]
  (let [dirname (get-dir-name)]
    (str common/storage-dir dirname "/" dirname "-" start-time "." ext)))

(declare chart-view
         chart-series
         renderer
         multi-renderer
         chart-dataset)

(defn setup-chart []
  ; This has to be a function or (XyMultipleSeriesDataset.) fails at compile with
  ; java.lang.ExceptionInInitializerError
  (defonce chart-series (XYSeries. "Runtime"))
  (defonce renderer (doto (XYSeriesRenderer.)
                  (.setColor Color/YELLOW)
                  (.setLineWidth 2.0)))
  (defonce multi-renderer (doto (XYMultipleSeriesRenderer.)
                        (.setAxisTitleTextSize 16)
                        (.setChartTitleTextSize 20)
                        (.setLabelsTextSize 16)
                        (.addSeriesRenderer renderer)
                        (.setXTitle "Minutes")
                        (.setYTitle "Relative Output")
                        (.setShowLegend false)
                        (.setZoomEnabled false)
                        (.setYAxisMin 0)
                        (.setYAxisMax 120)
                        (.setApplyBackgroundColor true)
                        (.setBackgroundColor Color/BLACK)
))
  (defonce chart-dataset (doto (XYMultipleSeriesDataset.)
                       (.addSeries chart-series)))
  (on-ui (defonce chart-view (doto (ChartFactory/getLineChartView @main-activity
                                                                  chart-dataset
                                                                  multi-renderer)
                               (.setLayoutParams (ViewGroup$LayoutParams.
                                                  ViewGroup$LayoutParams/FILL_PARENT
                                                  ViewGroup$LayoutParams/FILL_PARENT))
                               ))))

(defn add-point [x y]
  (-> chart-series
      (.add x y))
  (.repaint chart-view))

(defn clear-chart []
  (.clear chart-series)
  (.repaint chart-view))

(defn add-reading [minutes lux]
  (add-point minutes
             (percent lux)))

(defn save-chart [chart-path]
  (let [chart-view (find-view @main-activity ::chart)]
    (.setDrawingCacheEnabled chart-view true)
    (with-open [o (io/output-stream chart-path)]
      (-> chart-view
          .getDrawingCache
          (.compress Bitmap$CompressFormat/PNG 90 o)))))

(defn handle-output [output-vec dir-path csv-path]
  (let [pair (last output-vec)
        lux (first pair)
        minutes (second pair)]
    (when (and minutes ; NPE
               (>= minutes 0.5))      
      (when-not (.exists (io/as-file dir-path))
        (.mkdirs (io/as-file dir-path)))
      (when-not (.exists (io/as-file csv-path))
        (swap! lux-30s identity* lux)
        (clear-chart)
        (common/play-notification)
        (doseq [p @output]
          (write-line (first p) (second p) csv-path)
          (add-reading (second p) (first p))
          ))
      (write-line lux minutes csv-path))
    (when (and minutes lux)
      (add-reading minutes lux))))

(defn stop-runtime-test [_evt]
  (remove-watch common/lux :runtime-watch)
  (save-chart (path @test-time "png"))
  ; TODO do things with output before it's cleared
  (swap! output identity* [])
  (update-main ::runtime-test
             :text "Start runtime test"
             :on-click #'runtime-test))

(defn runtime-test [_evt]
  (.mkdirs (io/as-file common/storage-dir))
  (let [start-time (. System nanoTime)
        dir-path (get-dir-path)
        csv-path (path start-time "csv")]
    (swap! test-time max start-time)
    (swap! lux-30s identity* @common/lux) ; start the graph with this, update later
    (clear-chart)
    (.setChartTitle multi-renderer (read-field @main-activity ::filename))
    (add-watch common/lux :runtime-watch
               (fn [_key _ref _old new]
                 (handle-lux-rt new start-time)))
    (add-watch output :runtime-watch
               (fn [_key _ref _old new]
                 (handle-output new dir-path csv-path)))
    (update-main ::runtime-test
            :text "Stop test"
            :on-click #'stop-runtime-test)))

(defn fix-chart-view []
  (try
    (-> chart-view .getParent (.removeView chart-view))
    (catch Exception e nil)))

(defn mk-runtime-layout [] ; has to be this way because the compiler hates the graph
  (setup-chart)
  (defonce runtime-layout [:linear-layout (merge common/linear-layout-opts
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
                                              :layout-height :wrap
                                              :layout-gravity 1}
                            [:text-view {:text "Peak: "
                                         :id ::peak-label}]
                            [:text-view {:id ::lux-peak
                                         :layout-to-right-of ::peak-label
                                         :text "0"}]
                            [:button {:id ::reset-button
                                      :text "Reset peak"
                                      :layout-below ::lux-peak
                                      :on-click #'reset-peak}]]
                           [:linear-layout (merge common/linear-layout-opts
                                                  {:id ::chart
                                                   :layout-gravity 3
                                                   :layout-height :fill})
                            chart-view]
                           ]))

(defn activate-tab [& _args]
  (mk-runtime-layout)
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
  (common/set-30s nil)
  (fix-chart-view))
