# Contributing To Voidscape

Voidscape is developed as a focused private-server project. Contributions should preserve the classic RuneScape feel, keep scope tight, and use the existing build/test workflow.

## Before You Start

- Read `AGENTS.md` and the relevant subsystem guide in `docs/subsystems/`.
- Read `docs/DEVELOPMENT.md` for the supported build and run commands.
- Use scripts in `scripts/` instead of ad hoc build commands.
- Keep changes narrow; do not bundle unrelated refactors with feature or bug work.
- For non-trivial changes, update `docs/DIVERGENCE.md`.

## Local Workflow

```bash
scripts/build.sh
scripts/run-server.sh
scripts/run-client.sh
```

For game interaction, use `tools/voidbot/voidbot`. For UI verification, use the AI workbench described in `docs/subsystems/ai-workbench.md`.

## Pull Requests And Patches

- Work from a short, descriptive branch name.
- Use imperative commit messages that explain why the change exists.
- Include focused tests or smoke evidence for the behavior touched.
- Do not commit runtime artifacts, logs, generated jars, database dumps, or local credentials.
- Keep public/account/security wording pointed at Voidscape support channels.

## Support

For account, privacy, or security-sensitive coordination, email `support@voidscape.gg`.
