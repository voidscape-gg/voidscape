# Voidscape Content Packs

Custom Voidscape content starts here before it is wired into the server,
client, cache, drops, shops, or beta launcher package.

Use the canonical helper:

```bash
scripts/content.sh new item void_crystal --name "Void crystal"
scripts/content.sh report
scripts/content.sh validate
```

Each pack lives at `content/custom/<slug>/` and keeps the design, art prompts,
source images, working files, final assets, and rollout notes together. The
actual game integration still happens in the normal OpenRSC files until the
tooling graduates each content type to full registration.
