(ns data-md.compat)

#?(:clj
   (defn tagged-literal-value?
     "True when value is a preserved EDN tagged literal."
     [value]
     (tagged-literal? value)))

#?(:cljs
   (defn tagged-literal-value?
     "True when value is a preserved EDN tagged literal."
     [_value]
     false))

(defn record-type-name [value]
  #?(:clj (-> value class .getName)
     :cljs (str (type value))))
