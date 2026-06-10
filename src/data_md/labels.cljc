(ns data-md.labels
  (:require [clojure.string :as str]
            [data-md.pprint :as pprint]))

(defn stable-sort-key
  "Return a deterministic sort key for arbitrary values."
  [opts value]
  (if-let [sort-key-fn (:sort-key-fn opts)]
    #?(:clj (try
              (str (sort-key-fn value))
              (catch Throwable _
                (pprint/compact-pr-str value)))
       :cljs (try
               (str (sort-key-fn value))
               (catch :default _
                 (pprint/compact-pr-str value))))
    (pprint/compact-pr-str value)))

(defn ordered-map-keys
  "Return map keys in a deterministic, readable order."
  [m opts]
  (cond
    (or (record? m)
        (sorted? m)
        #?(:clj (instance? clojure.lang.PersistentArrayMap m)
           :cljs false))
    (keys m)

    :else
    (sort-by #(stable-sort-key opts %) (keys m))))

(defn ordered-set-values
  "Return set values in deterministic order."
  [s opts]
  (if (sorted? s)
    (seq s)
    (sort-by #(stable-sort-key opts %) s)))

(defn humanize-token [s]
  (->> (str/split (str s) #"[-_\s]+")
       (remove str/blank?)
       (map #(str (str/upper-case (subs % 0 1))
                  (str/lower-case (subs % 1))))
       (str/join " ")))

(defn default-key-label
  "Return a human label for a Clojure map key."
  [k]
  (let [token (cond
                (keyword? k) (name k)
                (and (symbol? k) (namespace k)) (str k)
                (symbol? k) (name k)
                (string? k) k
                :else (pprint/compact-pr-str k))
        label (if (and (symbol? k) (namespace k))
                token
                (humanize-token token))]
    (if (str/blank? label) "_" label)))

(defn key-label
  [opts k]
  (if-let [f (:key-label-fn opts)]
    (str (f k))
    (default-key-label k)))

(defn dedupe-labels
  "Return labels for keys, falling back to pr-str labels on collisions."
  [opts ks]
  (let [labels (mapv #(key-label opts %) ks)
        freqs (frequencies labels)]
    (mapv (fn [k label]
            (if (> (get freqs label 0) 1)
              (pprint/compact-pr-str k)
              label))
          ks
          labels)))

(defn keys->labels
  ([ks] (keys->labels {} ks))
  ([opts ks] (dedupe-labels opts ks)))
