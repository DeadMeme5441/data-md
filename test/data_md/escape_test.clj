(ns data-md.escape-test
  (:require [clojure.test :refer [deftest is]]
            [data-md.escape :as escape]))

(deftest escape-text-test
  (is (= "\\*x\\*" (escape/escape-text "*x*")))
  (is (= "\\_x\\_" (escape/escape-text "_x_")))
  (is (= "\\<https://example.com?q=a\\_b\\>"
         (escape/escape-text "<https://example.com?q=a_b>")))
  (is (= "\\# heading" (escape/escape-text "# heading")))
  (is (= "\\- item" (escape/escape-text "- item")))
  (is (= "\\+ item" (escape/escape-text "+ item")))
  (is (= "1\\. item" (escape/escape-text "1. item")))
  (is (= "\\---" (escape/escape-text "---")))
  (is (= "\\<b\\>x\\</b\\>" (escape/escape-text "<b>x</b>"))))

(deftest heading-test
  (is (= "\\*danger\\*" (escape/escape-heading "*danger*")))
  (is (= "A B" (escape/escape-heading "A\nB")))
  (is (= "_" (escape/escape-heading ""))))

(deftest code-span-test
  (is (= "`:x`" (escape/code-span ":x")))
  (is (= "``a`b``" (escape/code-span "a`b")))
  (is (= "`` `edge` ``" (escape/code-span "`edge`"))))

(deftest fenced-code-test
  (is (= "```clojure\n{:a 1}\n```"
         (escape/fenced-code "{:a 1}" {:code-language "clojure"})))
  (is (= "````clojure\n{:doc \"contains ``` inside\"}\n````"
         (escape/fenced-code "{:doc \"contains ``` inside\"}"
                             {:code-language "clojure"})))
  (is (= "~~~bad`lang\n{:a 1}\n~~~"
         (escape/fenced-code "{:a 1}" {:code-language "bad`lang"}))))

(deftest table-cell-test
  (is (= "a\\|b" (escape/escape-table-cell "a|b" {:newline-in-table-cell "<br>"})))
  (is (= "a<br>b" (escape/escape-table-cell "a\nb" {:newline-in-table-cell "<br>"})))
  (is (= "a / b" (escape/escape-table-cell "a\nb" {:newline-in-table-cell " / "}))))
