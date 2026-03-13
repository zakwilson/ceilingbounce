(ns com.zakreviews.ceilingbounce.repl
  (:require 
            [neko.find-view :refer [find-view]]
            [neko.resource :refer [get-theme-color]]
            [neko.reactive :refer [cell cell=]]
            [neko.log :as log]
            [clj-android.repl.server :as repl-server]
            [com.zakreviews.ceilingbounce.common
               :as common
             :refer [main-activity ui-tree* root-view*]])
  (:import android.app.Activity
           android.view.View
           android.widget.EditText
           android.widget.TextView
           com.goodanser.clj_android.runtime.ClojureActivity))

;; ---------------------------------------------------------------------------
;; State cells (defonce — survive UI rebuilds)
;; ---------------------------------------------------------------------------

;; :status  — :unknown | :starting | :running | :stopping | :stopped | :error | :unavailable
;; :port    — integer when running, else nil
;; :error   — string when :error, else nil
(defonce state* (cell {:status :unknown :port nil :error nil}))

(add-watch state* :state-watch
           (fn [_k _r _o n]
             (log/w (str n))))

;; Theme colors for the current activity, updated in section-ui.
(defonce theme-colors* (cell nil))

;; Derived formula cells — auto-recompute when state* or theme-colors* changes.
(defonce status-text=
  (cell= #(let [{:keys [status port]} @state*]
            (case status
              :starting    "Starting..."
              :stopping    "Stopping..."
              :running     (str "Running on port " port)
              :stopped     "Stopped"
              :error       "Error"
              :unavailable "Unavailable"
              "Stopped"))))

(defonce status-color=
  (cell= #(let [{:keys [status]}      @state*
                {:keys [secondary
                        error-color]} (or @theme-colors* {})]
            (case status
              :running  0xFF00CC00
              :starting 0xFFCCCC00
              :stopping 0xFFCCCC00
              (:error :unavailable) (or error-color 0xFFFF4444)
              (or secondary 0xFF888888)))))

(defonce error-text=
  (cell= #(or (:error @state*) "")))

(defonce start-enabled=
  (cell= #(contains? #{:stopped :error} (:status @state*))))

(defonce stop-enabled=
  (cell= #(= :running (:status @state*))))

;; ---------------------------------------------------------------------------
;; Auto-start watcher (defonce — starts once at namespace load)
;; ---------------------------------------------------------------------------

(defonce ^:private _auto-start-watcher
  (future
    (cond
      ;; Server already running: autoStartNrepl may have completed before
      ;; this namespace loaded.  Reflect reality immediately.
      (repl-server/running?)
      (reset! state* {:status :running
                      :port   (or (repl-server/port) 7888)
                      :error  nil})

      ;; nREPL infrastructure not bundled (release build or runtime-repl
      ;; excluded).  Nothing will start it.
      (not (repl-server/repl-available?))
      (reset! state* {:status :unavailable :port nil :error nil})

      ;; Wait for autoStartNrepl to bring the server up.
      :else
      (do
        (reset! state* {:status :starting :port nil :error nil})
        (if (repl-server/wait-for-ready :timeout-ms 60000)
          (reset! state* {:status :running
                          :port   (or (repl-server/port) 7888)
                          :error  nil})
          (when-not (repl-server/running?)
            (reset! state* {:status :stopped :port nil :error nil})))))))

;; ---------------------------------------------------------------------------
;; Button handlers
;; ---------------------------------------------------------------------------

(defn- parse-port [^EditText et]
  (try
    (let [p (Integer/parseInt (.. et getText toString trim))]
      (when (<= 1 p 65535) p))
    (catch NumberFormatException _ nil)))

(defn on-start-nrepl [_view]
  (when-let [root @root-view*]
    (let [port (some-> (find-view root ::nrepl-port-input) parse-port)]
      (if-not port
        (swap! state* assoc :error "Invalid port (1\u201365535)")
        (do
          (reset! state* {:status :starting :port nil :error nil})
          (.start
            (Thread.
              (.getThreadGroup (Thread/currentThread))
              (fn []
                (try
                  (repl-server/start port)
                  (reset! state* {:status :running
                                  :port   (or (repl-server/port) port)
                                  :error  nil})
                  (catch Throwable t
                    (reset! state* {:status :error
                                    :port   nil
                                    :error  (.getMessage t)}))))
              "nrepl-start"
              1048576)))))))

(defn- on-stop-nrepl [_view]
  (reset! state* {:status :stopping :port nil :error nil})
  (future
    (try
      (repl-server/stop)
      (reset! state* {:status :stopped :port nil :error nil})
      (catch Throwable t
        (reset! state* {:status :error
                        :port   nil
                        :error  (.getMessage t)})))))

;; ---------------------------------------------------------------------------
;; Section UI
;; ---------------------------------------------------------------------------

(defn section-ui
  "Returns the nREPL controls section UI tree.
  Also updates theme-colors* so derived cells use current theme."
  [ctx section-id]
  (reset! theme-colors*
          {:secondary   (get-theme-color ctx :text-color-secondary)
           :error-color (get-theme-color ctx :color-error)})
  (let [subtitle-color (get-theme-color ctx :text-color-secondary)]
    [:scroll-view {:id section-id
                   :layout-width :fill
                   :layout-height :fill
                   :visibility :gone}
     [:linear-layout {:orientation :vertical
                      :padding [16 16 16 16]
                      :layout-width :match-parent}
      [:text-view {:text "nREPL Server"
                   :text-size [22 :sp]
                   :padding [0 0 0 8]}]
      [:text-view {:text "Connect from your editor to live-reload code."
                   :text-size [14 :sp]
                   :text-color subtitle-color
                   :padding [0 0 0 16]}]
      [:linear-layout {:orientation :horizontal}
       [:text-view {:text "Port: "
                    :text-size [16 :sp]
                    :padding [0 8 8 0]}]
       [:edit-text {:id ::nrepl-port-input
                    :text "7888"
                    :input-type :number}]]
      [:text-view {:id ::nrepl-status
                   :text status-text=
                   :text-size [16 :sp]
                   :text-color status-color=
                   :padding [0 4 0 4]}]
      [:linear-layout {:orientation :horizontal
                       :padding [0 4 0 4]}
       [:button {:id ::nrepl-start-btn
                 :text "Start"
                 :enabled start-enabled=
                 :on-click on-start-nrepl}]
       [:button {:id ::nrepl-stop-btn
                 :text "Stop"
                 :enabled stop-enabled=
                 :on-click on-stop-nrepl}]]
      [:text-view {:id ::nrepl-error
                   :text error-text=
                   :text-size [14 :sp]
                   :text-color status-color=
                   :padding [0 4 0 0]}]]]))
