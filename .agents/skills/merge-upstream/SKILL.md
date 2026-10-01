---
name: merge-upstream
description: Inspect, plan, execute, and validate an ancestry-preserving merge of upstream CAPod main or a specified upstream version tag into the Earside fork's current branch. Use when asked to merge, sync, or port changes from the `upstream` Git remote, including executing an approved upstream merge plan.
---

# Merge upstream

Use two phases: inspect and plan first, then execute only after explicit approval. Preserve upstream Git
ancestry with a real merge; do not implement upstream changes as unrelated cherry-picks. The merge
source defaults to the `main` branch of the `upstream` remote (`d4rken-org/capod`). When the user
provides an upstream version tag, use that exact tag instead. The merge base is whichever branch is
currently checked out, normally `upcoming`.

Upstream tags are fetched into `refs/upstream-tags/`, not `refs/tags/`: the clone sets
`remote.upstream.tagOpt=--no-tags` and the refspec `+refs/tags/*:refs/upstream-tags/*`, because
upstream's `v1.0.0` and later tags would collide with Earside's own release tags. Never fetch upstream
tags into `refs/tags/` and never push them to `origin`.

## Inspect and plan

Remain in Plan mode when the active surface supports it. Keep the worktree and branches unchanged.
Fetching from `upstream` is the permitted metadata-only exception.

1. Verify the repository root, current branch, worktree state, and the URLs for `origin`
   (`pa2x2/earside`) and `upstream` (`d4rken-org/capod`). Confirm the upstream tag configuration above
   is still in place. Do not stash, discard, overwrite, or incorporate unrelated changes. Record the
   current branch name and its HEAD commit as the fork base.
2. Select and resolve the merge source:
   - With no version tag, run `git fetch upstream` and resolve `refs/remotes/upstream/main^{commit}`.
   - With a version tag, require exactly one non-empty tag argument and use it literally. Run
     `git fetch upstream`, verify that `refs/upstream-tags/<tag>` exists and matches
     `git ls-remote --tags upstream refs/tags/<tag>`, and resolve `refs/upstream-tags/<tag>^{commit}`.
     Stop if the tag is absent or the two disagree.
   Record the selector, its exact name, and the resolved target SHA. Compute the target's merge base
   with the fork base.
3. Treat `merge-base(<fork-base>, <target>)..<target>` as the upstream delta and
   `merge-base(<fork-base>, <target>)..<fork-base>` as the fork divergence. Inspect commits, full
   diffs, renamed or deleted files, overlapping paths, and likely conflicts. Use `git merge-tree` for
   conflict forecasting when useful, without starting a merge.
4. Read the "Fork" section of `AGENTS.md`. It lists what Earside removed on purpose and how it differs.
5. Review cleanly mergeable changes as carefully as textual conflicts. Pay particular attention to:
   - files under removed paths: upstream edits to them conflict (modify/delete), and new upstream files
     there merge in silently without any conflict;
   - new translations (`values-<lang>/`), Google Play flavor code, `app-e2e` tests, fastlane and
     Crowdin changes, which all stay removed;
   - the application ID, signing, version, `CHANGELOG.md`, and `.github/workflows/`, which belong to
     the fork;
   - Pro gating: `UpgradeRepoFoss` always reports Pro, and new gates must keep working with that;
   - new upstream links to CAPod's support, wiki, Discord, translation or sponsor pages, which the fork
     removes;
   - user-visible strings that say "CAPod", which become "Earside";
   - changes to upstream's agent docs. `.claude/rules/architecture.md`, `build-commands.md`,
     `commit-guidelines.md`, `pull-requests.md` and `agent-instructions.md` were folded into
     `AGENTS.md`; port relevant upstream edits there by hand. The path-scoped rules that remain in
     `.claude/rules/` merge normally;
   - Gradle, Android, Kotlin, and dependency changes.
6. Group upstream changes by subsystem and classify each group:
   - `direct`: accept the upstream implementation;
   - `adapt`: preserve upstream intent through the fork's changes;
   - `replace`: retain or extend an existing fork equivalent;
   - `skip`: intentionally inapplicable, with a concrete reason;
   - `defer`: requires a separate user decision.
7. Present the plan with:
   - repository state, fork base branch and commit, target selector and exact name, exact target SHA,
     merge base, and comparison ranges;
   - upstream change scope and affected subsystems;
   - a table of classifications, overlapping files, semantic risks, and proposed resolutions;
   - an ordered merge and validation procedure;
   - unresolved decisions, expected conflicts, and explicit exclusions.

Stop after the plan. Require explicit user approval in a later turn before creating a branch, changing
files, starting the merge, or resolving conflicts.

## Execute an approved plan

Proceed only when the thread contains the plan, the user explicitly approved it, material decisions are
resolved, and the active mode permits mutations.

1. Fetch from `upstream` again and resolve the approved source by the same procedure used during
   planning. Stop and re-inspect if it no longer resolves to the approved target SHA, or if the
   current branch or its HEAD commit differs from the approved plan.
2. Protect the active checkout. Never stash or discard user work automatically. Require the worktree
   to be clean, and stop if `upstream-sync/<date>` already exists. Create and switch to
   `upstream-sync/<date>` from the current branch HEAD, where `<date>` is today in `YYYY-MM-DD` form:

   ```bash
   git switch -c upstream-sync/$(date +%F)
   ```

   Read `AGENTS.md` again after switching branches.
3. Start an ancestry-preserving merge without committing:

   ```bash
   git merge --no-ff --no-commit <approved-target-sha>
   ```

4. Resolve textual and semantic conflicts according to the approved plan. Do not use blanket `ours` or
   `theirs` strategies.
5. Re-apply the fork's removals. Resolve modify/delete conflicts on removed paths with `git rm`, then
   find files the merge added under removed paths and remove them too:

   ```bash
   git diff --cached --name-only --diff-filter=A HEAD
   ```

   Check the result against the removed-paths list in `AGENTS.md`, including any new `values-<lang>/`
   directory.
6. Inspect the complete staged and unstaged result, including files Git merged without conflicts.
   Confirm that skipped or replaced upstream behavior is intentional and documented in the final
   report.
7. Run the validation in `AGENTS.md`, plus `./gradlew lintVitalFossRelease`, which CI also gates on.
8. Fix in-scope failures and rerun affected checks. If blocked, preserve the active merge state and
   report it; do not abort and discard resolutions automatically.
9. Commit only after validation succeeds. Use `Merge upstream CAPod main` as the merge subject for a
   `main` merge, or `Merge upstream CAPod <tag>` for a tagged merge. Do not push, open a pull request,
   tag a release, switch back to the previous branch, or delete the sync branch unless the user
   separately requests it.
10. Report the sync branch, fork base branch, target selector and SHA, merge commit, classifications
    that required adaptation or exclusion, re-applied removals, validation results, and anything not
    tested.
