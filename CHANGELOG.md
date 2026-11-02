# Changelog

All notable changes to Kiosk will be documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and version numbers
should follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.0] - 2026-11-02

### Added

- Navigation drawer from a menu button in the search bar, with the feeds and a Library section.
- Save stories (article bar) and single comments (comment sheet); Saved stories, Saved comments,
  and Recently viewed lists, kept in a new local database.
- A saved comment opens its thread at that comment.
- Saved stories open offline from the library's copy; the reader cache no longer evicts them.
- "N new comments" on feed rows for stories opened before.
- Collapse a comment's replies by tapping it; a button jumps to the next top-level comment.
- OP tag on comments by the story's submitter.
- Tap a commenter's name for their karma, account age, about text, and recent stories.
- Swipe a story right to save it and left to hide it (with Undo); each direction can be set to
  Save, Hide, or None.
- Mute sites and title words from a Muted screen or the article menu.
- Search within the library lists.
- Export and import the library as a JSON file.
- Docker build script (`scripts/docker-build.sh`) needing no local JDK or Android SDK.

## [0.1.1] - 2026-09-06

### Added

- GitLab CI and GitHub Actions release-prep workflows for quality, nightly, and tag builds.
- Release signing support through environment variables.

### Fixed

- Prevented comment-sheet scroll jitter by giving the comments list a stable full-height frame. Thanks to [@valonsodev](https://github.com/valonsodev) for the contribution in [PR #62](https://github.com/shapeshed/kiosk/pull/62).

## [0.1.0] - 2026-08-09

### Added

- Initial release baseline for a reader-first Hacker News Android app.
- Six HN feeds, historical search, read/unread styling, and pull-to-refresh.
- Native article reader powered by vendored Mozilla Readability.
- Reader extraction warming around the visible feed list, with memory and disk caches.
- Comments bottom sheet with active-article comment preloading.
- Reader appearance settings, Speed Reader, Read Aloud, and zoomable article images.
- External-app handling for PDFs, X/Twitter, YouTube, and arbitrary links.
