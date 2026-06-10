(ns data-md.io
  (:require [clojure.edn :as edn]
            [clojure.java.io :as jio])
  (:import [java.io PushbackReader]))

(defn- default-reader-fn [default-reader]
  (cond
    (= :tagged-literal default-reader)
    (fn [tag value] (tagged-literal tag value))

    (ifn? default-reader)
    default-reader

    :else nil))

(defn edn-read-options [opts]
  (cond-> {:eof ::eof
           :readers (:readers opts)}
    (default-reader-fn (:default-reader opts))
    (assoc :default (default-reader-fn (:default-reader opts)))))

(defn- pushback-reader [reader]
  (if (instance? PushbackReader reader)
    reader
    (PushbackReader. reader)))

(defn- read-edn-forms* [reader opts]
  (let [reader (pushback-reader reader)
        read-opts (edn-read-options opts)]
    (loop [forms []]
      (let [value (edn/read read-opts reader)]
        (if (= ::eof value)
          (if (empty? forms)
            (throw (ex-info "EDN input is empty" {:data-md/error :empty-input}))
            forms)
          (recur (conj forms value)))))))

(defn read-edn-forms-reader
  "Read all EDN values from reader."
  [reader opts]
  (try
    (read-edn-forms* reader opts)
    (catch Throwable e
      (if (= :empty-input (:data-md/error (ex-data e)))
        (throw e)
        (throw (ex-info "Could not read EDN input"
                        {:data-md/error :read-error}
                        e))))))

(defn read-edn-forms-file
  "Read all EDN values from path."
  [path opts]
  (try
    (with-open [reader (jio/reader path)]
      (read-edn-forms* reader opts))
    (catch Throwable e
      (if (= :empty-input (:data-md/error (ex-data e)))
        (throw (ex-info "EDN input is empty"
                        {:data-md/error :read-error
                         :path (str path)
                         :cause :empty-input}
                        e))
        (throw (ex-info "Could not read EDN file"
                        {:data-md/error :read-error
                         :path (str path)}
                        e))))))

(defn read-edn-reader
  "Read exactly one EDN value from reader."
  [reader opts]
  (let [forms (read-edn-forms-reader reader opts)]
    (if (= 1 (count forms))
      (first forms)
      (throw (ex-info "Expected exactly one EDN form"
                      {:data-md/error :multiple-forms
                       :count (count forms)})))))

(defn read-edn-file
  "Read exactly one EDN value from path."
  [path opts]
  (let [forms (read-edn-forms-file path opts)]
    (if (= 1 (count forms))
      (first forms)
      (throw (ex-info "Expected exactly one EDN form"
                      {:data-md/error :read-error
                       :path (str path)
                       :cause :multiple-forms
                       :count (count forms)})))))

(defn write-markdown-file!
  "Write markdown to path, creating parent directories as needed."
  [path markdown]
  (try
    (when-let [parent (.getParentFile (jio/file path))]
      (.mkdirs parent))
    (spit path markdown)
    path
    (catch Throwable e
      (throw (ex-info "Could not write Markdown file"
                      {:data-md/error :write-error
                       :path (str path)}
                      e)))))
