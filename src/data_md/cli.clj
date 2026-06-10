(ns data-md.cli
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [data-md.core :as md]
            [data-md.io :as data-io]
            [data-md.render :as render]))

(def version "0.2.0")

(def usage
  (str "data-md renders EDN data as GitHub-Flavored Markdown.\n\n"
       "Usage:\n"
       "  data-md input.edn\n"
       "  data-md input.edn output.md\n"
       "  data-md --title \"deps.edn\" input.edn output.md\n"
       "  data-md --table rows.edn\n"
       "  data-md --columns name,lang --table rows.edn\n"
       "  data-md --align right --table rows.edn\n"
       "  data-md --align-col lang=center --table rows.edn\n"
       "  data-md --max-depth 3 --max-items 50 input.edn\n"
       "  data-md --line-ending crlf --no-final-newline input.edn\n"
       "  data-md --version\n"
       "  data-md --help\n\n"
       "Use - for stdin or stdout paths. Multiple EDN forms render as numbered sections.\n"))

(defn- cli-error! [message data]
  (throw (ex-info message (merge {:data-md/error :invalid-cli} data))))

(defn- parse-int-flag [flag value]
  (try
    (Integer/parseInt value)
    (catch Throwable _
      (cli-error! (str "Invalid integer for " flag) {:flag flag :value value}))))

(defn- require-value [args flag]
  (if-let [value (second args)]
    value
    (cli-error! (str flag " requires a value") {:flag flag})))

(defn- parse-choice [flag value choices]
  (let [choice (keyword value)]
    (if (contains? choices choice)
      choice
      (cli-error! (str "Invalid value for " flag)
                  {:flag flag
                   :value value
                   :choices choices}))))

