(ns com.zakreviews.ceilingbounce.lumens
    (:require [neko.reactive :refer [cell cell=]])
    (:use com.zakreviews.ceilingbounce.theme)
    (:use com.zakreviews.ceilingbounce.common))

(defn lumens [lux]
  (try (round (/ lux (@prefs* :lux-to-lumens)))
       (catch Exception e 0)))

(defn lumens-to-lux [lumens]
  (* lumens (@prefs* :lux-to-lumens)))

(def lumens= (cell= #(lumens @lux=)))
(def peak= (cell= #(lumens @peak-lux*)))
(def thirty= (cell= #(str "30s: " (lumens @thirty*))))
(def threshold* (cell 10))

(defn measure-lumens [threshold]
  (start-threshold (lumens-to-lux threshold)
                   30
                   (fn [_lux]
                     (play-start)
                     (reset-peak)
                     (watch-peak))
                   (fn [lux]
                     (play-end)
                     (unwatch-peak)
                     (reset! thirty* lux))))

(defn measure-or-cancel [& _]
  (if @threshold-running
    (abort-threshold)
    (measure-lumens @threshold*)))

(def lumens-layout
  [:scroll-view {:id ::lumens
                 :layout-weight 1
                 :layout-width :fill}
   [:linear-layout {:layout-width :fill
                    :orientation :vertical}
    [:text-view (t :big-text
                   {:id ::lumens-now
                    :text (cell= #(str @lumens=))})]
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
                       :layout-height :fill}
     [:text-view (t :normal-text
                    {:text "Peak: "
                     :id ::peak-label})]
     [:text-view (t :normal-text
                    {:id ::lumens-peak
                     :layout-to-right-of ::peak-label
                     :text (cell= #(str @peak=))})]
     [:text-view (:t :normal-text
                     {:text " | "
                      :id ::lumens-separator
                      :layout-to-right-of ::lumens-peak})]
     [:text-view (t :normal-text
                    {:text thirty=
                     :id ::thirty-seconds                  
                     :layout-to-right-of ::lumens-separator})]]
    [:button (t :normal-text
                {:id ::reset-button
                 :text "Reset"
                 :on-click reset-peak})]]])
