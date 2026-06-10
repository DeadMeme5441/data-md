(ns data-md.render
  (:require [clojure.string :as str]
            [data-md.compat :as compat]
            [data-md.escape :as escape]
            [data-md.labels :as labels]
            [data-md.pprint :as pprint]
            [data-md.table :as table]))

(def default-options
  {:title nil
   :heading-level 2
   :final-newline? true
   :line-ending "\n"
   :max-depth 4
   :max-collection-size 100
   :large-collection-strategy :truncate
   :map-style :auto
   :seq-style :auto
   :set-style :list
   :empty-style :literal
   :record-style :map-with-type
   :include-metadata? false
   :map-key-order :preserve-known-else-sort
   :set-order :sort
   :table-column-order :first-seen
   :columns nil
   :missing-cell ""
   :nested-cell-style :inline-code
   :newline-in-table-cell "<br>"
   :table-align nil
   :string-style :plain
   :keyword-style :code
   :symbol-style :code
   :number-style :code
   :boolean-style :code
   :nil-style :code
   :char-style :code
   :inst-style :code
   :uuid-style :code
   :code-language "clojure"
   :escape-mode :gfm
   :key-label-fn nil
   :value-renderer nil
   :cell-renderer nil
   :sort-key-fn nil
   :readers {}
   :default-reader :tagged-literal})

(def valid-option-keys (set (keys default-options)))

