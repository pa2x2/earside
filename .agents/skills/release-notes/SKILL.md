---
name: release-notes
description: Generate Earside release notes in CHANGELOG.md. Use when asked to run or prepare an Earside release changelog.
---

## Inspect the release

1. Read `versionName` and `versionCode` from `app/build.gradle.kts`. Require a stable semantic version
   such as `1.1.0` and derive the future tag `v1.1.0`; stop if the version is missing or not a stable
   semantic version. If `CHANGELOG.md` already has a section for it, or the tag exists on `origin`,
   tell the user the version needs a bump (and `versionCode` an increment) before preparing notes.
2. Read `CHANGELOG.md` and compare the checked-out branch with local `main`.

## Changelog writing rules

1. Verify final behavior in the relevant source and tests before claiming an outcome.
2. Build a shortlist of release-note candidates by user-facing outcome, not by commit. Combine related
   commits into one outcome and discard duplicate, superseded, reverted, or implementation-only work.
   A large commit range may legitimately produce only a few bullets. Classify each outcome by its
   final behavior rather than the commit subject. Omit follow-up fixes that merely complete, correct,
   or safeguard a feature introduced in the same release range.
3. Omit by default:
   - documentation, comments, formatting, lint, and typo-only changes;
   - test additions, test fixes, fixtures, and test infrastructure;
   - refactors, renames, code cleanup, dependency updates, build/CI/release plumbing, and developer
     tooling;
   - internal APIs and implementation details with no verified effect on users;
   - intermediate fixes whose final released behavior is unchanged, and fixes for bugs introduced and
     corrected entirely within the same release range.
   Changes merged from upstream CAPod are user-facing outcomes like any other: include them when they
   change what Earside users see.
4. Use a Keep a Changelog section named `[X.Y.Z]` with the current date for a numbered release, or an
   undated `[Unreleased]` section for pending changes. Use only the applicable headings from this
   mapping:

   - `✨ Added` - for new features.
   - `🔄 Changed` - for changes in existing functionality.
   - `🧩 Improved` - for enhancement in existing functionality.
   - `🗑️ Removed` - for now removed features.
   - `🐛 Fixed` - for any bug fixes.
   - `🧩 Other` - for technical stuff.
   - `⚡️ Performance` - for optimizations in existing functionality.

   Use this shape:

   ```markdown
   ## [X.Y.Z] - YYYY-MM-DD

   ### ✨ Added

   - A distinct outcome for users.
   ```

5. Use the `unslop` skill to write notes. If it is unavailable, describe outcomes in concise, natural,
   polished language. Let each heading provide the category context. Keep the tone factual rather than
   promotional, and make each bullet understandable without commit or implementation context.
6. Preserve released entries and avoid repeating documented outcomes. Keep `[Unreleased]` first and
   numbered releases in descending version order. When preparing a release, move its applicable
   unreleased notes into the release section. If there are no new outcomes, leave the file unchanged
   and report that it was reviewed; do not create empty sections.

## Update CHANGELOG.md

1. Inspect `git log main..HEAD` and `git diff main...HEAD`. Trace representative runtime and
   presentation paths for user-facing outcomes.
2. Update `CHANGELOG.md` for the configured version using the writing rules above. Add or update the
   version's link definition at the bottom of the file:
   `[X.Y.Z]: https://github.com/pa2x2/earside/releases/tag/vX.Y.Z`.
3. If the range includes a merge from upstream CAPod, name the upstream version it brought in, right
   under the release heading:

   ```markdown
   Based on [CAPod <version>](https://github.com/d4rken-org/capod/releases/tag/<version>)
   ```

   Find the version from the merge commit's subject (`Merge upstream CAPod <tag>`), or for a `main`
   merge, from the nearest `refs/upstream-tags/` tag at or before the merged commit.

## Guidance

- Derive release-note content only from the checked-out branch's changes relative to local `main`.
  The `vX.Y.Z` tag is a future release identifier for the changelog link, not a comparison endpoint.
- `release.yml` releases a version as soon as `main` has a `## [X.Y.Z]` section for it. Writing that
  section on `upcoming` is safe; merging it into `main` publishes a draft release.
- Create a temporary file under `.claude/tmp/` to track findings while exploring.
