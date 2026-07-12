package com.openrsc.server.plugins.custom.commands;

import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.CommandTrigger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class Integrity implements CommandTrigger {
	private static final String DEFAULT_FINDINGS_PATH = "/var/lib/voidscape-portal/integrity-findings.json";

	@Override
	public boolean blockCommand(Player player, String command, String[] args) {
		return command.equalsIgnoreCase("integrity");
	}

	@Override
	public void onCommand(Player player, String command, String[] args) {
		if (!player.isAdmin()) {
			player.message(player.getConfig().MESSAGE_PREFIX + "You do not have permission to use ::integrity.");
			return;
		}

		JSONObject report = readReport(player);
		if (report == null) {
			return;
		}

		if (args.length == 0 || args[0].equalsIgnoreCase("summary") || args[0].equalsIgnoreCase("scan")) {
			showSummary(player, report);
		} else if (args[0].equalsIgnoreCase("recent")) {
			showRecent(player, report, parseLimit(args, 1));
		} else if (args[0].equalsIgnoreCase("player")) {
			if (args.length < 2) {
				player.message(player.getConfig().BAD_SYNTAX_PREFIX + "::integrity player <name> [limit]");
				return;
			}
			showPlayer(player, report, args[1], parseLimit(args, 2));
		} else {
			player.message(player.getConfig().BAD_SYNTAX_PREFIX + "::integrity [summary|recent [limit]|player <name> [limit]]");
		}
	}

	private JSONObject readReport(Player player) {
		final String path = findingsPath();
		try {
			return new JSONObject(new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8));
		} catch (Exception ex) {
			player.message(player.getConfig().MESSAGE_PREFIX + "No integrity findings file found at " + path);
			player.message(player.getConfig().MESSAGE_PREFIX + "Wait for the integrity exporter, or run it with PORTAL_INTEGRITY_FINDINGS set.");
			return null;
		}
	}

	private void showSummary(Player player, JSONObject report) {
		JSONObject summary = report.optJSONObject("summary");
		if (summary == null) {
			player.message(player.getConfig().MESSAGE_PREFIX + "Integrity report is missing a summary.");
			return;
		}
		player.message(player.getConfig().MESSAGE_PREFIX + "Integrity scan " + summary.optString("status", "unknown")
			+ ": " + summary.optInt("flagged", 0) + " flagged, " + summary.optInt("highSeverity", 0) + " high.");
		player.message(player.getConfig().MESSAGE_PREFIX + "Checked " + summary.optInt("trackedItems", 0)
			+ " item rows across " + summary.optInt("checkedPlayers", 0) + " players.");
		player.message(player.getConfig().MESSAGE_PREFIX + "Economy flagged: " + summary.optInt("economyFlagged", summary.optInt("flagged", 0))
			+ ". Account flagged: " + summary.optInt("accountFlagged", 0)
			+ ". Review: " + summary.optInt("review", 0) + ".");
		player.message(player.getConfig().MESSAGE_PREFIX + "Account review: " + summary.optInt("privilegedAccounts", 0)
			+ " privileged, " + summary.optInt("watchedCacheRows", 0) + " watched cache rows, "
			+ summary.optInt("recentSensitiveCommands24h", 0) + " sensitive staff commands 24h.");
		player.message(player.getConfig().MESSAGE_PREFIX + "Last scan: " + shortTimestamp(report.optString("generatedAt", "")));
		if (summary.optInt("flagged", 0) > 0 || summary.optInt("review", 0) > 0) {
			player.message(player.getConfig().MESSAGE_PREFIX + "Use ::integrity recent or ::integrity player <name>.");
		}
	}

	private void showRecent(Player player, JSONObject report, int limit) {
		JSONArray findings = report.optJSONArray("findings");
		if (findings == null || findings.length() == 0) {
			player.message(player.getConfig().MESSAGE_PREFIX + "No private integrity findings in the latest scan.");
			return;
		}
		int shown = Math.min(limit, findings.length());
		player.message(player.getConfig().MESSAGE_PREFIX + "Recent integrity findings (" + shown + "/" + findings.length() + "):");
		for (int i = 0; i < shown; i++) {
			player.message(formatFinding(findings.optJSONObject(i)));
		}
	}

	private void showPlayer(Player player, JSONObject report, String username, int limit) {
		JSONArray findings = report.optJSONArray("findings");
		if (findings == null || findings.length() == 0) {
			player.message(player.getConfig().MESSAGE_PREFIX + "No private integrity findings in the latest scan.");
			return;
		}
		String needle = username.trim().toLowerCase();
		int shown = 0;
		for (int i = 0; i < findings.length() && shown < limit; i++) {
			JSONObject finding = findings.optJSONObject(i);
			if (finding == null || !finding.optString("username", "").toLowerCase().equals(needle)) {
				continue;
			}
			if (shown == 0) {
				player.message(player.getConfig().MESSAGE_PREFIX + "Integrity findings for " + username + ":");
			}
			player.message(formatFinding(finding));
			shown++;
		}
		if (shown == 0) {
			player.message(player.getConfig().MESSAGE_PREFIX + "No integrity findings for " + username + " in the latest scan.");
		}
	}

	private String formatFinding(JSONObject finding) {
		if (finding == null) {
			return "- malformed finding";
		}
		StringBuilder line = new StringBuilder();
		line.append("- ")
			.append(finding.optString("severity", "info"))
			.append(" ")
			.append(finding.optString("category", "unknown"));
		String username = finding.optString("username", "");
		if (!username.isEmpty()) {
			line.append(" player=").append(username);
		}
		String staffUsername = finding.optString("staffUsername", "");
		if (!staffUsername.isEmpty()) {
			line.append(" staff=").append(staffUsername);
		}
		String cacheKey = finding.optString("cacheKey", "");
		if (!cacheKey.isEmpty()) {
			line.append(" cache=").append(cacheKey);
		}
		int itemID = finding.optInt("itemID", 0);
		if (itemID > 0) {
			line.append(" itemID=").append(itemID);
		}
		String message = finding.optString("message", "");
		if (!message.isEmpty()) {
			line.append(" - ").append(message);
		}
		return line.length() > 160 ? line.substring(0, 157) + "..." : line.toString();
	}

	private int parseLimit(String[] args, int index) {
		if (args.length <= index) {
			return 5;
		}
		try {
			return Math.max(1, Math.min(10, Integer.parseInt(args[index])));
		} catch (NumberFormatException ex) {
			return 5;
		}
	}

	private String shortTimestamp(String timestamp) {
		if (timestamp == null || timestamp.length() < 16) {
			return "unknown";
		}
		return timestamp.substring(0, 16).replace('T', ' ') + " UTC";
	}

	private String findingsPath() {
		String path = System.getenv("PORTAL_INTEGRITY_FINDINGS");
		if (path == null || path.trim().isEmpty()) {
			return DEFAULT_FINDINGS_PATH;
		}
		return path.trim();
	}
}
