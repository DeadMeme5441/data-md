(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'io.github.deadmeme5441/data-md)
(def version "0.1.0")
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-file (format "target/%s-%s.jar" (name lib) version))

(defn clean
  "Delete build artifacts."
  [_]
  (b/delete {:path "target"}))

(defn jar
  "Build a jar in target/."
  [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src"] :target-dir class-dir})
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis basis
                :src-dirs ["src"]
                :scm {:url "https://github.com/DeadMeme5441/data-md"}
                :pom-data [[:licenses
                            [:license
                             [:name "EPL-2.0"]
                             [:url "https://www.eclipse.org/legal/epl-2.0/"]]]]})
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
