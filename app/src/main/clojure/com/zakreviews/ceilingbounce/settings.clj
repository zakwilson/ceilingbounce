(ns com.zakreviews.ceilingbounce.settings
  (:require [neko.reactive :refer [cell cell=]]
            [neko.activity :as activity])
  (:use com.zakreviews.ceilingbounce.common)
  (:use com.zakreviews.ceilingbounce.theme)
  (:import android.content.Intent))

(defn select-dir [view]
  (activity/start-activity-for-result
   (.getContext view)
   Intent/ACTION_OPEN_DOCUMENT_TREE
   :on-result (fn [act _code data]
                (when-let [uri (.getData data)]
                  (swap! prefs* assoc :directory (str uri))
                  (.takePersistableUriPermission uri
                                                 (bit-or Intent/FLAG_GRANT_READ_URI_PERMISSION
                                                         Intent/FLAG_GRANT_WRITE_URI_PERMISSION
                                                         Intent/FLAG_GRANT_PERSISTABLE_URI_PERMISSION))))))

(def settings-layout
  [:scroll-view {:id ::settings
                 :layout-width :fill
                 :layout-weight 1}
   [:linear-layout {:layout-width :fill
                    :orientation :vertical}
    [:text-view (t :med-text {:text "Calibration"})]
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
                  :layout-weight 1
                  }]]
    [:text-view (t :med-text {:text "Settings"})]
    [:check-box {:text "Use sound"
                 :checked (cell= #(or (@prefs* :use-sound) false))
                 :on-checked-change (fn [_ checked]
                                      (swap! prefs* assoc :use-sound checked))}]
    ;; [:check-box {:text "Aggressively poll sensor"
    ;;              :checked (cell= #(or (@prefs* :aggressive) false))
    ;;              :on-checked-change (fn [_ checked]
    ;;                                   (swap! prefs* assoc :aggressive checked))}]
    ;; [:text-view {:text "Might fix jagged graphs, requires restart"}]
    [:text-view (t :med-text {:text "Data directory"})]
    [:text-view {:text (cell= #(str "Selected: "
                                    (or (:directory @prefs*)
                                        "(NONE)")))}]
    [:button {:text "Select directory"
              :on-click select-dir}]
    ]])
