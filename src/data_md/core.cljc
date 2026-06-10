(ns data-md.core
  (:require #?@(:clj [[data-md.io :as data-io]])
            [data-md.render :as render]
            [data-md.table :as table])
  #?(:clj (:import [java.io Reader])))

(def default-options
  "Default options used by all public data-md functions."
  render/default-options)

(defn render
  "Render a Clojure/EDN value as GitHub-Flavored Markdown.

  Returns a Markdown string."
  ([data] (render data {}))
  ([data opts]
   (render/render-document data opts)))

(defn render-forms
  "Render one or more top-level Clojure/EDN values as one Markdown document.

  A single value renders exactly like render. Multiple values are grouped under
  numbered form sections."
  ([forms] (render-forms forms {}))
  ([forms opts]
   (render/render-forms-document forms opts)))

(defn render-table
  "Render row data as a GitHub-Flavored Markdown pipe table.

  Rows may be a sequence of maps. A sequence of vectors is supported when
  :columns is supplied."
  ([rows] (render-table rows {}))
  ([rows opts]
   (let [opts (render/normalize-options opts)]
     (table/render-table* rows opts))))

#?(:clj
   (defn read-edn-forms
     "Read all EDN values from a java.io.Reader or file path."
     ([source] (read-edn-forms source {}))
     ([source opts]
      (let [opts (render/normalize-options opts)]
        (if (instance? Reader source)
          (data-io/read-edn-forms-reader source opts)
          (data-io/read-edn-forms-file source opts))))))

#?(:clj
   (defn render-file
     "Read EDN values from path and render them as Markdown."
     ([path] (render-file path {}))
     ([path opts]
      (let [opts (render/normalize-options opts)]
        (render/render-forms-document (data-io/read-edn-forms-file path opts) opts)))))

#?(:clj
   (defn write-file!
     "Read EDN from edn-path, render Markdown, write md-path, and return md-path."
     ([edn-path md-path] (write-file! edn-path md-path {}))
     ([edn-path md-path opts]
      (let [markdown (render-file edn-path opts)]
        (data-io/write-markdown-file! md-path markdown)))))

#?(:cljs
   (defn read-edn-forms
     "File I/O is available on the JVM and Babashka only."
     [& _]
     (throw (js/Error. "data-md.core/read-edn-forms is not available in ClojureScript"))))

#?(:cljs
   (defn render-file
     "File I/O is available on the JVM and Babashka only."
     [& _]
     (throw (js/Error. "data-md.core/render-file is not available in ClojureScript"))))

#?(:cljs
   (defn write-file!
     "File I/O is available on the JVM and Babashka only."
     [& _]
     (throw (js/Error. "data-md.core/write-file! is not available in ClojureScript"))))
