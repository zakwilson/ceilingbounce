(ns com.zakreviews.ceilingbounce.runtime
    (:require [neko.activity :refer [defactivity set-content-view!]]
              [neko.notify :as notify]
              [neko.resource :as res]
              [neko.find-view :refer [find-view]]
              [neko.threading :refer [on-ui]]
              [neko.log :as log]
              [neko.reactive :refer [cell cell=]]
              [neko.content :as content]
              [neko.dialog :as dialog]
              [clojure.java.io :as io]
              [com.zakreviews.ceilingbounce.csv :as csv]
              [com.zakreviews.ceilingbounce.lumens :as lumens]
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
             androidx.documentfile.provider.DocumentFile
             [com.androidplot.xy
              SimpleXYSeries
              SimpleXYSeries$ArrayFormat
              XYPlot
              XYGraphWidget$Edge
              LineAndPointFormatter
              BoundaryMode]
             com.androidplot.ui.Insets
             java.text.DecimalFormat
             [android.graphics Paint$Align]
             android.view.ViewGroup$LayoutParams
             android.view.ViewGroup$LayoutParams
             )
    (:use overtone.at-at
          com.zakreviews.ceilingbounce.common          
          com.zakreviews.ceilingbounce.theme))

(def peak-lux (cell 0))
(def test-time (atom 0))
(def runtime-pool (mk-pool))
(def output-file-name (cell ""))
(def output-file-name= (cell= #(s/trim @output-file-name)))
(def output (atom []))
(def dir (atom nil))
(def csv-file (atom nil))
(def png-file (atom nil))
(def running (cell false))
(def plot-type (cell :percent))
(def begin-threshold (cell 100))
(def end-threshold (cell 10))

(declare runtime-test)

(defn nanos-since [start-time]
  (-> (. System nanoTime)
      (- start-time)))

(defn minutes-since [start-time]
  (float (/ (nanos-since start-time) 60000000000)))

(defn sample-lux []
  (swap! output conj [(minutes-since @test-time) @lux=]))

(defn percent [lux]
  (if lux
    (float (* 100 (/ lux (max 1 @thirty*))))
    0))

(defn inv-percent [pct]
  (float (* (/ pct 100) @thirty*)))

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
        (.write os (.getBytes (seq->csv [(first v)
                                         (second v)
                                         (percent (first v))])
                              "UTF-8"))))))

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

(declare plot series)

(defn make-chart []
  (def plot (XYPlot. @main-activity @output-file-name=))
  (on-ui
   (.setLayoutParams plot
                     (ViewGroup$LayoutParams. ViewGroup$LayoutParams/MATCH_PARENT
                                              ViewGroup$LayoutParams/MATCH_PARENT)))
  (def series (SimpleXYSeries. ""))
  (def formatter (LineAndPointFormatter. (unchecked-int 0xFFFF4500) nil nil nil))
  (.addSeries ^XYPlot plot ^SimpleXYSeries series ^LineAndPointFormatter formatter)
  (let [title-paint (.getLabelPaint (.getTitle plot))]
    (.setTextSize title-paint 64)
    (.setColor title-paint Color/WHITE))
  (.setMargins (.getTitle plot ) 0 60 0 0)
  (.setVisible (.getLegend plot) false)
  (.setColor (.getBackgroundPaint plot) Color/BLACK)
  (.setColor (.getBackgroundPaint (.getGraph plot)) Color/BLACK)
  (.setColor (.getGridBackgroundPaint (.getGraph plot)) (unchecked-int 0x88111111))
  (.setRangeLabel plot (@plot-type
                        {:percent "Relative Output"
                         :lumens "Lumens"
                         :raw "Lux"}))
  (.setDomainLabel plot "Minutes")
  (.setPlotPadding plot (float 30) (float 5) (float 5) (float 30))
  (doseq [label [(.getDomainTitle plot) (.getRangeTitle plot)]]
    (let [paint (.getLabelPaint label)]
      (.setTextSize paint 32)
      (.setColor paint Color/WHITE)))
  ;; Line labels on left and bottom edges
  (.setLinesPerRangeLabel plot 2)
  (.setLinesPerDomainLabel plot 2)
  (.setRangeLowerBoundary plot 0 BoundaryMode/FIXED)
  (.setDomainLowerBoundary plot 0 BoundaryMode/FIXED)
  (let [g (.getGraph plot)]
    (.setLineLabelEdges g (into-array [XYGraphWidget$Edge/BOTTOM XYGraphWidget$Edge/LEFT]))
    (let [style (.getLineLabelStyle g XYGraphWidget$Edge/BOTTOM)]
      (doto (.getPaint style)
        (.setColor Color/WHITE) (.setTextSize 24)
        (.setTextAlign Paint$Align/CENTER))
      (.setFormat style (DecimalFormat. "0.#")))
    (let [style (.getLineLabelStyle g XYGraphWidget$Edge/LEFT)]
      (doto (.getPaint style)
        (.setColor Color/WHITE) (.setTextSize 24)
        (.setTextAlign Paint$Align/RIGHT))
      (.setFormat style (DecimalFormat. "#")))
    ;; Graph margins (left, top, right, bottom) - room for line labels
    (.setMargins g (float 40) (float 80) (float 10) (float 40))
    ;(.setLineLabelInsets g (Insets. (float 0) (float 5) (float 5) (float -5)))
    )
  )

(defn activate-chart []
  (on-ui (try (let [container (find-view @root-view* ::chart)]
                (.removeAllViews container)
                (.addView container plot))
              (catch Exception e nil))))

(defn set-chart-title [title]
  (.setTitle plot title)
  (on-ui (.redraw ^XYPlot plot)))

