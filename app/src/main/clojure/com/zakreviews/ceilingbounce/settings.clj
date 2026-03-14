(ns com.zakreviews.ceilingbounce.settings
  (:require [neko.reactive :refer [cell cell=]])
  (:use com.zakreviews.ceilingbounce.common)
  (:use com.zakreviews.ceilingbounce.theme)
  (:import android.content.Intent
           android.app.Activity))

(def ^:const REQUEST_DIR 42)

(defn select-dir []
  (let [activity @main-activity
        intent (Intent. Intent/ACTION_OPEN_DOCUMENT_TREE)]
    (.startActivityForResult activity intent REQUEST_DIR)))

(defn on-dir-result [result-code ^Intent data]
  (when (= result-code Activity/RESULT_OK)
    (let [activity @main-activity
          uri (.getData data)
          flags (bit-or Intent/FLAG_GRANT_READ_URI_PERMISSION
                        Intent/FLAG_GRANT_WRITE_URI_PERMISSION)]
      (.takePersistableUriPermission (.getContentResolver activity) uri flags)
      (swap! prefs* assoc :directory (str uri)))))

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
                 :checked (cell= #(@prefs* :use-sound))
                 :on-checked-change (fn [_ checked]
                                      (swap! prefs* assoc :use-sound checked))}]
    [:text-view (t :med-text {:text "Data directory"})]
    [:text-view {:text (cell= #(str "Selected: "
                                    (or (@prefs* :directory)
                                        "(NONE)")))}]
    [:button {:text "Select directory"
              :on-click (fn [_] (select-dir))}]
    ]])
