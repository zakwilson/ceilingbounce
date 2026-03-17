(ns com.zakreviews.ceilingbounce.runtime
    (:require [neko.activity :refer [defactivity set-content-view!]]
              [neko.notify :as notify]
              [neko.resource :as res]
              [neko.find-view :refer [find-view]]
              [neko.threading :refer [on-ui]]
              [neko.log :as log]
              [neko.ui :as ui]
              [neko.reactive :refer [cell cell=]]
              [neko.content :as content]
              [neko.dialog :as dialog]
              [neko.ui.support.card-view]
              [clojure.java.io :as io]
              [clojure.core.async
               :as a
               :refer [>! <! >!! <!! go chan buffer close! thread
                       alts! alts!! timeout]]
              [com.zakreviews.ceilingbounce.csv :as csv]
              [amalloy.ring-buffer :refer [ring-buffer]]
              [clojure.string :as s])
    (:import android.widget.EditText
             [android.app
              Activity]
             [android.graphics
              Color
              Bitmap
              Canvas
              Bitmap$Config
              Bitmap$CompressFormat]
             java.io.File
             neko.App
             android.app.Activity
             android.net.Uri
             androidx.documentfile.provider.DocumentFile)
    (:use overtone.at-at
          com.zakreviews.ceilingbounce.common          
          com.zakreviews.ceilingbounce.graph
          com.zakreviews.ceilingbounce.theme))

