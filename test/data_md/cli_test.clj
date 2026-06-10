(ns data-md.cli-test
  (:require [clojure.java.shell :as shell]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [data-md.cli :as cli]))

(deftest parse-args-test
  (is (= {:opts {:title "deps.edn" :max-depth 3 :max-collection-size 50}
          :table? true
          :help? false
          :input "in.edn"
          :output "out.md"}
         (cli/parse-args ["--table"
                          "--title" "deps.edn"
                          "--max-depth" "3"
                          "--max-items" "50"
                          "in.edn"
                          "out.md"])))
  (is (:help? (cli/parse-args ["--help"])))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"Unknown flag"
                        (cli/parse-args ["--wat"]))))

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
