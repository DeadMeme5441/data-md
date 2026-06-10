(ns build
  (:require [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as deps-deploy]))

(def lib 'net.clojars.deadmeme5441/data-md)
(def version (or (System/getenv "RELEASE_VERSION") "0.2.0"))
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-file (format "target/%s-%s.jar" (name lib) version))
(def pom-file
  (format "%s/META-INF/maven/%s/%s/pom.xml"
          class-dir
          (namespace lib)
          (name lib)))
(def pom-data
  [[:description "Render Clojure data and EDN files as readable GitHub-Flavored Markdown."]
   [:url "https://github.com/DeadMeme5441/data-md"]
   [:licenses
    [:license
     [:name "MIT"]
     [:url "https://opensource.org/license/mit"]]]
   [:developers
    [:developer
     [:id "DeadMeme5441"]]]
   [:scm
    [:url "https://github.com/DeadMeme5441/data-md"]
    [:connection "scm:git:https://github.com/DeadMeme5441/data-md.git"]
    [:developerConnection "scm:git:ssh://git@github.com/DeadMeme5441/data-md.git"]]])

(defn clean
  "Delete build artifacts."
  [_]
  (b/delete {:path "target"}))

(defn jar
  "Build a jar in target/."
  [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src"] :target-dir class-dir})
  (b/copy-file {:src "LICENSE" :target (str class-dir "/LICENSE")})
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis basis
                :src-dirs ["src"]
                :pom-data pom-data})
  (b/jar {:class-dir class-dir :jar-file jar-file}))

(defn install
  "Install the jar into the local Maven repository."
  [_]
  (jar nil)
  (b/install {:basis basis
              :lib lib
              :version version
              :jar-file jar-file
              :class-dir class-dir}))

(defn deploy
  "Build and deploy the jar to Clojars."
  [_]
  (jar nil)
  (deps-deploy/deploy {:installer :remote
                       :sign-releases? false
                       :pom-file pom-file
                       :artifact jar-file}))
