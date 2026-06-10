(ns data-md.render-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [data-md.core :as md]
            [data-md.labels :as labels]))

(defrecord User [id name active?])

(deftest option-validation-test
  (is (= (:title md/default-options) nil))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"Invalid data-md option"
                        (md/render {:a 1} {:unknown true})))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"Invalid data-md option"
                        (md/render {:a 1} {:max-depth -1})))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"Invalid data-md option"
                        (md/render {:a 1} {:max-collection-size 0}))))

(deftest scalar-rendering-test
  (is (= "`nil`\n" (md/render nil)))
  (is (= "`true`\n" (md/render true)))
  (is (= "hello\n" (md/render "hello")))
  (is (= "`\"\"`\n" (md/render "")))
  (is (= "`:a/b`\n" (md/render :a/b)))
  (is (= "`foo/bar`\n" (md/render 'foo/bar)))
  (is (= "`1/3`\n" (md/render 1/3)))
  (is (= "`42N`\n" (md/render 42N)))
  (is (= "`1.2M`\n" (md/render 1.2M))))

(deftest key-label-test
  (is (= "Project Name" (labels/default-key-label :project-name)))
  (is (= "Id" (labels/default-key-label :user/id)))
  (is (= "Project Name" (labels/default-key-label "project_name")))
  (is (= [":user/id" ":order/id"]
         (labels/keys->labels [:user/id :order/id]))))

(deftest map-rendering-test
  (is (= "## Project\n\nfoo\n\n## Status\n\n`:green`\n"
         (md/render {:project "foo" :status :green})))
  (is (= "# Build Report\n\n## Project\n\nfoo\n\n## Status\n\n`:green`\n"
         (md/render {:project "foo" :status :green}
                    {:title "Build Report"})))
  (is (= "## Server\n\n- **Host:** localhost\n- **Port:** `8080`\n"
         (md/render {:server {:host "localhost" :port 8080}})))
  (is (= "## Service\n\n- **Name:** api\n- **Port:** `8080`\n- **Ssl?:** `false`\n"
         (md/render {:service {:name "api" :port 8080 :ssl? false}}))))

(deftest nested-map-with-table-test
  (is (= "## Server\n\n- **Host:** localhost\n\n### Routes\n\n| Method | Path |\n| --- | --- |\n| `:get` | /health |\n| `:post` | /jobs |\n"
         (md/render {:server {:host "localhost"
                              :routes [{:method :get :path "/health"}
                                       {:method :post :path "/jobs"}]}}))))

(deftest sequence-rendering-test
  (is (= "- `:a`\n- `:b`\n" (md/render [:a :b])))
  (is (= "| A |\n| --- |\n| `1` |\n| `2` |\n"
         (md/render [{:a 1} {:a 2}])))
  (is (= "- `:alpha`\n- plain text\n- `{:x 1, :y 2}`\n"
         (md/render [:alpha "plain text" {:x 1 :y 2}])))
  (is (str/includes? (md/render (range) {:max-collection-size 3})
                     "truncated after 3")))

(deftest set-rendering-test
  (is (= "- `:a`\n- `:b`\n"
         (md/render #{:b :a}))))

(deftest depth-fallback-test
  (is (str/includes?
       (md/render {:a {:b {:c {:d 1}}}} {:max-depth 2})
       "```clojure")))

(deftest records-and-metadata-test
  (testing "records include type"
    (let [rendered (md/render (->User 1 "Ada" true))]
      (is (str/includes? rendered "**Type:**"))
      (is (str/includes? rendered "- **Name:** Ada"))))
  (testing "metadata is opt-in"
    (is (not (str/includes? (md/render (with-meta {:a 1} {:m true}))
                            "Metadata")))
    (is (str/includes? (md/render (with-meta {:a 1} {:m true})
                                  {:include-metadata? true})
                       "**Metadata:**"))))

(deftest hooks-test
  (let [percentage (fn [v _]
                     (when (and (number? v) (<= 0 v 1))
                       (format "%.2f%%" (* 100 v))))
        build-label (fn [k]
                      (case k
                        :build/status "Build status"
                        (name k)))]
    (is (= "## Coverage\n\n92.34%\n"
           (md/render {:coverage 0.9234}
                      {:value-renderer percentage})))
    (is (= "## Build status\n\n`:green`\n"
           (md/render {:build/status :green}
                      {:key-label-fn build-label})))))

(deftest deterministic-rendering-test
  (let [value (hash-map :b 2 :a 1)]
    (dotimes [_ 20]
      (is (= (md/render value) (md/render value))))))
