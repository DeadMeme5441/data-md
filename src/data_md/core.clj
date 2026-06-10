(ns data-md.core
  (:require [data-md.io :as data-io]
            [data-md.render :as render]
            [data-md.table :as table]))

(def default-options
  "Default options used by all public data-md functions."
  render/default-options)

(defn render
  "Render a Clojure/EDN value as GitHub-Flavored Markdown.

  Returns a Markdown string."
  ([data] (render data {}))
  ([data opts]
   (render/render-document data opts)))

(defn render-table
  "Render row data as a GitHub-Flavored Markdown pipe table.

  Rows may be a sequence of maps. A sequence of vectors is supported when
  :columns is supplied."
  ([rows] (render-table rows {}))
  ([rows opts]
   (let [opts (render/normalize-options opts)]
     (table/render-table* rows opts))))

(defn render-file
  "Read one EDN value from path and render it as Markdown."
  ([path] (render-file path {}))
  ([path opts]
   (let [opts (render/normalize-options opts)]
     (render/render-document (data-io/read-edn-file path opts) opts))))

(defn write-file!
  "Read EDN from edn-path, render Markdown, write md-path, and return md-path."
  ([edn-path md-path] (write-file! edn-path md-path {}))
  ([edn-path md-path opts]
   (let [markdown (render-file edn-path opts)]
     (data-io/write-markdown-file! md-path markdown))))
