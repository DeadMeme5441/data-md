(ns data-md.cljs-smoke
  (:require [clojure.string :as str]
            [data-md.core :as md]))

(defn- assert= [expected actual]
  (when-not (= expected actual)
    (throw (js/Error. (str "Expected " (pr-str expected) ", got " (pr-str actual))))))

(defn -main [& _]
  (assert= "## Project\n\nfoo\n\n## Status\n\n`:green`\n"
           (md/render {:project "foo" :status :green}))
  (assert= "| A |\n| --- |\n| `1` |\n| `2` |\n"
           (md/render-table [{:a 1} {:a 2}]))
  (when-not (str/includes? (md/render (range) {:max-collection-size 3})
                           "truncated after 3")
    (throw (js/Error. "Expected lazy sequence truncation")))
  (println "ClojureScript smoke passed"))

(set! *main-cli-fn* -main)
