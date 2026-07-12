package com.openrsc.server.net.rsc.handlers;

/** Executable policy regression for launch-sensitive deterministic fixture commands. */
public final class CommandLockdownTest {
	private CommandLockdownTest() {
	}

	public static void main(String[] args) {
		for (String alias : new String[]{"workbenchcracker", "crackerfixture"}) {
			check(CommandHandler.productionLockdownBlocks(true, false, alias,
				new String[]{"0", "0"}, false), alias + " is owner-only in production");
			check(!CommandHandler.productionLockdownBlocks(true, true, alias,
				new String[]{"0", "0"}, false), alias + " remains available to the owner");
			check(!CommandHandler.productionLockdownBlocks(false, false, alias,
				new String[]{"0", "0"}, false), alias + " is not production-blocked on QA worlds");
			check(CommandHandler.isBetaBlockedAdminCommand(alias),
				alias + " is disabled for non-owner beta admins");
			check(CommandHandler.isAuditedItemCommand(alias),
				alias + " is recorded as an item command");
		}

		check(CommandHandler.productionLockdownBlocks(true, false, "cracker",
			new String[]{"1000"}, false),
			"cracker campaign control is owner-only in production lockdown");
		check(!CommandHandler.productionLockdownBlocks(true, true, "cracker",
			new String[]{"1000"}, false),
			"production lockdown allows the owner to control the campaign");
		check(CommandHandler.ownerOnlyWorldControlBlocks(false, "cracker"),
			"cracker campaign remains owner-only on QA worlds too");
		check(!CommandHandler.ownerOnlyWorldControlBlocks(true, "cracker"),
			"unconditional owner gate allows the owner");
		check(!CommandHandler.ownerOnlyWorldControlBlocks(false, "workbenchcracker"),
			"campaign owner gate does not change the separate deterministic fixture policy");
		check(!CommandHandler.isAuditedItemCommand("cracker"),
			"campaign pool control is a world command, not an item-spawn command");

		for (String alias : new String[]{"announcepreview", "worldannouncepreview"}) {
			check(CommandHandler.productionLockdownBlocks(true, false, alias,
				new String[]{"firstskill"}, false),
				alias + " is owner-only in production lockdown");
			check(!CommandHandler.productionLockdownBlocks(true, true, alias,
				new String[]{"firstskill"}, false),
				alias + " remains available to the owner in production");
			check(!CommandHandler.productionLockdownBlocks(false, false, alias,
				new String[]{"firstskill"}, false),
				alias + " remains available to admins outside production lockdown");
			check(!CommandHandler.ownerOnlyWorldControlBlocks(false, alias),
				alias + " is not unconditionally owner-only");
		}

		System.out.println("Cracker and announcement-preview command lockdown tests passed.");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
