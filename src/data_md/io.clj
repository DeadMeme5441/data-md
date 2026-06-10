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

(defn read-edn-reader
  "Read one EDN value from reader."
  [reader opts]
  (let [reader (if (instance? PushbackReader reader)
                 reader
                 (PushbackReader. reader))
        value (edn/read (edn-read-options opts) reader)]
    (if (= ::eof value)
      (throw (ex-info "EDN input is empty" {:data-md/error :empty-input}))
      value)))

(defn read-edn-file
  "Read one EDN value from path."
  [path opts]
  (try
    (with-open [reader (jio/reader path)]
      (read-edn-reader reader opts))
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
