(ns com.zakreviews.ceilingbounce.theme)

(def theme
  {:big-text {:text-size [72 :dip]}
   :med-text {:text-size [36 :dip]}
   :normal-text {:text-size [18 :dip]}
   :black {:text-color 0x000000FF}
   :white {:text-color 0xFFFFFFFF}})

(defn t [& args]
  (let [theme-attribs (butlast args)
        neko-map (last args)
        theme-maps (map theme theme-attribs)]
    (apply merge
           (conj theme-maps neko-map))))
