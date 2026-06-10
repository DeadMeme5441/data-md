# data-md

Render Clojure data and EDN files as readable GitHub-Flavored Markdown.

[![Clojars Project](https://img.shields.io/clojars/v/net.clojars.deadmeme5441/data-md.svg)](https://clojars.org/net.clojars.deadmeme5441/data-md)

`data-md` is a small Clojure library for turning ordinary values into Markdown reports, tables, issue comments, README snippets, and GitHub Actions summaries. It emits Markdown directly, has no runtime Markdown parser, and keeps the core API intentionally small.

## Installation

Use the Clojars coordinate:

```clojure
{:deps {net.clojars.deadmeme5441/data-md {:mvn/version "0.2.0"}}}
```

For local development or local Maven installation:

```bash
clojure -T:build install
```

The library runtime depends only on Clojure.

## Compatibility

- Clojure: `render`, `render-table`, `render-file`, `write-file!`, and CLI.
- ClojureScript: `render` and `render-table` from the pure `.cljc` renderer namespaces.
- Babashka: `render`, `render-table`, `render-file`, `write-file!`, and CLI.

File I/O is JVM/Babashka-only. ClojureScript callers should parse data in their host environment and call `render` or `render-table`.

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

(md/render-forms [data-1 data-2])
(md/render-forms [data-1 data-2] opts)

(md/render-table rows)
(md/render-table rows opts)

(md/read-edn-forms "input.edn")
(md/read-edn-forms reader opts)

(md/render-file "deps.edn")
(md/render-file "deps.edn" opts)

(md/write-file! "deps.edn" "docs/deps.md")
(md/write-file! "deps.edn" "docs/deps.md" opts)
```

Render functions return strings. `read-edn-forms` returns a vector of EDN values.
`write-file!` writes Markdown and returns the output path.

## Render EDN Files

```clojure
(md/render-file "deps.edn" {:title "deps.edn"})
(md/write-file! "deps.edn" "docs/deps.md" {:title "deps.edn"})
```

Files may contain one or more top-level EDN forms. A single form renders exactly
like `render`. Multiple forms render under numbered sections:

```clojure
{:project "foo"}
{:status :green}
```

renders as:

```markdown
## Form 1

### Project

foo

## Form 2

### Status

`:green`
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
| `:final-newline?` | `true` | Append one final newline |
| `:line-ending` | `"\n"` | Output line ending |
| `:max-depth` | `4` | Depth where nested data falls back to fenced code |
| `:max-collection-size` | `100` | Maximum rendered items before truncation |
| `:map-style` | `:auto` | `:auto`, `:sections`, `:list`, or `:code-block` |
| `:seq-style` | `:auto` | `:auto`, `:table`, `:list`, or `:code-block` |
| `:set-style` | `:list` | `:list` or `:code-block` |
| `:record-style` | `:map-with-type` | `:map-with-type`, `:map`, or `:code-block` |
| `:columns` | `nil` | Explicit table columns |
| `:missing-cell` | `""` | Table cell text for absent values |
| `:newline-in-table-cell` | `"<br>"` | Replacement for newlines inside table cells |
| `:table-align` | `nil` | `:left`, `:center`, `:right`, or a column alignment map |
| scalar style opts | mixed | `:plain` or `:code` for strings, keywords, symbols, numbers, booleans, nil, chars, instants, UUIDs |
| `:code-language` | `"clojure"` | Fenced code block info string |
| `:key-label-fn` | `nil` | Custom map-key label function |
| `:value-renderer` | `nil` | Custom value renderer hook |
| `:cell-renderer` | `nil` | Custom table cell renderer hook |
| `:sort-key-fn` | `nil` | Custom deterministic sort key for unordered maps and sets |
| `:include-metadata?` | `false` | Include metadata before rendered values |
| `:readers` | `{}` | EDN tagged-literal readers for file/CLI input |
| `:default-reader` | `:tagged-literal` | Unknown EDN tag handling; use `nil` to reject unknown tags |

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
clojure -M -m data-md.cli --columns name,lang --table rows.edn
clojure -M -m data-md.cli --align right --table rows.edn
clojure -M -m data-md.cli --align-col lang=center --table rows.edn
clojure -M -m data-md.cli --max-depth 3 --max-items 50 input.edn
clojure -M -m data-md.cli --map-style list --nil-style plain input.edn
clojure -M -m data-md.cli --line-ending crlf --no-final-newline input.edn
clojure -M -m data-md.cli --version
```

Use `-` for stdin or stdout:

```bash
cat input.edn | clojure -M -m data-md.cli - -
```

CLI failures use exit code `2` for invalid CLI usage and `1` for read, render,
or write failures.

## Babashka

The runtime namespaces are Babashka-compatible:

```bash
bb -cp src -m data-md.cli test-resources/simple.edn
bb -cp src -e "(require '[data-md.core :as md]) (println (md/render {:a 1}))"
```

Repo tasks:

```bash
bb test
bb cljs-test
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
clojure -M:cljs-test
bb test
bb consumer-smoke
```

Build a jar:

```bash
clojure -T:build jar
```

Install locally:

```bash
clojure -T:build install
```

Deploy to Clojars:

```bash
clojure -T:build deploy
```

Deployment reads `CLOJARS_USERNAME` and `CLOJARS_PASSWORD` from the environment. The release workflow publishes when a `v*` tag is pushed.

## License

MIT. See [LICENSE](LICENSE).
