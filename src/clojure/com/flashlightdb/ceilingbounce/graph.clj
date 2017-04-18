(ns com.flashlightdb.ceilingbounce.graph
  (:require [neko.threading :refer [on-ui]]
            [com.flashlightdb.ceilingbounce.common :refer [main-activity
                                                           identity*]])
  (:import [android.view ViewGroup$LayoutParams]
           [org.achartengine.chart PointStyle]
           [org.achartengine.model XYSeries]
           [org.achartengine.model XYMultipleSeriesDataset]
           [org.achartengine.renderer
            XYMultipleSeriesRenderer
            XYSeriesRenderer]
           [org.achartengine ChartFactory]))

(defrecord LineGraph
    [series
     series-renderer
     multi-renderer
     dataset
     view])

(defn method-name [mth]
  (->> mth
       name
       (str ".")
       symbol))

; This needs to be a fn, not a macro and it needs to use eval so that method
; gets evaluated at call time. It needs to generate a function and call it
; so that the compiler doesn't have to be able to represent obj.
(defn call-method [obj method & args]
  (binding [*ns* (the-ns 'com.flashlightdb.ceilingbounce.graph)]
    (let [mth (method-name method)
          mfn (eval `(fn [the-object#]
                       (~mth the-object# ~@args)))]
      (mfn obj))))

(defn call-setter [obj setter & args]
  (apply (partial call-method
                  obj
                  (str "set" (name setter)))
         args))

(defn map-setters [obj props]
  (doseq [p props]
    (call-setter obj (key p) (val p)))
  obj)

(defn make-series [title]
  (XYSeries. title))

(defn make-series-renderer [props]
  (doto (XYSeriesRenderer.)
    (map-setters props)))

(defn make-multi-renderer [props & renderers]
  (let [mrenderer (doto (XYMultipleSeriesRenderer.)
                    (map-setters props))]
    (doseq [r renderers]
      (.addSeriesRenderer mrenderer r))
    mrenderer))

(defn make-dataset [& series]
  (let [d (XYMultipleSeriesDataset.)]
    (doseq [s series]
      (.addSeries d s))
    d))

(defn make-view [activity dataset renderer]
  (let [ret (promise)]
    (on-ui
     (deliver ret
              (doto (ChartFactory/getLineChartView activity
                                                   dataset
                                                   renderer)
                (.setLayoutParams (ViewGroup$LayoutParams.
                                   ViewGroup$LayoutParams/FILL_PARENT
                                   ViewGroup$LayoutParams/FILL_PARENT)))))
    @ret))

(defn add-point [graph x y]
  (-> (:series graph)
      (.add x y))
  (.repaint (:view graph)))

(defn clear-chart [graph]
  (.clear (:series graph))
  (.repaint (:view graph)))

