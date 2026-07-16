# Christmas cracker reel

- Christmas cracker `575` self-opens; player/banker use paths never award.
- Server odds are exact: 60% nothing, 20% weighted party hats, 20% weighted holiday rares.
- The reward is committed before the client receives `@vscracker@v1|rollId|category|itemId|seed`.
- Only senderless QUEST server messages may invoke the reel; sender-bearing chat must never spoof it.
- Client `10129` consumes the envelope and runs a visual-only deterministic reel; it never claims a reward.
- Winner is reel index `43`; Workbench must prove near-stop centers `42` and reveal centers `43` on the authoritative item.
- ESC/Back/X fast-forwards once, then closes; every platform must swallow pointer/key input beneath the modal.
- PC/Android use packaged WAVs; TeaVM uses the same five WAVs through an unlocked, cache-tokenized Web Audio path.
- Canonical gameplay QA is `tests/christmas-cracker.sh`; remove the noted probe with `::ritem ... true` so the last unnoted cracker remains.
