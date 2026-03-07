(ns com.zakreviews.ceilingbounce.repl
  (:require [neko.ui :as ui]
            [neko.find-view :refer [find-view]]
            [neko.log :as log]
            [neko.reactive :refer [cell cell=]]
            [neko.ui.support.material]
            [clj-android.repl.server :as repl-server]
            [com.zakreviews.ceilingbounce.common
               :as common
             :refer [identity* config main-activity do-nothing ui-tree* root-view*]])
  (:import android.app.Activity
           android.view.View
           android.widget.EditText
           android.widget.TextView
           com.goodanser.clj_android.runtime.ClojureActivity))

;; --- nREPL controls ---
;; These update values in the UI the old fashioned way

(defn- run-on-ui! [f]
  (when-let [^Activity activity @main-activity]
    (.runOnUiThread activity f)))

(defn- nrepl-set-status! [text color]
  (run-on-ui!
    (fn []
      (when-let [^TextView v (find-view @root-view* ::nrepl-status)]
        (.setText v (str text))
        (.setTextColor v (unchecked-int color))))))

(defn- nrepl-set-error! [text]
  (run-on-ui!
    (fn []
      (when-let [^TextView v (find-view @root-view* ::nrepl-error)]
        (.setText v (str text))))))

(defn- nrepl-set-buttons! [start-enabled? stop-enabled?]
  (run-on-ui!
    (fn []
      (some-> (find-view @root-view* ::nrepl-start-btn)
              (.setEnabled (boolean start-enabled?)))
      (some-> (find-view @root-view* ::nrepl-stop-btn)
              (.setEnabled (boolean stop-enabled?))))))

(defn- parse-port [^EditText et]
  (try
    (let [p (Integer/parseInt (.. et getText toString trim))]
      (when (<= 1 p 65535) p))
    (catch NumberFormatException _ nil)))

(defn on-start-nrepl [_view]
  (when-let [root @root-view*]
    (let [port (some-> (find-view root ::nrepl-port-input)
                       (parse-port))]
      (if-not port
        (nrepl-set-error! "Invalid port (1\u201365535)")
        (do
          (nrepl-set-error! "")
          (nrepl-set-status! "Starting..." 0xFFCCCC00)
          (nrepl-set-buttons! false false)
          (.start
            (Thread.
              (.getThreadGroup (Thread/currentThread))
              (fn []
                (try
                  (if (repl-server/running?)
                    (nrepl-set-status! (str "Running on port "
                                            (or (repl-server/port) port))
                                       0xFF00CC00)
                    (repl-server/start port))
                  (nrepl-set-status! (str "Running on port "
                                          (or (repl-server/port) port))
                                     0xFF00CC00)
                  (nrepl-set-buttons! false true)
                  (catch Throwable t
                    (nrepl-set-status! "Error" 0xFFFF0000)
                    (nrepl-set-error! (.getMessage t))
                    (nrepl-set-buttons! true false))))
              "nrepl-start"
              1048576)))))))

(defn- on-stop-nrepl [_view]
  (nrepl-set-status! "Stopping..." 0xFFCCCC00)
  (nrepl-set-buttons! false false)
  (.start
    (Thread.
      (fn []
        (try
          (repl-server/stop)
          (nrepl-set-status! "Stopped" 0xFFAAAAAA)
          (nrepl-set-error! "")
          (nrepl-set-buttons! true false)
          (catch Throwable t
            (nrepl-set-status! "Error" 0xFFFF0000)
            (nrepl-set-error! (.getMessage t))
            (nrepl-set-buttons! false true))))
      "nrepl-stop")))

(defn- sync-nrepl-status!
  "Waits for nREPL auto-start completion and updates the UI.
  Shows 'Starting...' while loading, then 'Running' once ready."
  []
  (.start
    (Thread.
      (fn []
        ;; Wait for UI to be ready before showing status
        (loop [waited 0]
          (when (and (nil? @root-view*) (< waited 10000))
            (Thread/sleep 500)
            (recur (+ waited 500))))
        (when @root-view*
          (nrepl-set-status! "Starting..." 0xFFCCCC00)
          (when-not (repl-server/repl-available?)
            (nrepl-set-status! "Unavailable" 0xFFFF0000))
          (nrepl-set-buttons! false false)
          (if (repl-server/wait-for-ready :timeout-ms 60000)
            (let [p (or (repl-server/port) 7888)]
              (nrepl-set-status! (str "Running on port " p) 0xFF00CC00)
              (nrepl-set-buttons! false true))
            ;; Timeout — server didn't start
            (when-not (repl-server/running?)
              (nrepl-set-status! "Stopped" 0xFFAAAAAA)
              (nrepl-set-buttons! true false)))))
      "nrepl-status-sync")))

;; Detect nREPL auto-started by ClojureApp and sync the UI status.

(add-watch ui-tree* :nrepl-status-watch
           (fn [_key _ref _old _new]
             (sync-nrepl-status!)))

(def repl-layout 
  [:scroll-view {:id ::repl
                 :layout-width :fill
                 :layout-height 0
                 :layout-weight 1
                 :visibility :gone}
   [:linear-layout {:orientation :vertical
                    :padding [32 32 32 32]
                    :layout-width :match-parent}
    [:text-view {:text "nREPL Server"
                 :text-size [24 :sp]
                 :padding [0 0 0 8]}]
    [:text-view {:text "Connect from your editor to live-reload code."
                 :text-size [14 :sp]
                 :text-color (unchecked-int 0xFF888888)
                 :padding [0 0 0 16]}]
    [:linear-layout {:orientation :horizontal}
     [:text-view {:text "Port: "
                  :text-size [16 :sp]
                  :padding [0 8 8 0]}]
     [:edit-text {:id ::nrepl-port-input
                  :text "7888"
                  :input-type :number}]]
    [:text-view {:id ::nrepl-status
                 :text "Stopped"
                 :text-size [16 :sp]
                 :text-color (unchecked-int 0xFFAAAAAA)
                 :padding [0 4 0 4]}]
    [:linear-layout {:orientation :horizontal
                     :padding [0 4 0 4]}
     [:button {:id ::nrepl-start-btn
               :text "Start"
               :on-click on-start-nrepl}]
     [:button {:id ::nrepl-stop-btn
               :text "Stop"
               :enabled false
               :on-click on-stop-nrepl}]]
    [:text-view {:id ::nrepl-error
                 :text ""
                 :text-size [14 :sp]
                 :text-color (unchecked-int 0xFFFF0000)
                 :padding [0 4 0 0]}]]])
