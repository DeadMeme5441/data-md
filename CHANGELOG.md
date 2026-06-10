# Changelog

## 0.2.0 - 2026-06-10

Production readiness pass:

- Add `render-forms` and `read-edn-forms` for multi-form EDN streams.
- Make `render-file` and the CLI render multiple top-level EDN forms as numbered sections.
- Expand the CLI with version, table column/alignment, line-ending, final-newline, code-language, and style flags.
- Remove placeholder options from the public option surface and validate table alignment maps.
- Harden EDN input errors, Markdown escaping coverage, lazy/code-block truncation, and jar consumer validation.
- Switch project licensing to MIT.

## 0.1.0 - 2026-06-10

Initial MVP:

- Render Clojure and EDN values as GitHub-Flavored Markdown.
- Render table-shaped row data as GFM pipe tables.
- Read EDN files and write Markdown files.
- Provide a minimal zero-runtime-dependency CLI.
- Support Clojure, ClojureScript, and Babashka for pure rendering.
- Include Clojars deploy wiring, release workflow, golden-output, and build validation.
