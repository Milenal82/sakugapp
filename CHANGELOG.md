# Changelog

## 1.2.1 / code 12 — development

This is a development update, not a new public release. The package name and existing signing certificate are unchanged.

- Add portable `sakugapp.library` schema 3 backups with explicit folder keys, covers, ordering, multiple post memberships, tag favorites, and standalone notes. Keep legacy v1/v2 imports; preview counts and conflict choices before an atomic merge, then report added/skipped/failed items. Existing notes are retained by default.
- Use consistent wrapping search-tool tiles while retaining queries, suggestions, history, and tool state.
- Group browsing history by relative date, with a compact count/recording header and separators. Group existing settings into rounded sections without changing preference keys.
- Rewrite the README in Chinese, English, and Japanese with shared navigation and current build/backup guidance.
- Replace the launcher artwork with the supplied pastel icon, including legacy density assets and an adaptive launcher resource.

Backup details: [format and compatibility](docs/backup-format.md). The current update's verification results are recorded in [the test report](docs/test-report.md); prior runs are not evidence for new changes.

## 1.2.0 / code 11 — development

- Add safe Artist/Source/Post ID download names, pull-to-refresh with request-generation protection, French/Spanish/Russian/Korean UI resources, six-column grids, local browsing history, tag favorites, and independent tag notes.
- Follow up with explicit episode naming, combined-search history drafts, proper search back navigation and list restoration, and grouped advanced player controls.
- Preserve existing folders, settings, downloads, and legacy JSON imports. See [development details](docs/development-1.2.0.md) and [follow-up verification](docs/incremental-search-download-controls.md).

## 1.1.0 — released

- Consolidate continuous browsing and search tools, native details, advanced player controls, local multi-folder favorites, and theme/layout improvements.
- See the [release notes](release-notes/v1.1.0.md) and [release checks](docs/release-1.1.0.md).

## 1.0.0 — released

- First signed release. See the [release notes](release-notes/v1.0.0.md).
