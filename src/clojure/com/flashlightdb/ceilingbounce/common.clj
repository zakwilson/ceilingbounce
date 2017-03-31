(ns com.flashlightdb.ceilingbounce.common
  (:require [clojure.core.async
             :as a
             :refer [>! <! >!! <!! go chan buffer close! thread
                     alts! alts!! timeout]]
            [neko.ui :as ui]
            [neko.threading :refer [on-ui]]
            [neko.find-view :refer [find-view]]
            [amalloy.ring-buffer :refer [ring-buffer]]
            [clojure.java.io :as io])
  (:use overtone.at-at)
  (:import android.media.RingtoneManager))

(defn do-nothing [])

(defn play-notification []
  (let [ringtone-uri (RingtoneManager/getDefaultUri RingtoneManager/TYPE_NOTIFICATION)
        ringtone (RingtoneManager/getRingtone neko.App/instance ringtone-uri)]
    (.play ringtone)))

(def storage-dir "/storage/emulated/0/ceilingbounce/")
(def config-path (str storage-dir "config.edn"))

(def main-ui-chan (chan 10))

(def main-activity (atom nil))

(defn main-tag [x]
  (keyword (str "com.flashlightdb.ceilingbounce.main/" x)))

(defn identity* [_ replacement]
  replacement)


(def lux (atom 0))

(def linear-layout-opts
  {:orientation :vertical
   :layout-width :fill
   :layout-height :wrap})

(def default-config {:lux-to-lumens 1 :effective-distance 1})

(def config (atom default-config))

(defn read-config
  ([] (read-config config-path))
  ([path]
   (when (.exists (io/as-file path))
       (swap! config merge
              (read-string (slurp path))))))

(defn write-config
  ([conf] (write-config config-path conf))
  ([path conf]
   (spit path conf)))

(add-watch config :config-write-watch
           (fn [ _key _ref _old new]
             (write-config new)))

(defn update-ui [activity identifier & updates]
  (on-ui ; FIXME - fix, rather than silence errors
   (try
     (apply (partial ui/config (find-view activity identifier))
            (flatten (into [] updates)))
     (catch Exception e nil))))

(defn update-main [identifier & updates]
  (apply (partial update-ui @main-activity identifier) updates))

(defn read-field [activity identifier]
  (.toString (.getText (find-view activity identifier))))

(def at-pool (mk-pool))

(defn average [a-seq]
  (/ (apply + a-seq) (count a-seq)))

(def thirty-second-task (atom (into (ring-buffer 1) [nil])))

(def light-on-lux (* 10 (@config :lux-to-lumens)))

(def last-20 (atom (ring-buffer 20)))

(def peak-lux (atom 0))

(defn reset-peak []
  (swap! peak-lux min 0))

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
  (swap! peak-lux max lux)
  (swap! last-20 conj lux)
  (when (and (> lux (* 2 (average @last-20)))
                    (> lux (* 1.5 (last (butlast @last-20))))
                    (> lux (max 10 (/ @peak-lux 2))))
    (let [core-task (peek @thirty-second-task)]
      (after (* 30 1000) (make-30s core-task) at-pool :desc "Run 30s task"))))

(add-watch lux :thirty-second-watch
           (fn [_key _ref _old new]
             (handle-lux new)))
