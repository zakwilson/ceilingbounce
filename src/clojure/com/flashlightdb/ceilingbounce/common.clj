(ns com.flashlightdb.ceilingbounce.common
  (:require [clojure.core.async
             :as a
             :refer [>! <! >!! <!! go chan buffer close! thread
                     alts! alts!! timeout]]))


(comment defmacro update-ui [layout tag & body] ; FIXME - do we need this?
  `(on-ui (-> layout
              .getTag
              ~tag
              ~@body)))

(defn do-nothing [])

(def storage-dir "/storage/emulated/0/ceilingbounce/")
(def config-path (str storage-dir "config.edn"))

(def main-ui-chan (chan 10))

(def main-activity (atom nil))

(defn main-tag [x]
  (keyword (str "com.flashlightdb.ceilingbounce.main/" x)))

(def lux (atom 0))

(def linear-layout-opts
  {:orientation :vertical
                   :layout-width :fill
                   :layout-height :wrap})

(def config (atom {}))

(defn read-config
  ([] (read-config config-path))
  ([path] (swap! config (fn [_ replacement] replacement)
                 (read-string (slurp path)))))

(defn write-config
  ([conf] (write-config config-path conf))
  ([path conf]
   (spit path conf :append true)))

(add-watch config :config-write-watch
           (fn [ _key _ref _old new]
             (write-config new)))
