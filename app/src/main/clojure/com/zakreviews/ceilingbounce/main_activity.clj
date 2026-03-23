(ns com.zakreviews.ceilingbounce.main-activity
    (:require [neko.activity :refer [defactivity set-content-view!]]
              [neko.notify :as notify]
              [neko.resource :as res]
              [neko.find-view :refer [find-view]]
              [neko.threading :refer [on-ui]]
              [neko.log :as log]
              [neko.ui :as ui]
              [neko.ui.support.material]
              [neko.ui.support.window-insets :as wini]
              [neko.reactive :refer [cell cell=]]
              neko.tools.repl
              [neko.ui.support.material]
              [clj-android.repl.server :refer [repl-available?]]
              [clojure.java.io :as io]
              [com.zakreviews.ceilingbounce.runtime :as runtime]
              [com.zakreviews.ceilingbounce.lumens :as lumens]
              [com.zakreviews.ceilingbounce.throw :as throw]
              [com.zakreviews.ceilingbounce.repl :as repl]
              [com.zakreviews.ceilingbounce.settings :as settings]
              [com.zakreviews.ceilingbounce.common
               :as common
               :refer [main-activity do-nothing ui-tree* root-view*]])
    (:use com.zakreviews.ceilingbounce.theme)
    (:import android.widget.EditText
             [android.hardware
              SensorManager
              SensorEventListener
              Sensor
              SensorEvent
              SensorListener]
             [android.app
              Activity]
             [android.widget
              EditText TextView]
             android.view.View
             [com.google.android.material.tabs TabLayout TabLayout$Tab]
             com.goodanser.clj_android.runtime.ClojureActivity
             neko.App))

;; We execute this function to import all subclasses of R class. This gives us
;; access to all application resources.
(res/import-all)

(defonce ^:private building? (volatile! false))

(declare reload-ui!)

(defn rebuild-ui-tree! []
  (vreset! building? true)
  (reset! ui-tree*
          [:linear-layout {:id-holder true
                           :orientation :vertical
                           :insets-padding :top}
           [:tab-layout {:id ::tabs
                         :tab-mode :scrollable
                         :tab-gravity :fill
                         :layout-width :fill
                         :tab-content (if (repl-available?)
                                        ["Lumens" ::lumens/lumens
                                         "Throw" ::throw/throw
                                         "Runtime" ::runtime/runtime
                                         "Settings" ::settings/settings
                                         "REPL" ::repl/repl
                                         ]
                                        ["Lumens" ::lumens/lumens
                                         "Throw" ::throw/throw
                                         "Runtime" ::runtime/runtime
                                         "Settings" ::settings/settings])}]
           lumens/lumens-layout
           throw/throw-layout
           runtime/runtime-layout
           settings/settings-layout
           (when (repl-available?)
             (repl/section-ui @main-activity ::repl/repl))
           ])
  (vreset! building? false)
  (reload-ui!))

(defn make-ui
  [^Activity activity]
  (let [root (ui/make-ui activity @ui-tree*)]
    (reset! root-view* root)
    root))

(defn on-create [^Activity activity saved-state]
  (reset! main-activity activity)
  (common/activate-sensor activity)
  (wini/enable-edge-to-edge! activity)
  (reset! main-activity activity)
  (neko.debug/keep-screen-on activity)
  (rebuild-ui-tree!))

(defn reload-ui!
  "Hot-reload the UI from the REPL. Uses ClojureActivity's
  built-in reloadUi mechanism."
  []
  (when-let [a @main-activity]
    (.reloadUi a)))

(add-watch ui-tree* :ui-reload-watch
           (fn [_key _ref _old _new]
             (when-not @building?
               (reload-ui!))))
