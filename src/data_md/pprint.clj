(ns data-md.pprint
  (:require [clojure.pprint :as pp]
            [clojure.string :as str]))

(defn safe-pr-str
  "Return pr-str for value, falling back to str if printing fails."
  [value]
  (try
    (pr-str value)
    (catch Throwable _
      (try
        (str value)
        (catch Throwable _
          "#object[unprintable]")))))

(defn compact-pr-str
  "Return a single-line printable representation for value."
  [value]
  (-> (safe-pr-str value)
      (str/replace #"\s+" " ")
      str/trim))

(defn pretty-str
  "Return clojure.pprint output for value, falling back to safe-pr-str."
  [value]
  (try
    (with-out-str (pp/pprint value))
    (catch Throwable _
      (safe-pr-str value))))

(defn short-inline?
  "True when s is short enough for an inline code span."
  ([s] (short-inline? s 80))
  ([s max-length]
   (let [s (str s)]
     (and (<= (count s) max-length)
          (not (str/includes? s "\n"))
          (not (str/includes? s "\r"))))))
