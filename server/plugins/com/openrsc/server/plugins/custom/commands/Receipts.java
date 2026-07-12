package com.openrsc.server.plugins.custom.commands;

import com.openrsc.server.database.GameDatabase;
import com.openrsc.server.database.GameDatabaseException;
import com.openrsc.server.database.JDBCDatabase;
import com.openrsc.server.external.ItemDefinition;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.CommandTrigger;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public final class Receipts implements CommandTrigger {
	private static final int DEFAULT_LIMIT = 6;
	private static final int MAX_LIMIT = 12;
	private static final int MAX_LINE_LENGTH = 160;

	@Override
	public boolean blockCommand(Player player, String command, String[] args) {
		return command.equalsIgnoreCase("receipts") || command.equalsIgnoreCase("receipt");
	}

	@Override
	public void onCommand(Player player, String command, String[] args) {
		if (!player.isAdmin()) {
			player.message(player.getConfig().MESSAGE_PREFIX + "You do not have permission to use ::receipts.");
			return;
		}

		if (args.length == 0 || args[0].equalsIgnoreCase("recent")) {
			showRecent(player, parseLimit(args, 1));
		} else if (args[0].equalsIgnoreCase("player")) {
			if (args.length < 2) {
				player.message(player.getConfig().BAD_SYNTAX_PREFIX + "::receipts player <name> [limit]");
				return;
			}
			showPlayer(player, args[1], parseLimit(args, 2));
		} else if (args[0].equalsIgnoreCase("item")) {
			if (args.length < 2) {
				player.message(player.getConfig().BAD_SYNTAX_PREFIX + "::receipts item <itemID> [limit]");
				return;
			}
			showItem(player, args[1], parseLimit(args, 2));
		} else if (args[0].equalsIgnoreCase("catalog")) {
			if (args.length < 2) {
				player.message(player.getConfig().BAD_SYNTAX_PREFIX + "::receipts catalog <catalogID> [limit]");
				return;
			}
			showCatalog(player, args[1], parseLimit(args, 2));
		} else if (args[0].equalsIgnoreCase("command")) {
			if (args.length < 2) {
				player.message(player.getConfig().BAD_SYNTAX_PREFIX + "::receipts command <name> [limit]");
				return;
			}
			showCommand(player, args[1], parseLimit(args, 2));
		} else {
			player.message(player.getConfig().BAD_SYNTAX_PREFIX
				+ "::receipts [recent|player <name>|item <itemID>|catalog <catalogID>|command <name>] [limit]");
		}
	}

	private void showRecent(Player player, int limit) {
		showRows(player, "Recent item receipts", query(player, "", null, limit));
	}

	private void showPlayer(Player player, String username, int limit) {
		int playerID = -1;
		try {
			playerID = player.getWorld().getServer().getDatabase().playerIdFromUsername(username);
		} catch (GameDatabaseException ignored) {
			playerID = -1;
		}
		final int targetPlayerID = playerID;
		final String targetUsername = username.trim();
		showRows(player, "Item receipts for " + targetUsername, query(player,
			"WHERE `actorID` = ? OR `targetID` = ? OR LOWER(`actor_username`) = LOWER(?) OR LOWER(`target_username`) = LOWER(?)",
			statement -> {
				statement.setInt(1, Math.max(0, targetPlayerID));
				statement.setInt(2, Math.max(0, targetPlayerID));
				statement.setString(3, targetUsername);
				statement.setString(4, targetUsername);
				return 5;
			}, limit));
	}

	private void showItem(Player player, String itemIDText, int limit) {
		Long itemID = parseLong(itemIDText);
		if (itemID == null || itemID < 0) {
			player.message(player.getConfig().BAD_SYNTAX_PREFIX + "::receipts item <itemID> [limit]");
			return;
		}
		showRows(player, "Item receipts for itemID " + itemID, query(player, "WHERE `itemID` = ?",
			statement -> {
				statement.setLong(1, itemID);
				return 2;
			}, limit));
	}

	private void showCatalog(Player player, String catalogIDText, int limit) {
		Integer catalogID = parseInt(catalogIDText);
		if (catalogID == null || catalogID < 0) {
			player.message(player.getConfig().BAD_SYNTAX_PREFIX + "::receipts catalog <catalogID> [limit]");
			return;
		}
		showRows(player, "Item receipts for catalog " + catalogID, query(player, "WHERE `catalogID` = ?",
			statement -> {
				statement.setInt(1, catalogID);
				return 2;
			}, limit));
	}

	private void showCommand(Player player, String command, int limit) {
		final String needle = command.trim();
		showRows(player, "Item receipts for command " + needle, query(player, "WHERE LOWER(`command`) = LOWER(?)",
			statement -> {
				statement.setString(1, needle);
				return 2;
			}, limit));
	}

	private List<ReceiptRow> query(Player player, String whereClause, StatementBinder binder, int limit) {
		GameDatabase gameDatabase = player.getWorld().getServer().getDatabase();
		if (!(gameDatabase instanceof JDBCDatabase)) {
			player.message(player.getConfig().MESSAGE_PREFIX + "Receipt lookup requires a JDBC database.");
			return new ArrayList<>();
		}

		String prefix = player.getWorld().getServer().getConfig().DB_TABLE_PREFIX;
		String sql = "SELECT `eventID`, `itemID`, `catalogID`, `amount`, `noted`, `actorID`, `actor_username`, "
			+ "`targetID`, `target_username`, `event_type`, `source`, `destination`, `command`, `x`, `y`, `time`, `extra` "
			+ "FROM `" + prefix + "item_provenance_events` " + whereClause
			+ " ORDER BY `time` DESC, `eventID` DESC LIMIT ?";

		try (PreparedStatement statement = ((JDBCDatabase) gameDatabase).preparedStatement(sql)) {
			int limitIndex = binder == null ? 1 : binder.bind(statement);
			statement.setInt(limitIndex, limit);
			try (ResultSet results = statement.executeQuery()) {
				List<ReceiptRow> rows = new ArrayList<>();
				while (results.next()) {
					rows.add(ReceiptRow.from(results));
				}
				return rows;
			}
		} catch (SQLException | GameDatabaseException ex) {
			player.message(player.getConfig().MESSAGE_PREFIX + "Receipt lookup failed: " + friendlyError(ex));
			return new ArrayList<>();
		}
	}

	private void showRows(Player player, String title, List<ReceiptRow> rows) {
		if (rows.isEmpty()) {
			player.message(player.getConfig().MESSAGE_PREFIX + title + ": no rows found.");
			return;
		}
		player.message(player.getConfig().MESSAGE_PREFIX + title + " (" + rows.size() + "):");
		for (ReceiptRow row : rows) {
			player.message(formatRow(player, row));
		}
	}

	private String formatRow(Player player, ReceiptRow row) {
		String item = itemName(player, row.catalogID);
		StringBuilder line = new StringBuilder();
		line.append("#").append(row.eventID)
			.append(" ").append(shortTime(row.time))
			.append(" ").append(row.eventType)
			.append(" ").append(row.command)
			.append(" ").append(row.source).append("->").append(row.destination)
			.append(" ").append(row.amount).append("x ").append(item)
			.append(" cat=").append(row.catalogID);
		if (row.itemID > 0) {
			line.append(" item=").append(row.itemID);
		}
		if (row.noted) {
			line.append(" noted");
		}
		String actors = actorSummary(row);
		if (!actors.isEmpty()) {
			line.append(" ").append(actors);
		}
		if (row.x != 0 || row.y != 0) {
			line.append(" @").append(row.x).append(",").append(row.y);
		}
		if (!row.extra.isEmpty()) {
			line.append(" ").append(row.extra);
		}
		return truncate(line.toString());
	}

	private String actorSummary(ReceiptRow row) {
		String actor = named(row.actorUsername, row.actorID);
		String target = named(row.targetUsername, row.targetID);
		if (actor.isEmpty() && target.isEmpty()) {
			return "";
		}
		if (target.isEmpty() || target.equals(actor)) {
			return "actor=" + actor;
		}
		if (actor.isEmpty()) {
			return "target=" + target;
		}
		return actor + "=>" + target;
	}

	private String named(String username, int playerID) {
		if (username != null && !username.isEmpty()) {
			return username;
		}
		if (playerID > 0) {
			return String.valueOf(playerID);
		}
		return "";
	}

	private String itemName(Player player, int catalogID) {
		ItemDefinition def = player.getWorld().getServer().getEntityHandler().getItemDef(catalogID);
		if (def == null || def.getName() == null || def.getName().isEmpty()) {
			return "unknown";
		}
		return def.getName();
	}

	private int parseLimit(String[] args, int index) {
		if (args.length <= index) {
			return DEFAULT_LIMIT;
		}
		Integer parsed = parseInt(args[index]);
		if (parsed == null) {
			return DEFAULT_LIMIT;
		}
		return Math.max(1, Math.min(MAX_LIMIT, parsed));
	}

	private Integer parseInt(String value) {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	private Long parseLong(String value) {
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	private String shortTime(long epochSeconds) {
		SimpleDateFormat format = new SimpleDateFormat("MM-dd HH:mm");
		format.setTimeZone(TimeZone.getTimeZone("UTC"));
		return format.format(new Date(epochSeconds * 1000L)) + "Z";
	}

	private String friendlyError(Exception ex) {
		String message = ex.getMessage();
		if (message == null || message.trim().isEmpty()) {
			return ex.getClass().getSimpleName();
		}
		return truncate(message.trim());
	}

	private String truncate(String line) {
		if (line.length() <= MAX_LINE_LENGTH) {
			return line;
		}
		return line.substring(0, MAX_LINE_LENGTH - 3) + "...";
	}

	private interface StatementBinder {
		int bind(PreparedStatement statement) throws SQLException;
	}

	private static final class ReceiptRow {
		private long eventID;
		private long itemID;
		private int catalogID;
		private int amount;
		private boolean noted;
		private int actorID;
		private String actorUsername;
		private int targetID;
		private String targetUsername;
		private String eventType;
		private String source;
		private String destination;
		private String command;
		private int x;
		private int y;
		private long time;
		private String extra;

		private static ReceiptRow from(ResultSet results) throws SQLException {
			ReceiptRow row = new ReceiptRow();
			row.eventID = results.getLong("eventID");
			row.itemID = results.getLong("itemID");
			row.catalogID = results.getInt("catalogID");
			row.amount = results.getInt("amount");
			row.noted = results.getInt("noted") != 0;
			row.actorID = results.getInt("actorID");
			row.actorUsername = clean(results.getString("actor_username"));
			row.targetID = results.getInt("targetID");
			row.targetUsername = clean(results.getString("target_username"));
			row.eventType = clean(results.getString("event_type"));
			row.source = clean(results.getString("source"));
			row.destination = clean(results.getString("destination"));
			row.command = clean(results.getString("command"));
			row.x = results.getInt("x");
			row.y = results.getInt("y");
			row.time = results.getLong("time");
			row.extra = clean(results.getString("extra"));
			return row;
		}

		private static String clean(String value) {
			return value == null ? "" : value;
		}
	}
}
