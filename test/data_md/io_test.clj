(ns data-md.io-test
  (:require [clojure.java.io :as jio]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [data-md.core :as md]))

(deftest render-file-test
  (is (= "## Project\n\nfoo\n\n## Status\n\n`:green`\n"
         (md/render-file "test-resources/simple.edn"))))

(deftest tagged-literal-test
  (is (str/includes? (md/render-file "test-resources/tagged.edn")
                     "`#my/tag {:a 1}`")))

(deftest inst-and-uuid-test
  (let [rendered (md/render-file "test-resources/scalars.edn")]
    (is (str/includes? rendered "#inst"))
    (is (str/includes? rendered "#uuid"))))

(deftest write-file-test
  (let [out (jio/file "target/test-output/simple.md")]
    (when (.exists out) (.delete out))
    (is (= "target/test-output/simple.md"
           (md/write-file! "test-resources/simple.edn"
                           "target/test-output/simple.md")))
    (is (str/includes? (slurp out) "## Project"))))

(deftest invalid-edn-test
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"Could not read EDN file"
                        (md/render-file "test-resources/invalid.edn"))))