(def peak-lux (cell 0))
(def test-time (atom 0))
(def runtime-pool (mk-pool))
(def output-file-name (cell ""))
(def output-file-name= (cell= #(s/trim @output-file-name)))
(def lux-30s (cell 0))
(def output (atom []))
(def dir (atom nil))
(def csv-file (atom nil))
(def png-file (atom nil))
(def running (cell false))

(declare runtime-test)


(defn nanos-since [start-time]
  (-> (. System nanoTime)
      (- start-time)))

(defn minutes-since [start-time]
  (float (/ (nanos-since start-time) 60000000000)))

(defn sample-lux []
  (swap! output conj [@lux= (minutes-since @test-time)]))

(defn percent [lux]
  (if lux
    (float (* 100 (/ lux (max 1 @lux-30s))))
    0))

(defn quote-if-string [x]
  (if (and (string? x) (not (empty? x)))
    (str \" x \")
    x))

(defn seq->csv [s]
  (apply str (concat (interpose \, (map quote-if-string s))
                     [\return \newline])))

(defn mkdir [uri dirname]
  (let [root (DocumentFile/fromTreeUri @main-activity uri)]
    (or (.findFile root dirname)
        (.createDirectory root dirname))))

(defn mkfile [dir filename type]
  (or (.findFile dir filename)
      (.createFile dir type filename)))

(defn write-csv-file [output-values file] ; appends
  (let [os (-> @main-activity
               .getContentResolver
               (.openOutputStream (.getUri file)))]
    (try
      (doseq [v output-values]
        (.write os (.getBytes (seq->csv [(second v)
                                         (first v)
                                         (percent (first v))])
                              "UTF-8"))))))

(def graph-props
  {:AxisTitleTextSize 16
   :ChartTitleTextSize 20
   :LabelsTextSize 16
   :XTitle "Minutes"
   :YTitle "Relative Output"
   :ShowLegend false
   :ZoomEnabled false
   :YAxisMin 0
   :ApplyBackgroundColor true
   :BackgroundColor Color/BLACK
   :YLabels 10
   :XLabels 10})

(def line-props
  {:Color (Color/rgb 0xFF 0x45 0)
   :LineWidth 2.0})

(declare live-chart)

(defn setup-chart []
  ; This has to be a function or (XyMultipleSeriesDataset.) fails at compile with
  ; java.lang.ExceptionInInitializerError

  (defonce live-chart
    (let [series (make-series "Runtime")
          dataset (make-dataset series)
          srenderer (make-series-renderer line-props)
          mrenderer (make-multi-renderer graph-props srenderer)
          view (make-view @main-activity dataset mrenderer)]
      (->LineGraph
       series
       srenderer
       mrenderer
       dataset
       view))))


(defn add-reading [minutes lux]
  (add-point live-chart
             minutes
             (percent lux)))

(defn save-chart []
  ; While the chart view should have a .toBitmap method, it returns nil
  ; probably because the larger chart is not drawn, so we draw it manually
  (let [bitmap (Bitmap/createBitmap 1400 1050 Bitmap$Config/ARGB_8888)
        canvas (Canvas. bitmap)
        srenderer (make-series-renderer line-props)
        mrenderer (make-multi-renderer graph-props srenderer)
        view (make-view @main-activity (:dataset live-chart) mrenderer)]
    (call-setter mrenderer
                 :ChartTitle 
                 @output-file-name=)
    (on-ui (doto view
             (.layout 0 0 1400 1050)
             (.draw canvas)
             .zoomReset)
           ; writing the bitmap has to happen after the drawing stuff is done
           ; but let's not block the UI thread
           (future (let [os (-> @main-activity
                                .getContentResolver
                                (.openOutputStream (.getUri @png-file)))]
                     (-> bitmap
                         (.compress Bitmap$CompressFormat/PNG 90 os)))))))

(defn handle-output [output-vec dir-path csv-path]
  (let [pair (last output-vec)
        lux (first pair)
        minutes (second pair)]
    (when (and minutes ; NPE
               (>= minutes 0.5))      
      (when-not (.exists (io/as-file csv-path))
        (reset! lux-30s lux)
        (clear-chart live-chart)
        (future (doseq [p @output]
                  (add-reading (second p) (first p))))))
    (when (and minutes lux)
      (add-reading minutes lux))
    (when (and minutes ; slow down the sample rate so the graph doesn't hang
               (> minutes 100)
               (< minutes 100.1))
      (stop-and-reset-pool! runtime-pool)
      (every 10000
             sample-lux
             runtime-pool
             :desc "Sample the light meter reading"))))



(defn dir-unset-alert []
  (dialog/alert @main-activity
                {:title "Storage directory not set"
                 :message "Select a storage directory in settings first"
                 :on-cancel do-nothing
                 :positive-button ["OK" do-nothing]}
                ))

(defn name-file-alert []
  (dialog/alert @main-activity
                {:title "No file name"
                 :message "Output file name cannot be empty or blank"
                 :on-cancel do-nothing
                 :positive-button ["OK" do-nothing]}
                ))

(defn runtime-test []
  (reset! dir
          (mkdir (Uri/parse (@prefs* :directory)) @output-file-name=))
  (reset! csv-file (mkfile @dir (str @output-file-name= ".csv") "text/csv"))
  (reset! png-file (mkfile @dir (str @output-file-name= ".png") "image/png"))
  (reset! running true)
  (let [start-time (. System nanoTime)]
    (swap! test-time max start-time)
    (reset! lux-30s @lux=)   ; start the graph with this, update later
    (clear-chart live-chart)
    (call-setter (:multi-renderer live-chart)
                 :ChartTitle 
                 @output-file-name=)
    (every 1000
           sample-lux
           runtime-pool
           :desc "Sample the light meter reading")))

(defn stop-runtime-test [_evt]
  (reset! running false)
  (remove-watch lux= :runtime-watch)
  (let [csv-writer (future (write-csv-file @output @csv-file))
        png-writer (future (save-chart))]
    (when (every? future-done? [csv-writer png-writer])
      (reset! output [])
      (reset! dir nil)
      (reset! csv-file nil)
      (reset! png-file nil)
      (stop-and-reset-pool! runtime-pool))))

(defn start-runtime-test [_evt]
  (cond (empty? @output-file-name) (name-file-alert)
        (nil? (@prefs* :directory)) (dir-unset-alert)
        true (runtime-test)))

(def runtime-layout [:scroll-view {:id ::runtime
                                   :layout-weight 1
                                   :layout-width :fill}
                     [:linear-layout {:layout-width :fill
                                      :orientation :vertical}
                      [:edit-text {:id ::filename
                                   :hint "Name output file"
                                   :text @output-file-name
                                   :on-text-change #(reset! output-file-name %)
                                   :layout-width :fill}]
                      [:button {:id ::runtime-test
                                :text (cell= #(if @running
                                                "Stop test"
                                                "Start test"))
                                :on-click #(if @running
                                             (stop-runtime-test %)
                                             (start-runtime-test %))}]
                      [:text-view (t :big-text
                                     {:id ::lux-now
                                      :text luxs=})]
                      [:linear-layout {:layout-width :fill
                                       :layout-height :wrap
                                       :orientation :horizontal}
                       [:text-view (t :med-text
                                      {:text "Peak: "
                                       :id ::peak-label})]
                       [:text-view (t :med-text
                                      {:id ::lux-peak
                                       :text (cell= #(str @peak-lux))})]
                       [:button {:id ::reset-button
                                 :text "Reset peak"
                                 :layout-gravity :right
                                 :on-click reset-peak}]]
                      [:card-view {:id ::chart
                                   :layout-gravity 3
                                   :layout-width :fill
                                   :layout-height :fill
                                   :card-background-color (Color/rgb 0xFF 0x45 0)}
                       [:linear-layout {:orientation :vertical}
                        [:image-view {:image-resource android.R$drawable/ic_dialog_info
                  :padding [0 4 0 0]}]]
                       ]
                      ]
                     ])

(defn activate-chart []
  (on-ui (try (-> (find-view @root-view* ::chart)
                  (.addView (:view live-chart)))
              (catch Exception e nil))))

(defn deactivate-chart []
  (on-ui (try (-> (:view live-chart)
                  .getParent
                  (.removeView (:view live-chart)))
              (catch Exception e nil))))
