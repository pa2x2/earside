@../AGENTS.md

## Claude Code rules

These load when a matching file is read (`paths:` frontmatter):

| Rule | Loads for |
|------|-----------|
| `.claude/rules/architecture-aap-protocol.md` | `**/aap/**`, conversation reaction |
| `.claude/rules/code-style.md` | Kotlin/Compose sources in `main/`, `foss/`, `debug/` |
| `.claude/rules/testing.md` | `app/src/test/`, `testFoss/` |
| `.claude/rules/localization.md` | `**/res/values/strings.xml` (base locale) |
