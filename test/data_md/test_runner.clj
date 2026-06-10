(ns data-md.test-runner
  (:require [clojure.test :as test]
            [data-md.cli-test]
            [data-md.escape-test]
            [data-md.golden-test]
            [data-md.io-test]
            [data-md.render-test]
            [data-md.table-test]))

(defn -main [& _]
  (let [{:keys [fail error]} (test/run-tests 'data-md.escape-test
                                             'data-md.render-test
                                             'data-md.table-test
                                             'data-md.io-test
                                             'data-md.cli-test
                                             'data-md.golden-test)]
    (when (pos? (+ fail error))
      (System/exit 1))))
