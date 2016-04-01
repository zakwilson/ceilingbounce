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

(def main-ui-chan (chan 10))

(def main-activity (atom nil))

(defn main-tag [x]
  (keyword (str "com.flashlightdb.ceilingbounce.main/" x)))