(defn- parse-line-ending [value]
  (case value
    "lf" "\n"
    "crlf" "\r\n"
    "cr" "\r"
    (cli-error! "Invalid value for --line-ending"
                {:flag "--line-ending"
                 :value value
                 :choices #{"lf" "crlf" "cr"}})))

(defn- parse-column-token [token]
  (let [token (str/trim token)]
    (cond
      (str/blank? token)
      (cli-error! "Column names must not be blank" {:flag "--columns"})

      (str/starts-with? token ":")
      (try
        (edn/read-string token)
        (catch Throwable _
          (cli-error! "Invalid EDN column token" {:token token})))

      :else
      (keyword token))))

(defn- parse-columns [value]
  (let [value (str/trim value)]
    (if (str/starts-with? value "[")
      (let [columns (try
                      (edn/read-string value)
                      (catch Throwable _
                        (cli-error! "Invalid EDN vector for --columns"
                                    {:flag "--columns" :value value})))]
        (when-not (vector? columns)
          (cli-error! "--columns EDN value must be a vector"
                      {:flag "--columns" :value value}))
        columns)
      (mapv parse-column-token (str/split value #",")))))

(defn- parse-align-col [value]
  (let [[column align extra] (str/split value #"=" 3)]
    (when (or extra (str/blank? column) (str/blank? align))
      (cli-error! "--align-col expects column=left|center|right"
                  {:flag "--align-col" :value value}))
    [(parse-column-token column)
     (parse-choice "--align-col" align #{:left :center :right})]))

(def ^:private style-flags
  {"--map-style" [:map-style #{:auto :sections :list :code-block}]
   "--seq-style" [:seq-style #{:auto :table :list :code-block}]
   "--set-style" [:set-style #{:list :code-block}]
   "--record-style" [:record-style #{:map-with-type :map :code-block}]
   "--string-style" [:string-style #{:plain :code}]
   "--keyword-style" [:keyword-style #{:code :plain}]
   "--symbol-style" [:symbol-style #{:code :plain}]
   "--number-style" [:number-style #{:code :plain}]
   "--boolean-style" [:boolean-style #{:code :plain}]
   "--nil-style" [:nil-style #{:code :plain}]
   "--char-style" [:char-style #{:code :plain}]
   "--inst-style" [:inst-style #{:code :plain}]
   "--uuid-style" [:uuid-style #{:code :plain}]})

(defn parse-args
  "Parse CLI args into {:opts ... :table? ... :input ... :output ...}."
  [args]
  (loop [args (seq args)
         opts {}
         table? false
         help? false
         version? false
         align-cols {}
         positional []]
    (if-let [arg (first args)]
      (if-let [[option choices] (get style-flags arg)]
        (let [value (require-value args arg)]
          (recur (nnext args)
                 (assoc opts option (parse-choice arg value choices))
                 table?
                 help?
                 version?
                 align-cols
                 positional))
        (case arg
          "--help" (recur (next args) opts table? true version? align-cols positional)
          "-h" (recur (next args) opts table? true version? align-cols positional)
          "--version" (recur (next args) opts table? help? true align-cols positional)
          "--table" (recur (next args) opts true help? version? align-cols positional)
          "--title" (let [value (require-value args "--title")]
                      (recur (nnext args) (assoc opts :title value) table? help? version? align-cols positional))
          "--heading-level" (let [value (require-value args "--heading-level")]
                              (recur (nnext args)
                                     (assoc opts :heading-level (parse-int-flag "--heading-level" value))
                                     table?
                                     help?
                                     version?
                                     align-cols
                                     positional))
          "--max-depth" (let [value (require-value args "--max-depth")]
                          (recur (nnext args)
                                 (assoc opts :max-depth (parse-int-flag "--max-depth" value))
                                 table?
                                 help?
                                 version?
                                 align-cols
                                 positional))
          "--max-items" (let [value (require-value args "--max-items")]
                          (recur (nnext args)
                                 (assoc opts :max-collection-size
                                        (parse-int-flag "--max-items" value))
                                 table?
                                 help?
                                 version?
                                 align-cols
                                 positional))
          "--columns" (let [value (require-value args "--columns")]
                        (recur (nnext args)
                               (assoc opts :columns (parse-columns value))
                               table?
                               help?
                               version?
                               align-cols
                               positional))
          "--align" (let [value (require-value args "--align")]
                      (when (seq align-cols)
                        (cli-error! "Cannot combine --align with --align-col"
                                    {:flag "--align"}))
                      (recur (nnext args)
                             (assoc opts :table-align
                                    (parse-choice "--align" value #{:left :center :right}))
                             table?
                             help?
                             version?
                             align-cols
                             positional))
          "--table-align" (let [value (require-value args "--table-align")]
                            (when (seq align-cols)
                              (cli-error! "Cannot combine --table-align with --align-col"
                                          {:flag "--table-align"}))
                            (recur (nnext args)
                                   (assoc opts :table-align
                                          (parse-choice "--table-align" value #{:left :center :right}))
                                   table?
                                   help?
                                   version?
                                   align-cols
                                   positional))
          "--align-col" (let [[column align] (parse-align-col (require-value args "--align-col"))]
                          (when (keyword? (:table-align opts))
                            (cli-error! "Cannot combine --align-col with --align"
                                        {:flag "--align-col"}))
                          (recur (nnext args)
                                 opts
                                 table?
                                 help?
                                 version?
                                 (assoc align-cols column align)
                                 positional))
          "--missing-cell" (let [value (require-value args "--missing-cell")]
                             (recur (nnext args)
                                    (assoc opts :missing-cell value)
                                    table?
                                    help?
                                    version?
                                    align-cols
                                    positional))
          "--newline-in-cell" (let [value (require-value args "--newline-in-cell")]
                                (recur (nnext args)
                                       (assoc opts :newline-in-table-cell value)
                                       table?
                                       help?
                                       version?
                                       align-cols
                                       positional))
          "--line-ending" (let [value (require-value args "--line-ending")]
                            (recur (nnext args)
                                   (assoc opts :line-ending (parse-line-ending value))
                                   table?
                                   help?
                                   version?
                                   align-cols
                                   positional))
          "--no-final-newline" (recur (next args)
                                      (assoc opts :final-newline? false)
                                      table?
                                      help?
                                      version?
                                      align-cols
                                      positional)
          "--code-language" (let [value (require-value args "--code-language")]
                              (recur (nnext args)
                                     (assoc opts :code-language value)
                                     table?
                                     help?
                                     version?
                                     align-cols
                                     positional))
          (if (str/starts-with? arg "--")
            (cli-error! (str "Unknown flag: " arg) {:flag arg})
            (recur (next args) opts table? help? version? align-cols (conj positional arg)))))
      (let [[input output & extra] positional]
        (when extra
          (cli-error! "Too many positional arguments" {:extra extra}))
        {:opts (cond-> opts
                 (seq align-cols) (assoc :table-align align-cols))
         :table? table?
         :help? help?
         :version? version?
         :input input
         :output output}))))

(defn- read-input [input opts]
  (if (= "-" input)
    (data-io/read-edn-forms-reader *in* opts)
    (data-io/read-edn-forms-file input opts)))

(defn- render-input [{:keys [input table? opts]}]
  (let [opts (render/normalize-options opts)
        forms (read-input input opts)]
    (if table?
      (render/render-table-forms-document forms opts)
      (md/render-forms forms opts))))

(defn- write-output! [output markdown]
  (if (or (nil? output) (= "-" output))
    (print markdown)
    (data-io/write-markdown-file! output markdown)))

(defn -main [& args]
  (try
    (let [{:keys [help? version? input output] :as parsed} (parse-args args)]
      (cond
        help? (print usage)
        version? (println version)
        (nil? input) (do
                       (binding [*out* *err*]
                         (println usage))
                       (System/exit 2))
        :else (write-output! output (render-input parsed))))
    (catch clojure.lang.ExceptionInfo e
      (binding [*out* *err*]
        (println (.getMessage e))
        (when-not (= :invalid-cli (:data-md/error (ex-data e)))
          (println (pr-str (ex-data e)))))
      (System/exit (if (= :invalid-cli (:data-md/error (ex-data e))) 2 1)))))
