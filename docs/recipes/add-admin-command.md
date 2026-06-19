# Recipe: add an admin command

Admin / staff commands are dispatched via `CommandTrigger` plugins, similar to other triggers. Many existing commands live in `server/plugins/authentic/commands/`.

The full list of in-game commands is in `Commands.md` at the repo root.

## Files you'll touch

| File | Why |
|---|---|
| `server/plugins/.../commands/<YourCommand>.java` | The command handler |
| `Commands.md` | (recommended) document the new command |

## Steps

1. **Create the plugin** at `server/plugins/.../commands/<YourCommand>.java`. Follow the pattern of an existing command:
   ```java
   public class TeleportToCommand implements CommandTrigger {
       @Override
       public boolean blockCommand(Player p, String cmd, String[] args) {
           return cmd.equalsIgnoreCase("teleto");
       }

       @Override
       public void onCommand(Player p, String cmd, String[] args) {
           if (!p.isMod()) {
               p.message("You don't have permission.");
               return;
           }
           if (args.length < 2) {
               p.message("Usage: ::teleto <x> <y>");
               return;
           }
           int x = Integer.parseInt(args[0]);
           int y = Integer.parseInt(args[1]);
           p.teleport(x, y);
           p.message("Teleported to " + x + ", " + y);
       }
   }
   ```
2. **Permission gate every command.** Use `p.isMod()`, `p.isAdmin()`, `p.isDev()` (look up the exact API in existing commands). Never trust the caller; the command framework doesn't auto-gate.
3. **Compile plugins + restart**:
   ```bash
   cd server && ant compile_plugins
   scripts/run-server.sh
   ```
4. **Document the command in `Commands.md`** with usage, arg format, and required role.
5. **Test**:
   - As a regular player → blocked.
   - As mod / admin → works.
   - With wrong arg count → user-friendly error, no crash.
   - Edge cases (negative coords, out-of-bounds).

## Verification checklist

- [ ] Command name doesn't collide with existing in `Commands.md`.
- [ ] Permission gate works: regular player gets denied.
- [ ] Mod/admin/dev can use it.
- [ ] Argument validation graceful (no `NumberFormatException` to user).
- [ ] Unhandled cases produce a user-friendly message, not a stack trace.
- [ ] Command is logged in staff logs (most existing commands do this — check the pattern).
- [ ] Documented in `Commands.md`.

## Common pitfalls

- **Forgetting the permission gate** — anyone could run it.
- **Not handling missing args** — `args[0]` throws `ArrayIndexOutOfBoundsException` when empty.
- **Not handling parse errors** — `Integer.parseInt("foo")` throws.
- **Issuing world-altering commands without an audit log** — dangerous for accountability. Add a `StaffLog` entry for any state-changing admin command.
- **Plugin recompile required** after every edit. Server restart too.
- **Commands.md drift** — keep it updated; staff rely on this reference.
