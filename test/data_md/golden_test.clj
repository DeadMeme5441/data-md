(ns data-md.golden-test
  (:require [clojure.java.io :as jio]
            [clojure.test :refer [deftest is]]
            [data-md.core :as md]))

(defn- golden [name]
  (slurp (jio/resource (str "golden/" name ".md"))))

(deftest golden-simple-test
  (is (= (golden "simple")
         (md/render-file "test-resources/simple-full.edn"))))

(deftest golden-table-test
  (is (= (golden "table")
         (md/render-table [{:name "Ada" :role :admin}
                           {:name "Rich" :language "Clojure"}]))))

(deftest golden-escaping-test
  (is (= (golden "escaping")
         (md/render-file "test-resources/escaping.edn"))))

(deftest golden-truncation-test
  (is (= (golden "truncation")
         (md/render (range) {:max-collection-size 3}))))

(deftest golden-tagged-test
  (is (= (golden "tagged")
         (md/render-file "test-resources/tagged.edn"))))

(deftest golden-deps-test
  (is (= (golden "deps")
         (md/render-file "test-resources/deps-sample.edn"
                         {:title "deps.edn"}))))
