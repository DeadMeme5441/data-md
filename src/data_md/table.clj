(ns data-md.table
  (:require [clojure.string :as str]
            [data-md.escape :as escape]
            [data-md.labels :as labels]
            [data-md.pprint :as pprint]))

(def ^:private missing ::missing)

(defn- invalid-table! [message data]
  (throw (ex-info message (merge {:data-md/error :invalid-table} data))))

(defn table-shaped?
  "True when rows is a non-empty sequence of map-like rows."
  [rows]
  (and (seq rows) (every? map? rows)))

(defn- bounded-rows [rows opts]
  (let [limit (:max-collection-size opts)
        sampled (doall (take (inc limit) rows))]
    {:rows (vec (take limit sampled))
     :truncated? (> (count sampled) limit)}))

(defn infer-columns
  "Infer table columns using first-seen key order across row maps."
  [rows opts]
  (if (vector? (:columns opts))
    (:columns opts)
    (loop [remaining rows
           seen #{}
           cols []]
      (if-let [row (first remaining)]
        (if (map? row)
          (let [new-keys (remove seen (labels/ordered-map-keys row opts))]
            (recur (rest remaining)
                   (into seen new-keys)
                   (into cols new-keys)))
          (invalid-table! "Table rows must be maps unless :columns is supplied"
                          {:row row}))
        cols))))

(defn- alignment-for [opts column]
  (let [align (:table-align opts)]
    (cond
      (map? align) (get align column)
      (keyword? align) align
      :else nil)))

(defn- delimiter-cell [align]
  (case align
    :left ":---"
    :center ":---:"
    :right "---:"
    "---"))

(defn alignment-row [columns opts]
  (mapv #(delimiter-cell (alignment-for opts %)) columns))

(defn- scalar-cell [value]
  (cond
    (nil? value) (escape/code-span "nil")
    (string? value) (if (empty? value)
                      (escape/code-span "\"\"")
                      (escape/escape-text value))
    (or (keyword? value)
        (symbol? value)
        (number? value)
        (true? value)
        (false? value)
        (char? value)
        (uuid? value)
        (inst? value)
        (instance? clojure.lang.TaggedLiteral value))
    (escape/code-span (pprint/compact-pr-str value))
    (or (map? value) (sequential? value) (set? value))
    (escape/code-span (pprint/compact-pr-str value))
    :else
    (let [printed (pprint/compact-pr-str value)]
      (if (pprint/short-inline? printed)
        (escape/code-span printed)
        (escape/escape-text printed)))))

(defn render-cell
  "Render one table cell."
  [value opts ctx]
  (if (= missing value)
    (:missing-cell opts)
    (let [inline (if-let [cell-renderer (:cell-renderer opts)]
                   (or (cell-renderer value ctx)
                       (scalar-cell value))
                   (scalar-cell value))]
      (escape/escape-table-cell inline opts))))

(defn- row-value [row column idx]
  (cond
    (map? row) (if (contains? row column) (get row column) missing)
    (sequential? row) (let [v (nth row idx missing)]
                        (if (= missing v) missing v))
    :else (invalid-table! "Invalid table row" {:row row})))

(defn render-row [row columns opts]
  (str "| "
       (str/join " | "
                 (map-indexed (fn [idx column]
                                (render-cell (row-value row column idx)
                                             opts
                                             {:column column
                                              :column-index idx
                                              :row row}))
                              columns))
       " |"))

(defn- render-header [labels opts]
  (str "| "
       (str/join " | " (map #(escape/escape-table-cell (escape/escape-heading %) opts) labels))
       " |"))

(defn- render-delimiter [columns opts]
  (str "| " (str/join " | " (alignment-row columns opts)) " |"))

(defn render-table*
  "Render rows as a GFM table string."
  [rows opts]
  (when-not (sequential? rows)
    (invalid-table! "Rows must be sequential" {:rows rows}))
  (let [{realized-rows :rows truncated? :truncated?} (bounded-rows rows opts)
        columns (infer-columns realized-rows opts)]
    (cond
      (and (empty? realized-rows) (empty? columns)) ""
      (and (empty? columns) (seq realized-rows)) ""
      :else
      (let [header-labels (labels/dedupe-labels opts columns)
            base-lines (into [(render-header header-labels opts)
                              (render-delimiter columns opts)]
                             (map #(render-row % columns opts) realized-rows))
            lines (cond-> base-lines
                    truncated? (conj (render-row (zipmap columns
                                                          (cons (str "... truncated after "
                                                                     (:max-collection-size opts)
                                                                     " rows")
                                                                (repeat (:missing-cell opts))))
                                                 columns
                                                 opts)))]
        (str (str/join "\n" lines) "\n")))))