(defn add-point [x y]
  (.addLast ^SimpleXYSeries series (double x) (double y))
  (on-ui (.redraw ^XYPlot plot)))

(defn clear-chart []
  (while (> (.size series) 0)
    (.removeLast series))
  (on-ui (.redraw ^XYPlot plot)))

(defn write-chart-png [file]
  (let [w (.getWidth plot)
        h (.getHeight plot)
        bitmap (Bitmap/createBitmap w h Bitmap$Config/ARGB_8888)
        canvas (Canvas. bitmap)
        os (-> @main-activity
               .getContentResolver
               (.openOutputStream (.getUri file)))]
    (.draw plot canvas)
    (try
      (.compress bitmap Bitmap$CompressFormat/PNG 100 os)
      (finally
        (.close os)
        (.recycle bitmap)))))

(defn dyn-val [lux]
  (cond (= :percent @plot-type) (percent lux)
        (= :lumens @plot-type) (lumens/lumens lux)
        true lux))

(defn dyn-lux [val]
  (cond (= :percent @plot-type) (inv-percent val)
        (= :lumens @plot-type) (lumens/lumens-to-lux val)
        true val))

(defn start-lux [val]
  (if (= :lumens @plot-type)
    (lumens/lumens-to-lux val)
    val))

(defn plot-point [min lux]
  (add-point min (dyn-val lux)))

(defn stop-runtime-test [& _]
  (reset! running false)
  (let [csv-writer (future (write-csv-file @output @csv-file))
        png-writer (future (write-chart-png @png-file))]
    (try @csv-writer
         @png-writer
         (catch Exception e
           (log/e (ex-data e))))
    (reset! output [])
    (reset! dir nil)
    (reset! csv-file nil)
    (reset! png-file nil)
    (stop-and-reset-pool! runtime-pool)
    (abort-threshold)))

(defn runtime-test [& _]
  (reset! dir
          (mkdir (Uri/parse (@prefs* :directory)) @output-file-name=))
  (make-chart)
  (activate-chart)
  (reset! thirty* @lux=)
  (let [start-time (. System nanoTime)]
    (swap! test-time max start-time)
    (reset! csv-file (mkfile @dir (str @output-file-name= "-" @test-time ".csv") "text/csv"))
    (reset! png-file (mkfile @dir (str @output-file-name= "-" @test-time ".png") "image/png"))
    (every 1000
           #(do (sample-lux)
                (plot-point (minutes-since @test-time) @lux=))
           runtime-pool
           :desc "Sample the light meter reading")
    (add-watch lux= :runtime-end-watch
               (fn [_k _r _o n]
                 (when (< (dyn-val n) @end-threshold)
                   (stop-runtime-test))))))

(defn reset-thirty [& _]
  (reset! thirty* @lux=)
  (clear-chart)
  (doseq [element @output]
    (apply plot-point element)))

;; (let [quick-add #(.addLast ^SimpleXYSeries series
;;                              (double (dyn-val %))
;;                              (double (dyn-val  %2)))]
;;     (doseq [element @output]
;;       (apply quick-add element)))

(defn start-runtime-test [_evt]
  (cond (empty? @output-file-name) (name-file-alert)
        (nil? (@prefs* :directory)) (dir-unset-alert)
        true (do (reset-peak)
                 (reset! running true)
                 (start-threshold
                  (start-lux @begin-threshold)
                  30
                  runtime-test
                  reset-thirty))))

(defn set-plot-type [type]
  (fn [_ c]
    (when c
      (if @running
        (reset! plot-type plot-type) ;force redraw view
        (reset! plot-type type)))))

(def runtime-layout [:scroll-view {:id ::runtime
                                   :layout-weight 1
                                   :layout-width :fill}
                     [:linear-layout {:layout-width :fill
                                      :layout-height :fill
                                      :orientation :vertical}
                      [:edit-text {:id ::filename
                                   :hint "Name output file"
                                   :text @output-file-name
                                   :on-text-change #(reset! output-file-name %)
                                   :layout-width :fill
                                   }]
                      [:linear-layout {:orientation :horizontal
                                       :layout-width :fill}
                       [:text-view {:text "Start at: "} ]
                       [:edit-text {:text (str @begin-threshold)
                                    :on-text-change #(reset! begin-threshold
                                                             (Integer/parseInt %))}]
                       [:text-view {:text "End at: "}]
                       [:edit-text {:text (str @end-threshold)
                                    :on-text-change #(reset! end-threshold
                                                             (Integer/parseInt %))}]
                       ]
                      [:radio-group {:orientation :horizontal
                                     :layout-height :fill}
                       [:radio-button {:text "Percent"
                                       :checked (cell= #(= :percent @plot-type))
                                       :on-checked-change (set-plot-type :percent)}]
                       [:radio-button {:text "Lumens"
                                       :checked (cell= #(= :lumens @plot-type))
                                       :on-checked-change (set-plot-type :lumens)}]
                       [:radio-button {:text "Raw"
                                       :checked (cell= #(= :raw @plot-type))
                                       :on-checked-change (set-plot-type :raw)}]]
                      
                      [:linear-layout {:id ::chart
                                       :layout-width :fill
                                       :layout-height [300 :dip]
                                       }
                       ]
                      [:text-view {:id ::lux-now
                                   :text (cell= #(str "Raw sensor reading: " @luxs=))}]
                      [:button {:id ::runtime-test
                                 :text (cell= #(if @running
                                                 "Stop test"
                                                 "Start test"))
                                 :on-click #(if @running
                                              (stop-runtime-test %)
                                              (start-runtime-test %))}]
                      ]
                     
                     ])




