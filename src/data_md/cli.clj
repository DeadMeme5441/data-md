(ns data-md.cli
  (:require [clojure.string :as str]
            [data-md.core :as md]
            [data-md.io :as data-io]
            [data-md.render :as render]))

(def usage
  (str "data-md renders EDN data as GitHub-Flavored Markdown.\n\n"
       "Usage:\n"
       "  data-md input.edn\n"
       "  data-md input.edn output.md\n"
       "  data-md --title \"deps.edn\" input.edn output.md\n"
       "  data-md --table rows.edn\n"
       "  data-md --max-depth 3 input.edn\n"
       "  data-md --max-items 50 input.edn\n"
       "  data-md --help\n\n"
       "Use - for stdin or stdout paths.\n"))

(defn- cli-error! [message data]
  (throw (ex-info message (merge {:data-md/error :invalid-cli} data))))

(defn- parse-int-flag [flag value]
  (try
    (Integer/parseInt value)
    (catch Throwable _
      (cli-error! (str "Invalid integer for " flag) {:flag flag :value value}))))

(defn parse-args
  "Parse CLI args into {:opts ... :table? ... :input ... :output ...}."
  [args]
  (loop [args (seq args)
         opts {}
         table? false
         help? false
         positional []]
    (if-let [arg (first args)]
      (case arg
        "--help" (recur (next args) opts table? true positional)
        "-h" (recur (next args) opts table? true positional)
        "--table" (recur (next args) opts true help? positional)
        "--title" (if-let [value (second args)]
                    (recur (nnext args) (assoc opts :title value) table? help? positional)
                    (cli-error! "--title requires a value" {:flag "--title"}))
        "--max-depth" (if-let [value (second args)]
                        (recur (nnext args)
                               (assoc opts :max-depth (parse-int-flag "--max-depth" value))
                               table?
                               help?
                               positional)
                        (cli-error! "--max-depth requires a value" {:flag "--max-depth"}))
        "--max-items" (if-let [value (second args)]
                        (recur (nnext args)
                               (assoc opts :max-collection-size
                                      (parse-int-flag "--max-items" value))
                               table?
                               help?
                               positional)
                        (cli-error! "--max-items requires a value" {:flag "--max-items"}))
        (if (str/starts-with? arg "--")
          (cli-error! (str "Unknown flag: " arg) {:flag arg})
          (recur (next args) opts table? help? (conj positional arg))))
      (let [[input output & extra] positional]
        (when extra
          (cli-error! "Too many positional arguments" {:extra extra}))
        {:opts opts
         :table? table?
         :help? help?
         :input input
         :output output}))))

(defn- read-input [input opts]
  (if (= "-" input)
    (data-io/read-edn-reader *in* opts)
    (data-io/read-edn-file input opts)))

(defn- render-input [{:keys [input table? opts]}]
  (let [opts (render/normalize-options opts)
        value (read-input input opts)]
    (if table?
      (md/render-table value opts)
      (md/render value opts))))

(defn- write-output! [output markdown]
  (if (or (nil? output) (= "-" output))
    (print markdown)
    (data-io/write-markdown-file! output markdown)))

(defn -main [& args]
  (try
    (let [{:keys [help? input output] :as parsed} (parse-args args)]
      (cond
        help? (print usage)
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
      (System/exit 1))))
