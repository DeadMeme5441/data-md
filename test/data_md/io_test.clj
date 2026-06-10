(ns data-md.io-test
  (:require [clojure.java.io :as jio]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [data-md.core :as md]
            [data-md.io :as data-io])
  (:import [java.io StringReader]))

(deftest render-file-test
  (is (= "## Project\n\nfoo\n\n## Status\n\n`:green`\n"
         (md/render-file "test-resources/simple.edn"))))

(deftest read-and-render-multiple-forms-test
  (is (= [{:project "foo"} {:status :green}]
         (md/read-edn-forms "test-resources/multi.edn")))
  (is (= [{:a 1} {:b 2}]
         (data-io/read-edn-forms-reader (StringReader. "{:a 1}\n{:b 2}") md/default-options)))
  (is (= "## Form 1\n\n### Project\n\nfoo\n\n## Form 2\n\n### Status\n\n`:green`\n"
         (md/render-file "test-resources/multi.edn"))))

(deftest tagged-literal-test
  (is (str/includes? (md/render-file "test-resources/tagged.edn")
                     "`#my/tag {:a 1}`"))
  (let [rendered (md/render-file "test-resources/multi-tagged.edn")]
    (is (str/includes? rendered "`#my/tag {:a 1}`"))
    (is (str/includes? rendered "`#other/tag [1 2]`"))))

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
                        (md/render-file "test-resources/invalid.edn")))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"Could not read EDN file"
                        (md/render-file "test-resources/trailing-invalid.edn")))
  (let [out (jio/file "target/test-output/empty.edn")]
    (.mkdirs (.getParentFile out))
    (spit out "")
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"EDN input is empty"
                          (md/render-file "target/test-output/empty.edn")))))
