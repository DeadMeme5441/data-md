(ns data-md.escape
  (:require [clojure.string :as str]))

(defn normalize-newlines
  "Normalize CRLF and CR line endings to LF."
  [s]
  (-> (str s)
      (str/replace "\r\n" "\n")
      (str/replace "\r" "\n")))

(defn- escape-inline-punctuation [s]
  (str/escape s {\\ "\\\\"
                 \` "\\`"
                 \* "\\*"
                 \_ "\\_"
                 \[ "\\["
                 \] "\\]"
                 \< "\\<"
                 \> "\\>"
                 \| "\\|"}))

(defn- neutralize-line-start [line]
  (cond
    (str/starts-with? line "# ") (str "\\" line)
    (str/starts-with? line "- ") (str "\\" line)
    (str/starts-with? line "+ ") (str "\\" line)
    (re-matches #"\d+\. .*" line) (let [[_ n suffix] (re-find #"^(\d+)(\. .*)" line)]
                                     (str n "\\" suffix))
    (or (= "---" line) (= "___" line)) (str "\\" line)
    :else line))

(defn escape-text
  "Escape prose text for GitHub-Flavored Markdown."
  [s]
  (->> (str/split (normalize-newlines s) #"\n" -1)
       (map #(-> % escape-inline-punctuation neutralize-line-start))
       (str/join "\n")))

(defn escape-heading
  "Escape a single-line Markdown heading label."
  [s]
  (let [heading (-> (normalize-newlines s)
                    (str/replace #"\n+" " ")
                    str/trim
                    escape-text)]
    (if (str/blank? heading) "_" heading)))

(defn- longest-run [re s]
  (->> (re-seq re s)
       (map count)
       (cons 0)
       (apply max)))

(defn code-span
  "Render s as an inline Markdown code span, handling embedded backticks."
  [s]
  (let [content (str s)
        tick-count (max 1 (inc (longest-run #"`+" content)))
        delimiter (apply str (repeat tick-count "`"))
        padded? (or (str/starts-with? content "`")
                    (str/ends-with? content "`"))]
    (if padded?
      (str delimiter " " content " " delimiter)
      (str delimiter content delimiter))))

(defn fenced-code
  "Render s as a fenced code block."
  ([s] (fenced-code s {}))
  ([s opts]
   (let [content (normalize-newlines s)
         info (str (:code-language opts "clojure"))
         use-tilde? (str/includes? info "`")
         fence-char (if use-tilde? "~" "`")
         fence-re (if use-tilde? #"~+" #"`+")
         fence-len (max 3 (inc (longest-run fence-re content)))
         fence (apply str (repeat fence-len fence-char))
         body (if (str/ends-with? content "\n") content (str content "\n"))]
     (str fence (when-not (str/blank? info) info) "\n" body fence))))

(defn- escape-pipes [s]
  (loop [chars (seq s)
         escaped? false
         out []]
    (if-let [ch (first chars)]
      (let [pipe? (= \| ch)
            backslash? (= \\ ch)
            out (if (and pipe? (not escaped?))
                  (conj out \\ \|)
                  (conj out ch))]
        (recur (next chars)
               (and backslash? (not escaped?))
               out))
      (apply str out))))

(defn escape-table-cell
  "Escape inline Markdown for use inside a GFM table cell."
  ([s] (escape-table-cell s {}))
  ([s opts]
   (-> (normalize-newlines s)
       (str/replace "\n" (:newline-in-table-cell opts "<br>"))
       escape-pipes
       str/trim)))
