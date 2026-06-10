# data-md

Render Clojure data and EDN files as readable GitHub-Flavored Markdown.

`data-md` is a small Clojure library for turning ordinary values into Markdown reports, tables, issue comments, README snippets, and GitHub Actions summaries. It emits Markdown directly, has no runtime Markdown parser, and keeps the core API intentionally small.

## Installation

Use the Git dependency from this repository:

```clojure
{:deps {io.github.deadmeme5441/data-md
        {:git/url "https://github.com/DeadMeme5441/data-md"
         :git/tag "v0.1.0"
         :git/sha "<release-sha>"}}}
```

For local development or local Maven installation:

```bash
clojure -T:build install
```

The library runtime depends only on Clojure.

## Quick Start

```clojure
(require '[data-md.core :as md])

(md/render {:project "foo"
            :status :green
            :deps [{:lib 'a :version "1.0"}
                   {:lib 'b :version "2.0"}]})
```

Output:

```markdown
## Project

foo

## Status

`:green`

## Deps

| Lib | Version |
| --- | --- |
| `a` | 1.0 |
| `b` | 2.0 |
```

## Public API

```clojure
(md/render data)
(md/render data opts)

(md/render-table rows)
(md/render-table rows opts)

(md/render-file "deps.edn")
(md/render-file "deps.edn" opts)

(md/write-file! "deps.edn" "docs/deps.md")
(md/write-file! "deps.edn" "docs/deps.md" opts)
```

All functions return strings except `write-file!`, which writes Markdown and returns the output path.

## Render EDN Files

```clojure
(md/render-file "deps.edn" {:title "deps.edn"})
(md/write-file! "deps.edn" "docs/deps.md" {:title "deps.edn"})
```

Unknown EDN tagged literals are preserved by default:

```clojure
#my/tag {:a 1}
```

renders as:

```markdown
`#my/tag {:a 1}`
```

## Render Tables

```clojure
(md/render-table [{:name "Ada" :role :admin}
                  {:name "Rich" :language "Clojure"}])
```

Output:

```markdown
| Name | Role | Language |
| --- | --- | --- |
| Ada | `:admin` |  |
| Rich |  | Clojure |
```

Vector rows are supported when columns are supplied:

```clojure
(md/render-table [["Ada" "Clojure"]
                  ["Rich" "Clojure"]]
                 {:columns [:name :lang]})
```

## What It Renders

`data-md` handles:

- scalars: nil, booleans, strings, chars, keywords, symbols, numbers, UUIDs, instants, tagged literals
- maps: top-level sections, nested key-value lists, nested subsections
- sequences: tables for row-shaped data, bullet lists for mixed values
- sets: deterministic sorted bullet lists
- records: type line plus field rendering
- deep or unknown values: fenced Clojure code or inline printable fallback

Large and lazy collections are bounded by `:max-collection-size`, so infinite sequences are truncated instead of fully realized.

## Options

Common options:

| Option | Default | Meaning |
| --- | --- | --- |
| `:title` | `nil` | Optional `#` document title |
| `:heading-level` | `2` | Heading level for top-level map sections |
| `:max-depth` | `4` | Depth where nested data falls back to fenced code |
| `:max-collection-size` | `100` | Maximum rendered items before truncation |
| `:columns` | `nil` | Explicit table columns |
| `:table-align` | `nil` | `:left`, `:center`, `:right`, or a column alignment map |
| `:key-label-fn` | `nil` | Custom map-key label function |
| `:value-renderer` | `nil` | Custom value renderer hook |
| `:cell-renderer` | `nil` | Custom table cell renderer hook |
| `:include-metadata?` | `false` | Include metadata before rendered values |

Unknown options are rejected with `ex-info` so typos fail early.

Custom value rendering:

```clojure
(md/render {:coverage 0.9234}
           {:value-renderer
            (fn [v _ctx]
              (when (and (number? v) (<= 0 v 1))
                (format "%.2f%%" (* 100 v))))})
```

Output:

```markdown
## Coverage

92.34%
```

## CLI

Run with Clojure:

```bash
clojure -M -m data-md.cli input.edn
clojure -M -m data-md.cli input.edn output.md
clojure -M -m data-md.cli --title "deps.edn" input.edn output.md
clojure -M -m data-md.cli --table rows.edn
clojure -M -m data-md.cli --max-depth 3 --max-items 50 input.edn
```

Use `-` for stdin or stdout:

```bash
cat input.edn | clojure -M -m data-md.cli - -
```

## Babashka

The runtime namespaces are Babashka-compatible:

```bash
bb -cp src -m data-md.cli test-resources/simple.edn
bb -cp src -e "(require '[data-md.core :as md]) (println (md/render {:a 1}))"
```

Repo tasks:

```bash
bb test
bb render test-resources/simple.edn
```

## Design Philosophy

`data-md` optimizes for readable, deterministic Markdown, not perfect round-tripping. It uses sections, lists, tables, code spans, and fenced code blocks where they make the value easier to inspect.

Tables intentionally keep cells inline because GFM tables do not support arbitrary block Markdown inside cells.

## Non-Goals

`data-md` is not:

- a Markdown parser
- an HTML renderer
- a notebook system
- a schema documentation generator
- a static site generator
- a replacement for Clerk, Kindly, Codox, or Markdown parser libraries

## Development

Run tests:

```bash
clojure -M:test
bb test
```

Build a jar:

```bash
clojure -T:build jar
```

Install locally:

```bash
clojure -T:build install
```

## License

EPL-2.0. See [LICENSE](LICENSE).