(def ^:private allowed-keywords
  {:large-collection-strategy #{:truncate :code-block}
   :map-style #{:auto :sections :list :code-block}
   :seq-style #{:auto :table :list :code-block}
   :set-style #{:list :code-block}
   :empty-style #{:literal}
   :record-style #{:map-with-type :map :code-block}
   :map-key-order #{:preserve-known-else-sort}
   :set-order #{:sort}
   :table-column-order #{:first-seen}
   :nested-cell-style #{:inline-code :code-block-string}
   :string-style #{:plain :code}
   :keyword-style #{:code :plain}
   :symbol-style #{:code :plain}
   :number-style #{:code :plain}
   :boolean-style #{:code :plain}
   :nil-style #{:code :plain}
   :char-style #{:code :plain}
   :inst-style #{:code :plain}
   :uuid-style #{:code :plain}
   :escape-mode #{:gfm}})

(defn- invalid-option! [option value message]
  (throw (ex-info "Invalid data-md option"
                  {:data-md/error :invalid-option
                   :option option
                   :value value
                   :message message})))

(defn- validate-keyword-option [opts option]
  (let [value (get opts option)]
    (when-not (contains? (get allowed-keywords option) value)
      (invalid-option! option value "Unsupported keyword value"))))

(defn normalize-options
  "Merge and validate public data-md options."
  ([] default-options)
  ([opts]
   (when-not (map? opts)
     (invalid-option! :opts opts "Options must be a map"))
   (when-let [unknown (seq (remove valid-option-keys (keys opts)))]
     (invalid-option! (first unknown) (get opts (first unknown)) "Unknown option"))
   (let [opts (merge default-options opts)]
     (doseq [option (keys allowed-keywords)]
       (validate-keyword-option opts option))
     (when-not (or (nil? (:title opts)) (string? (:title opts)))
       (invalid-option! :title (:title opts) "Expected string or nil"))
     (when-not (pos-int? (:heading-level opts))
       (invalid-option! :heading-level (:heading-level opts) "Expected positive integer"))
     (when-not (boolean? (:final-newline? opts))
       (invalid-option! :final-newline? (:final-newline? opts) "Expected boolean"))
     (when-not (string? (:line-ending opts))
       (invalid-option! :line-ending (:line-ending opts) "Expected string"))
     (when-not (and (int? (:max-depth opts)) (not (neg? (:max-depth opts))))
       (invalid-option! :max-depth (:max-depth opts) "Expected non-negative integer"))
     (when-not (pos-int? (:max-collection-size opts))
       (invalid-option! :max-collection-size (:max-collection-size opts) "Expected positive integer"))
     (when-not (or (nil? (:columns opts)) (vector? (:columns opts)))
       (invalid-option! :columns (:columns opts) "Expected vector or nil"))
     (when-not (or (nil? (:table-align opts))
                   (contains? #{:left :center :right} (:table-align opts))
                   (map? (:table-align opts)))
       (invalid-option! :table-align (:table-align opts) "Expected nil, alignment keyword, or map"))
     (doseq [option [:key-label-fn :value-renderer :cell-renderer :sort-key-fn]]
       (when-not (or (nil? (get opts option)) (ifn? (get opts option)))
         (invalid-option! option (get opts option) "Expected function or nil")))
     (when-not (map? (:readers opts))
       (invalid-option! :readers (:readers opts) "Expected map"))
     (when-not (or (nil? (:default-reader opts))
                   (= :tagged-literal (:default-reader opts))
                   (ifn? (:default-reader opts)))
       (invalid-option! :default-reader (:default-reader opts)
                        "Expected :tagged-literal, function, or nil"))
     opts)))

(defn- strip-trailing-newlines [s]
  (str/replace (str s) #"\n+\z" ""))

(defn finalize-markdown [s opts]
  (let [s (strip-trailing-newlines s)
        s (if (:final-newline? opts) (str s "\n") s)]
    (if (= "\n" (:line-ending opts))
      s
      (str/replace s "\n" (:line-ending opts)))))

(defn- heading [level label]
  (str (apply str (repeat level "#")) " " (escape/escape-heading label)))

(defn- fenced [value opts]
  (escape/fenced-code (pprint/pretty-str value) opts))

(defn- empty-literal [value]
  (cond
    (map? value) "{}"
    (vector? value) "[]"
    (set? value) "#{}"
    (list? value) "()"
    (sequential? value) "()"
    :else (pprint/compact-pr-str value)))

(defn- scalar? [value]
  (or (nil? value)
      (true? value)
      (false? value)
      (string? value)
      (char? value)
      (keyword? value)
      (symbol? value)
      (number? value)
      (uuid? value)
      (inst? value)
      (compat/tagged-literal-value? value)))

(defn render-scalar [value ctx]
  (let [opts (:opts ctx)]
    (cond
      (nil? value) (escape/code-span "nil")
      (string? value) (if (or (empty? value) (= :code (:string-style opts)))
                        (escape/code-span (pprint/compact-pr-str value))
                        (escape/escape-text value))
      (true? value) (if (= :code (:boolean-style opts))
                      (escape/code-span "true")
                      "true")
      (false? value) (if (= :code (:boolean-style opts))
                       (escape/code-span "false")
                       "false")
      (keyword? value) (if (= :code (:keyword-style opts))
                         (escape/code-span (pprint/compact-pr-str value))
                         (escape/escape-text (str value)))
      (symbol? value) (if (= :code (:symbol-style opts))
                        (escape/code-span (pprint/compact-pr-str value))
                        (escape/escape-text (str value)))
      (number? value) (if (= :code (:number-style opts))
                        (escape/code-span (pprint/compact-pr-str value))
                        (escape/escape-text (str value)))
      (char? value) (if (= :code (:char-style opts))
                      (escape/code-span (pprint/compact-pr-str value))
                      (escape/escape-text (str value)))
      (uuid? value) (if (= :code (:uuid-style opts))
                      (escape/code-span (pprint/compact-pr-str value))
                      (escape/escape-text (str value)))
      (inst? value) (if (= :code (:inst-style opts))
                      (escape/code-span (pprint/compact-pr-str value))
                      (escape/escape-text (str value)))
      (compat/tagged-literal-value? value) (escape/code-span (pprint/compact-pr-str value))
      :else nil)))

(defn render-fallback [value ctx]
  (let [opts (:opts ctx)
        printed (pprint/compact-pr-str value)]
    (if (pprint/short-inline? printed)
      (escape/code-span printed)
      (fenced value opts))))

(declare render-value)

(defn- inline-value [value ctx]
  (if (scalar? value)
    (render-scalar value ctx)
    (let [printed (pprint/compact-pr-str value)]
      (if (pprint/short-inline? printed)
        (escape/code-span printed)
        (escape/escape-text printed)))))

(defn- bounded-items [coll opts]
  (let [limit (:max-collection-size opts)
        sampled (doall (take (inc limit) coll))
        counted-total (when (and (not (seq? coll)) (counted? coll))
                        (count coll))]
    {:items (vec (take limit sampled))
     :truncated? (> (count sampled) limit)
     :total counted-total}))

(defn- truncation-line [opts total noun]
  (str "- ... truncated after " (:max-collection-size opts)
       (when total (str " of " total))
       " " noun))

(defn- render-seq-list [coll ctx]
  (let [opts (:opts ctx)
        {:keys [items truncated? total]} (bounded-items coll opts)
        lines (cond-> (mapv #(str "- " (inline-value % (update ctx :depth inc))) items)
                truncated? (conj (truncation-line opts total "items")))]
    (str/join "\n" lines)))

(defn- key-value-line [_k v label ctx]
  (str "- **" (escape/escape-text label) ":** "
       (inline-value v (update ctx :depth inc))))

(defn- render-map-list [m ctx]
  (let [opts (:opts ctx)
        ks (labels/ordered-map-keys m opts)
        labels (labels/dedupe-labels opts ks)]
    (str/join "\n"
              (map (fn [k label]
                     (key-value-line k (get m k) label ctx))
                   ks
                   labels))))

(defn- complex-value? [value]
  (and (not (scalar? value))
       (or (map? value) (sequential? value) (set? value) (record? value))))

(defn- render-map-sections [m ctx]
  (let [opts (:opts ctx)
        {:keys [items truncated? total]} (bounded-items (labels/ordered-map-keys m opts) opts)
        section-labels (labels/dedupe-labels opts items)
        level (:heading-level ctx)
        sections (mapv (fn [k label]
                         (str (heading level label)
                              "\n\n"
                              (strip-trailing-newlines
                               (render-value (get m k)
                                             (-> ctx
                                                 (update :depth inc)
                                                 (update :heading-level inc)
                                                 (assoc :path (conj (:path ctx) k)))))))
                       items
                       section-labels)
        sections (cond-> sections
                   truncated? (conj (truncation-line opts total "entries")))]
    (str/join "\n\n" sections)))

(defn- render-map-mixed [m ctx]
  (let [opts (:opts ctx)
        ks (labels/ordered-map-keys m opts)
        label-map (zipmap ks (labels/dedupe-labels opts ks))
        scalar-ks (remove #(complex-value? (get m %)) ks)
        complex-ks (filter #(complex-value? (get m %)) ks)
        scalar-block (when (seq scalar-ks)
                       (str/join "\n"
                                 (map #(key-value-line % (get m %) (get label-map %) ctx)
                                      scalar-ks)))
        level (:heading-level ctx)
        complex-blocks (map (fn [k]
                              (str (heading level (get label-map k))
                                   "\n\n"
                                   (strip-trailing-newlines
                                    (render-value (get m k)
                                                  (-> ctx
                                                      (update :depth inc)
                                                      (update :heading-level inc)
                                                      (assoc :path (conj (:path ctx) k)))))))
                            complex-ks)]
    (str/join "\n\n" (remove str/blank? (cons scalar-block complex-blocks)))))

(defn render-map [m ctx]
  (let [opts (:opts ctx)]
    (cond
      (empty? m) (escape/code-span "{}")
      (= :code-block (:map-style opts)) (fenced m opts)
      (or (= :sections (:map-style opts))
          (zero? (:depth ctx))) (render-map-sections m ctx)
      (every? (comp not complex-value?) (vals m)) (render-map-list m ctx)
      :else (render-map-mixed m ctx))))

(defn render-set [s ctx]
  (let [opts (:opts ctx)]
    (cond
      (empty? s) (escape/code-span "#{}")
      (= :code-block (:set-style opts)) (fenced s opts)
      :else (let [{:keys [items truncated? total]} (bounded-items (labels/ordered-set-values s opts) opts)
                  lines (cond-> (mapv #(str "- " (inline-value % (update ctx :depth inc))) items)
                          truncated? (conj (truncation-line opts total "items")))]
              (str/join "\n" lines)))))

(defn render-record [value ctx]
  (let [opts (:opts ctx)]
    (case (:record-style opts)
      :code-block (fenced value opts)
      :map (render-map value ctx)
      :map-with-type (str "**Type:** "
                          (escape/code-span (compat/record-type-name value))
                          "\n\n"
                          (render-map-list value (update ctx :depth inc))))))

(defn render-seq [coll ctx]
  (let [opts (:opts ctx)]
    (cond
      (empty? (take 1 coll)) (escape/code-span (empty-literal coll))
      (= :code-block (:seq-style opts)) (fenced coll opts)
      (or (= :table (:seq-style opts))
          (table/table-shaped? (take (:max-collection-size opts) coll)))
      (table/render-table* coll opts)
      :else (render-seq-list coll ctx))))

(defn- render-value* [value ctx]
  (let [opts (:opts ctx)]
    (cond
      (and (>= (:depth ctx) (:max-depth opts))
           (not (scalar? value))) (fenced value opts)
      (record? value) (render-record value ctx)
      (scalar? value) (render-scalar value ctx)
      (map? value) (render-map value ctx)
      (set? value) (render-set value ctx)
      (sequential? value) (render-seq value ctx)
      :else (render-fallback value ctx))))

(defn render-value [value ctx]
  (let [opts (:opts ctx)]
    (if-let [custom (:value-renderer opts)]
      (if-let [rendered (custom value ctx)]
        rendered
        (render-value* value ctx))
      (render-value* value ctx))))

(defn render-document [value opts]
  (let [opts (normalize-options opts)
        ctx {:opts opts
             :depth 0
             :path []
             :heading-level (:heading-level opts)}
        metadata-block (when (and (:include-metadata? opts) (meta value))
                         (str "**Metadata:** "
                              (escape/code-span (pprint/compact-pr-str (meta value)))))
        body (render-value value ctx)
        title-block (when (:title opts)
                      (heading 1 (:title opts)))
        doc (str/join "\n\n" (remove str/blank? [title-block metadata-block body]))]
    (finalize-markdown doc opts)))
