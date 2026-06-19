# Voidscape Content Studio

Local browser GUI for the existing custom-content pipeline.

```bash
scripts/content-studio.sh
```

The server binds to `127.0.0.1` by default and opens no external network
surface. This first slice shows allocation/validation state and scaffolds
content packs through the same `voidscape_content.manifest.scaffold_pack`
helper used by `scripts/content.sh new`.

