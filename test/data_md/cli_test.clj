(ns data-md.cli-test
  (:require [clojure.java.io :as jio]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [data-md.cli :as cli]))

(deftest parse-args-test
  (is (= {:opts {:title "deps.edn" :max-depth 3 :max-collection-size 50}
          :table? true
          :help? false
          :version? false
          :input "in.edn"
          :output "out.md"}
         (cli/parse-args ["--table"
                          "--title" "deps.edn"
                          "--max-depth" "3"
                          "--max-items" "50"
                          "in.edn"
                          "out.md"])))
  (is (= {:opts {:columns [:name :lang]
                 :table-align {:score :right}
                 :line-ending "\r\n"
                 :final-newline? false
                 :map-style :list
                 :nil-style :plain}
          :table? true
          :help? false
          :version? false
          :input "in.edn"
          :output nil}
         (cli/parse-args ["--table"
                          "--columns" "name,lang"
                          "--align-col" "score=right"
                          "--line-ending" "crlf"
                          "--no-final-newline"
                          "--map-style" "list"
                          "--nil-style" "plain"
                          "in.edn"])))
  (is (:help? (cli/parse-args ["--help"])))
  (is (:version? (cli/parse-args ["--version"])))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"Unknown flag"
                        (cli/parse-args ["--wat"])))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"Cannot combine"
                        (cli/parse-args ["--align" "left" "--align-col" "a=right" "in.edn"]))))

(deftest clojure-cli-smoke-test
  (let [{:keys [exit out err]} (shell/sh "clojure" "-M" "-m" "data-md.cli"
                                         "test-resources/simple.edn")]
    (is (= 0 exit) err)
    (is (str/includes? out "## Project"))))

(deftest babashka-cli-smoke-test
  (let [{:keys [exit out err]} (shell/sh "bb" "-cp" "src" "-m" "data-md.cli"
                                         "test-resources/simple.edn")]
    (is (= 0 exit) err)
    (is (str/includes? out "## Project"))))

(deftest clojure-cli-version-test
  (let [{:keys [exit out err]} (shell/sh "clojure" "-M" "-m" "data-md.cli"
                                         "--version")]
    (is (= 0 exit) err)
    (is (= "0.2.0\n" out))))

(deftest clojure-cli-stdin-stream-test
  (let [{:keys [exit out err]} (shell/sh "clojure" "-M" "-m" "data-md.cli"
                                         "-" "-"
                                         :in "{:a 1}\n{:b 2}")]
    (is (= 0 exit) err)
    (is (str/includes? out "## Form 1"))
    (is (str/includes? out "### B"))))

(deftest clojure-cli-output-and-table-flags-test
  (let [out-file (jio/file "target/test-output/cli-table.md")
        _ (when (.exists out-file) (.delete out-file))
        result (shell/sh "clojure" "-M" "-m" "data-md.cli"
                         "--table"
                         "--columns" "name,lang"
                         "--align-col" "lang=center"
                         "test-resources/table-rows.edn"
                         "target/test-output/cli-table.md")]
    (is (= 0 (:exit result)) (:err result))
    (let [rendered (slurp out-file)]
      (is (str/includes? rendered "| Name | Lang |"))
      (is (str/includes? rendered "| --- | :---: |")))))

(deftest clojure-cli-error-exit-tests
  (let [bad-flag (shell/sh "clojure" "-M" "-m" "data-md.cli" "--wat")
        bad-edn (shell/sh "clojure" "-M" "-m" "data-md.cli"
                          "test-resources/invalid.edn")]
    (is (= 2 (:exit bad-flag)))
    (is (str/includes? (:err bad-flag) "Unknown flag"))
    (is (= 1 (:exit bad-edn)))
    (is (str/includes? (:err bad-edn) "Could not read EDN file"))))
