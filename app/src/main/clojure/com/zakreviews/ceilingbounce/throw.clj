(ns com.zakreviews.ceilingbounce.throw
    (:require 
              [neko.reactive :refer [cell cell=]]
              [amalloy.ring-buffer :refer [ring-buffer]]
              )
    (:use overtone.at-at
          com.zakreviews.ceilingbounce.theme
          com.zakreviews.ceilingbounce.common))

(defn lux-to-cd [lux]
  (round
   (* lux (Math/pow (@prefs* :effective-distance) 2))))

(defn cd-to-lux [cd]
  (try (round (/ (Math/sqrt cd) (@prefs* :effective-distance)))
       (catch Exception e 0)))

(defn cd-to-m [cd]
  (Math/round (Math/sqrt (* 4 cd))))

(def peak-cd= (cell= #(lux-to-cd @peak-lux*)))
(def thirty-cd= (cell= #(lux-to-cd @thirty*)))
(def cd= (cell= #(lux-to-cd @lux=)))
(def m= (cell= #(cd-to-m @cd=)))
(def threshold* (cell 100))

(defn measure-throw [threshold]
  (start-threshold (cd-to-lux threshold)
                   30
                   (fn [_lux]
                     (play-start)
                     (reset-peak)
                     (watch-peak))
                   (fn [lux]
                     (play-mid)
                     (unwatch-peak)
                     (add-watch lux= :throw-watch
                                (fn [_k _r _o n]
                                  (swap! thirty* max n)))
                     (after 10000
                            (fn []
                              (remove-watch lux= :throw-watch)
                              (play-end))
                            at-pool))))

(defn measure-or-cancel [& _]
  (if @threshold-running
    (abort-threshold)
    (measure-throw @threshold*)))

(def throw-layout
  [:scroll-view {:id ::throw
                 :layout-weight 1
                 :layout-width :fill}
   [:linear-layout {:layout-width :fill
                    :orientation :vertical}
    [:text-view (t :big-text
                   {:id ::candela-now
                    :text (cell= #(str @cd=))})]
    [:linear-layout {:layout-width :fill
                     :layout-height :fill
                     :orientation :horizontal}
     [:text-view (t :normal-text
                    {:text "Start when reading reaches: "})]
     [:edit-text {:input-type :number
                  :text (cell= #(str @threshold*))
                  :min-width [96 :dip]
                  :on-text-change #(reset! threshold* (parse-int %))}]]
    [:button (t :normal-text
                {:text (cell= #(if @threshold-running
                                 "Cancel" "Measure"))
                 :on-click measure-or-cancel})]
    [:linear-layout {:min-height [96 :dip]}]
    [:relative-layout {:layout-width :fill
                       :layout-height :wrap}
     [:text-view {:text (cell= #(str "Peak cd: " @peak-cd=))
                  :id ::candela-peak}]
     [:text-view {:text " | "
                  :id ::candela-separator
                  :layout-to-right-of ::candela-peak}]
     [:text-view {:text (cell= #(str "30s cd: " @thirty-cd=))
                  :id ::candela-thirty-seconds
                  :layout-to-right-of ::candela-separator}]]
    [:relative-layout {:layout-width :fill
                       :layout-height :wrap
                       :layout-gravity 1}
     [:text-view {:text (cell= #(str "Peak m: " (cd-to-m @peak-cd=)))
                  :id ::throw-peak}]
     [:text-view {:text " | "
                  :id ::throw-separator
                  :layout-to-right-of ::throw-peak}]
     [:text-view {:text (cell= #(str "30s m: " (cd-to-m @thirty-cd=)))
                  :id ::throw-thirty
                  :layout-to-right-of ::throw-separator}]]
    [:button {:id ::reset-button
              :text "Reset"
              :on-click #'reset-peak}]]
   ])
