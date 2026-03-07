(ns com.zakreviews.ceilingbounce.main-activity
    (:require [neko.activity :refer [defactivity set-content-view!]]
              [neko.notify :as notify]
              [neko.resource :as res]
              [neko.find-view :refer [find-view]]
              [neko.threading :refer [on-ui]]
              [neko.log :as log]
              [neko.ui :as ui]
              [neko.ui.support.material]
              [neko.reactive :refer [cell cell=]]
              neko.tools.repl
              [neko.ui.support.material]
              [clojure.java.io :as io]
              [clojure.core.async
               :as a
               :refer [>! <! >!! <!! go chan buffer close! thread
                       alts! alts!! timeout]]
              [com.zakreviews.ceilingbounce.runtime :as runtime]
              [com.zakreviews.ceilingbounce.lumens :as lumens]
              [com.zakreviews.ceilingbounce.throw :as throw]
              [com.zakreviews.ceilingbounce.repl :as repl]
              [com.zakreviews.ceilingbounce.common
               :as common
               :refer [identity* config main-activity do-nothing ui-tree* root-view*]])
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


(defn- setup-tabs!
  "Adds tab items to the TabLayout after the UI tree is built."
  [root]
  (when-let [^TabLayout tabs (find-view root ::tabs)]
    (let [t1 (doto (.newTab tabs) (.setText "Lumens"))
          t2 (doto (.newTab tabs) (.setText "Throw"))
          t3 (doto (.newTab tabs) (.setText "Runtime"))
          t4 (doto (.newTab tabs) (.setText "Repl"))]
      (on-ui (doseq [t [t1 t2 t3 t4]]
               (.addTab tabs t))))))

(defn make-ui
  "Builds the sample UI tree using neko's declarative DSL.
  Called by ClojureActivity.reloadUi() and by on-create.
  Reads the UI tree from *ui-tree if set, otherwise uses the default."
  [^Activity activity]
  (reset! main-activity activity)
  (let [root (ui/make-ui activity @ui-tree*)]
    (reset! root-view* root)
    (setup-tabs! root)
    root))

(defn on-create [^Activity activity saved-state]
  (reset! main-activity activity)

  (def sm (cast SensorManager (.getSystemService ^Activity activity "sensor")))
  (def light-sensor (.getDefaultSensor ^SensorManager sm
                                       (Sensor/TYPE_LIGHT)))
  (def sensor-listener
    (reify SensorEventListener
      (onSensorChanged [activity evt]
        (let [lux (first (.values evt))]
          (reset! common/lux lux)))
      (onAccuracyChanged [activity s a]
        (do-nothing))))

  (neko.debug/keep-screen-on activity)
  (runtime/setup-chart)

  (let [view (make-ui activity)]
    (.setFitsSystemWindows view true)
    (.setContentView activity view)
    (repl/on-start-nrepl view)))

(defn on-resume [^Activity activity]
  (.registerListener ^SensorManager sm
                     sensor-listener
                     light-sensor
                     200000))

(defn on-pause [^Activity activity]
  (.unregisterListener ^SensorManager sm sensor-listener)
  (.superOnPause activity))

(defn reload-ui!
  "Hot-reload the UI from the REPL. Uses ClojureActivity's
  built-in reloadUi mechanism."
  []
  (when-let [a @main-activity]
    (.reloadUi a)))

(defn tab-handler [tab]
  (when-let [root @root-view*]
    (let [^View lumens (find-view root ::lumens/lumens)
          ^View throw (find-view root ::throw/throw)
          ^View runtime (find-view root ::runtime/runtime)
          ^View repl (find-view root ::repl/repl)
          target-idx (.getPosition tab)]
      (if (every? identity [lumens throw runtime repl])
        (doseq [[idx view] (map-indexed vector [lumens throw runtime repl])]
          (on-ui
           (if (= idx target-idx)
             (.setVisibility view View/VISIBLE)
             (.setVisibility view View/GONE))))))))

(add-watch ui-tree* :ui-reload-watch
           (fn [_key _ref _old _new]
             (reload-ui!)))

(reset! ui-tree*
        [:linear-layout {:id-holder true
                         :orientation :vertical}
         [:tab-layout {:id ::tabs
                       :tab-mode :fixed
                       :tab-gravity :fill
                       :layout-width :fill
                       :on-tab-selected tab-handler}]
         lumens/lumens-layout
         throw/throw-layout
         runtime/runtime-layout
         repl/repl-layout
         ])
