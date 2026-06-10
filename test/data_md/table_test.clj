(ns data-md.table-test
  (:require [clojure.test :refer [deftest is]]
            [data-md.core :as md]))

(deftest map-row-table-test
  (is (= "| A | B |\n| --- | --- |\n| `1` | x |\n"
         (md/render-table [{:a 1 :b "x"}]))))

(deftest missing-nil-and-nested-table-test
  (is (= "| A | B | C |\n| --- | --- | --- |\n| `1` | x\\|y |  |\n|  | `nil` | `{:nested true}` |\n"
         (md/render-table [{:a 1 :b "x|y"}
                           {:b nil :c {:nested true}}]))))

(deftest empty-rows-test
  (is (= "" (md/render-table [])))
  (is (= "| A |\n| --- |\n" (md/render-table [] {:columns [:a]}))))

(deftest vector-rows-with-columns-test
  (is (= "| Name | Lang |\n| --- | --- |\n| Ada | Clojure |\n| Rich | Clojure |\n"
         (md/render-table [["Ada" "Clojure"]
                           ["Rich" "Clojure"]]
                          {:columns [:name :lang]}))))

(deftest alignment-test
  (is (= "| A | B |\n| :--- | ---: |\n| `1` | `2` |\n"
         (md/render-table [{:a 1 :b 2}]
                          {:table-align {:a :left :b :right}}))))

(deftest invalid-row-test
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"Table rows must be maps"
                        (md/render-table [[1 2]]))))

(deftest cell-renderer-test
  (let [percentage (fn [v _]
                     (when (number? v)
                       (format "%.0f%%" (* 100 v))))]
    (is (= "| Score |\n| --- |\n| 92% |\n"
           (md/render-table [{:score 0.92}]
                            {:cell-renderer percentage})))))

(deftest table-truncation-test
  (is (= "| A |\n| --- |\n| `0` |\n| `1` |\n| ... truncated after 2 rows |\n"
         (md/render-table (map (fn [n] {:a n}) (range))
                          {:max-collection-size 2}))))
