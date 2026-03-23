(ns com.zakreviews.ceilingbounce.common
  (:require [clojure.core.async
             :as a
             :refer [>! <! >!! <!! go chan buffer close! thread
                     alts! alts!! timeout]]
            [neko.ui :as ui]
            [neko.threading :refer [on-ui]]
            [neko.find-view :refer [find-view]]
            [amalloy.ring-buffer :refer [ring-buffer]]
            [clojure.java.io :as io]
            [neko.sensor :as sensor]
            [neko.reactive :refer [cell cell=]]
            [neko.data.shared-prefs :as prefs])
  (:use overtone.at-at)
  (:import [android.media AudioManager ToneGenerator]
           android.content.Context
           android.app.Activity))

(def main-activity (atom nil))

(def root-view* (atom nil))

(def ui-tree* (atom [:linear-layout {}
                     [:text-view {:text "Loading..."}]]))

(defn do-nothing [& args])

(prefs/defpreferences prefs* "prefs")
(when (empty? @prefs*)
  (swap! prefs* #(merge {:lux-to-lumens 1 :effective-distance 1 :use-sound true} %)))

(defn parse-int [i]
  (try (Integer/parseInt i)
       (catch Exception e 0)))

(defn parse-float [i]
  (try (Float/parseFloat i)
       (catch Exception e 0.0)))

(defn get-volume []
  (if (@prefs* :use-sound)
    (let [am (.getSystemService @main-activity Context/AUDIO_SERVICE)]
      (.getStreamVolume am AudioManager/STREAM_MUSIC))
    0))

(defn play-start []
  (let [tg (ToneGenerator. AudioManager/STREAM_MUSIC (get-volume))]
    (.startTone tg ToneGenerator/TONE_CDMA_LOW_L 150)
    (Thread/sleep 250)
    (.startTone tg ToneGenerator/TONE_CDMA_HIGH_L 150)))

(defn play-mid []
  (let [tg (ToneGenerator. AudioManager/STREAM_MUSIC (get-volume))]
    (.startTone tg ToneGenerator/TONE_CDMA_MED_L 150)
    (Thread/sleep 250)
    (.startTone tg ToneGenerator/TONE_CDMA_MED_L 150)
    (Thread/sleep 250)
    (.startTone tg ToneGenerator/TONE_CDMA_MED_L 150)))

(defn play-end []
  (let [tg (ToneGenerator. AudioManager/STREAM_MUSIC (get-volume))]
    (.startTone tg ToneGenerator/TONE_CDMA_HIGH_L 150)
    (Thread/sleep 250)
    (.startTone tg ToneGenerator/TONE_CDMA_LOW_L 150)))

(def play-notification play-mid)



;; FIXME remove
(def storage-dir "/storage/emulated/0/ceilingbounce/")


(defn round [^Float n]
  (Math/round n))

(def lux-sensor* (cell (cell [0])))
(def lux= (cell= #(or (first @@lux-sensor*) 0))) ; guard against null during init
(def luxs= (cell= #(str (round @lux=))))
(def peak-lux* (cell 0))
(def thirty* (cell 0))

(defn activate-sensor [ctx]
  (reset! lux-sensor* (sensor/sensor-cell ctx :light)))

(def linear-layout-opts
  {:orientation :vertical
   :layout-width :fill
   :layout-height :wrap})

(def at-pool (mk-pool))

(defn average [a-seq]
  (/ (apply + a-seq) (count a-seq)))

(defn watch-peak []
  (add-watch lux= :peak-watch
             (fn [_key _ref _old new]
               (swap! peak-lux* max new))))

(defn unwatch-peak []
  (remove-watch lux= :peak-watch))

(defn reset-peak [& _]
  (reset! peak-lux* 0)
  (reset! thirty* 0))

(def threshold-pool (mk-pool))
(def threshold-watchers (cell #{}))
(def threshold-running (cell false))

(defn start-threshold [threshold delay start-callback end-callback]
  (let [watch-name (keyword (gensym))]
    (swap! threshold-watchers conj watch-name)
    (reset! threshold-running true)
    (add-watch lux= watch-name
               (fn [_key _ref _old new]
                 (when (>= new threshold)
                   (start-callback new)
                   (remove-watch lux= watch-name)
                   (swap! threshold-watchers disj watch-name)
                   (after (* delay 1000)
                          #(do (when @threshold-running
                                 (end-callback @lux=))
                               (reset! threshold-running false))
                          threshold-pool))))))

(defn abort-threshold []
  (reset! threshold-running false)
  (doseq [watch-name @threshold-watchers]
    (remove-watch lux= watch-name))
  (reset! threshold-watchers #{}))
