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
           android.app.Activity))

(defn do-nothing [])

(defn play-notification []
  (let [tg (ToneGenerator. AudioManager/STREAM_MUSIC 10)]
    (.startTone tg ToneGenerator/TONE_CDMA_LOW_L 150)
    (Thread/sleep 200)
    (.startTone tg ToneGenerator/TONE_CDMA_HIGH_L 150)))

(prefs/defpreferences prefs* "prefs")
(when (empty? @prefs*)
  (reset! prefs* {:lux-to-lumens 1 :effective-distance 1}))

;; FIXME remove
(def storage-dir "/storage/emulated/0/ceilingbounce/")

(def main-activity (atom nil))

(defn main-tag [x]
  (keyword (str "com.zakreviews.ceilingbounce.main/" x)))

(defn identity* [_ replacement]
  replacement) 

(def root-view* (atom nil))

(def ui-tree* (atom []))

(def lux-sensor* (cell [0]))
(def lux= (cell= #(first @lux-sensor*)))
(def luxs= (cell= #(str @lux=)))
(def peak-lux* (cell 0))

(defn ensure-sensor [ctx]
  (def lux-sensor* (sensor/sensor-cell ctx :light)))

(def linear-layout-opts
  {:orientation :vertical
   :layout-width :fill
   :layout-height :wrap})

(defn update-ui [activity identifier & updates]
  (on-ui                     ; FIXME - fix, rather than silence errors
   (try
     (apply (partial ui/config (find-view activity identifier))
            (flatten (into [] updates)))
     (catch Exception e nil))))

(defn update-main [identifier & updates]
  (apply (partial update-ui @main-activity identifier) updates))

(defn read-field [^Activity activity identifier]
  (.toString (.getText (find-view activity identifier))))

(def at-pool (mk-pool))

(defn average [a-seq]
  (/ (apply + a-seq) (count a-seq)))



;; dead?

(def thirty-second-task (atom (into (ring-buffer 1) [nil])))

(def light-on-lux (* 10 (@prefs :lux-to-lumens)))

(def last-20 (atom (ring-buffer 20)))

(defn reset-peak []
  (swap! peak-lux* min 0))

(defn set-30s [func]
  (swap! thirty-second-task conj func))

(defn make-30s [core-task]
  (fn []
    (let [func (peek @thirty-second-task)]
      (when func
        (func)
        (set-30s nil) ; debounce
        (after (* 15 1000)
               (fn []
                 (set-30s core-task))
               at-pool :desc "Restore 30s task")))))

(defn handle-lux [lux]
  (swap! peak-lux* max lux)
  (swap! last-20 conj lux)
  (when (and (> lux (* 2 (average @last-20)))
                    (> lux (* 1.5 (last (butlast @last-20))))
                    (> lux (max 10 (/ @peak-lux 2))))
    (let [core-task (peek @thirty-second-task)]
      (after (* 30 1000) (make-30s core-task) at-pool :desc "Run 30s task"))))

(add-watch lux= :peak-watch
           (fn [_key _ref _old new]
             (swap! peak-lux* max new)))
