# Voidscape macOS Getting Started

The supported macOS workflow is documented in `docs/DEVELOPMENT.md`. Install Java and Ant as described there, then use the wrapper scripts from the repository root:

```bash
scripts/build.sh
scripts/run-server.sh
scripts/run-client.sh
```

For launcher, Android, web client, and deployment tasks, use the matching `scripts/*.sh` entry point rather than calling build tools directly.
