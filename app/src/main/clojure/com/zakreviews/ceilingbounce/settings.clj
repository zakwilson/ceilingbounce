(ns com.zakreviews.ceilingbounce.settings
  (:require [neko.reactive :refer [cell cell=]])
  (:use com.zakreviews.ceilingbounce.common))

(def settings-layout
  [:scroll-view {:id ::settings
                 :layout-width :fill
                 :layout-weight 1}
   [:linear-layout {:layout-width :fill
                    :orientation :vertical}
    [:linear-layout {:layout-width :fill
                     :layout-height :wrap
                     :orientation :horizontal}
     [:text-view {:id ::conversion-label
                  :text-color 0xFFFFFFFF
                  :text "Lux per lumen: "}]
     [:edit-text {:id ::conversion
                  :text (cell= #(str (@prefs* :lux-to-lumens)))
                  :on-text-change #(swap! prefs* assoc :lux-to-lumens
                                          (parse-float %))
                  :input-type :number
                  :layout-width :fill
                  :layout-weight 1
                  }]]
    [:linear-layout {:layout-width :fill
                     :layout-height :wrap
                     :orientation :horizontal}
     [:text-view {:id ::conversion-label
                  :text "Calculated distance: "}]
     [:edit-text {:id ::conversion
                  :text (cell= #(str (@prefs* :effective-distance)))
                  :on-text-change #(swap! prefs* assoc :effective-distance
                                          (parse-float %))
                  :input-type :number
                  :layout-weight 1
                  }]]
    [:check-box {:text "Use sound?"
                 :checked (cell= #(@prefs* :use-sound))
                 :on-checked-change (fn [_ checked]
                                      (swap! prefs* assoc :use-sound checked))}]]])
