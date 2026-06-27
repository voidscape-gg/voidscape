package com.openrsc.server.net.rsc.handlers;

import com.openrsc.server.database.impl.mysql.queries.logging.StaffLog;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.PayloadProcessor;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.struct.incoming.CommandStruct;
import com.openrsc.server.plugins.triggers.CommandTrigger;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class CommandHandler implements PayloadProcessor<CommandStruct, OpcodeIn> {
	private static final int STAFF_LOG_COMMAND_AUDIT = 24;
	private static final int BETA_MAX_SPAWN_NPC_RADIUS = 3;
	private static final int BETA_MAX_SPAWN_NPC_MINUTES = 15;
	private static final int BETA_MAX_ITEM_AMOUNT = 10000;
	private static final long BETA_SPAWN_NPC_COOLDOWN_MILLIS = 15000L;
	private static final Map<Long, Long> BETA_SPAWN_NPC_LAST_USE_MILLIS = new ConcurrentHashMap<Long, Long>();
	private static final Set<String> BETA_PLAYER_COMMANDS = new HashSet<String>(Arrays.asList(
		"beta", "betaguide", "farmkit", "farmsim", "farmcal", "codes", "refcodes"
	));
	private static final Set<String> BETA_BLOCKED_ADMIN_COMMANDS = new HashSet<String>(Arrays.asList(
		"restart", "shutdown", "update", "reloadworld", "reloadland",
		"loadbots", "loadtest", "cinematic", "cine", "voidrushbots", "vrbots",
		"massnpc", "massitem", "npcevent", "chickenevent", "stopnpcevent",
		"cancelnpcevent", "stopchickenevent", "holidaydrop", "stopholidaydrop",
		"cancelholidaydrop", "christmasiscancelled", "cabbagehalloweendrop",
		"winterholidayevent", "toggleholiday", "santaclausiscomingtotown",
		"resetevent", "stopresetevent", "cancelresetevent", "setpvpevent",
		"startpvpevent", "stoppvpevent", "eventchest", "seers", "toggleseers",
		"partyhall", "togglepartyhall",
		"say", "announcement", "announce", "anouncement", "anounce",
		"systemmessage", "sysmes", "playertalk", "npctalk", "npcsay", "alert",
		"summon", "summonall", "returnall", "groupteleport", "grouptele",
		"grouptp", "groupteleportto", "groupteleto", "grouptpto", "returngroup",
		"jail", "release", "forcesleep", "queuesleepword", "qs",
		"queuesleepwordspecial", "qss",
		"gmute", "ungmute", "mute", "unmute", "ban", "unban", "ipban",
		"banip", "syncipbans", "sip", "kick", "stayin", "setgroup",
		"setrank", "group", "rank", "setcache", "scache", "storecache",
		"deletecache", "dcache", "removecache", "rcache", "setquest",
		"queststage", "setqueststage", "resetquest", "resetq", "questcomplete",
		"questcom", "completeallquests", "renameplayer", "rename", "rp", "rn",
		"ren", "renameuser", "renamechar", "offensivename", "inappropriatename",
		"badname", "releasename", "freeusername", "freename", "copypassword",
		"copypass", "copypw", "clearipbans", "fixloggedincount",
		"kill", "damage", "dmg", "wipeinventory", "wipeinv", "wipebank",
		"ritem", "rbitem", "removegi", "removegitem", "removegrounditem",
		"rgi", "rgitem", "rgrounditem",
		"bankitem", "bitem", "addbank", "fillbank", "unfillbank",
		"gi", "gitem", "grounditem", "dropwave", "farmdrops", "spawndrops",
		"quickauction", "workbenchauctionfixture", "workbenchahfixture",
		"invisible", "invis", "invulnerable", "invul", "blink", "norender",
		"renderself", "possess", "pos", "possessnpc", "pnpc", "posnpc",
		"possessrandom", "pr", "possessnext", "pn", "lain",
		"leapaboutinstantnavigator", "hellonavi", "becomelain", "navi",
		"become", "becomenpc", "morph", "morphnpc", "becomegod",
		"speaktongues", "restorehumanity", "resetappearance", "weird",
		"weirdplayer", "stay",
		"smitenpc", "damagenpc", "dmgnpc", "shootme", "npcrangeevent",
		"npcfightevent", "npcrangedlvl", "setnpcstats", "strpotnpc",
		"combatstylenpc", "combatstyle", "skull", "unskull", "rskull",
		"setmaxplayersperip", "smppi", "setmaxconnectionsperip", "smcpi",
		"setmaxconnectionspersecond", "smcps", "stockgroup",
		"setpidshuffleinterval", "setglobalcooldown", "setgloballevelreq",
		"reloadsslcert", "refreshsslcert", "sqlerrorreportingtest", "mip",
		"smip", "setmonitorip", "monitorip", "mas", "smas",
		"setmonitorautomaticshutdown", "monitorautomaticshutdown", "simlogin",
		"simregister", "cleanidle", "cleanidleconns", "cleanidleconnections"
	));
	private static final Set<String> PRODUCTION_OWNER_ONLY_COMMANDS = new HashSet<String>(Arrays.asList(
		"saveall", "restart", "shutdown", "update", "reloadworld", "reloadland",
		"reloadsslcert", "refreshsslcert", "sqlerrorreportingtest",
		"clearipbans", "fixloggedincount",
		"loadbots", "loadtest", "cinematic", "cine", "voidrushbots", "vrbots",
		"pf", "pathfinddebug",
		"item", "certeditem", "noteditem", "bankitem", "bitem", "addbank",
		"fillbank", "unfillbank", "ritem", "rbitem", "swapitem", "wipeinventory",
		"wipeinv", "wipebank", "massitem", "gi", "gitem", "grounditem",
		"removegi", "removegitem", "removegrounditem", "rgi", "rgitem",
		"rgrounditem", "stockgroup", "beastmode", "givemodtools", "givetools",
		"lemons", "lemon", "quickauction", "workbenchauctionfixture",
		"workbenchahfixture", "dropwave", "farmdrops", "spawndrops",
		"gatherstreak", "resourcestreak",
		"spawnnpc", "massnpc", "smitenpc", "damagenpc", "dmgnpc", "shootme",
		"npcrangeevent", "npcfightevent", "npcrangedlvl", "strpotnpc",
		"setnpcstats", "npcrangeevent2", "npcevent", "chickenevent",
		"stopnpcevent", "cancelnpcevent", "stopchickenevent",
		"holidaydrop", "stopholidaydrop", "cancelholidaydrop",
		"christmasiscancelled", "cabbagehalloweendrop", "winterholidayevent",
		"toggleholiday", "santaclausiscomingtotown", "resetevent",
		"stopresetevent", "cancelresetevent", "wildhobdebug", "wildhobgoblin",
		"wildrule", "setpvpevent", "startpvpevent", "stoppvpevent",
		"eventchest", "seers", "toggleseers", "partyhall", "togglepartyhall",
		"playertalk", "npctalk", "npcsay",
		"teleport", "tp", "tele", "town", "goto", "tpto", "teleportto",
		"tpat", "rftele", "rtele", "ftele", "return", "summon",
		"summonall", "returnall", "groupteleport", "grouptele", "grouptp",
		"groupteleportto", "groupteleto", "grouptpto", "returngroup",
		"heal", "recharge", "healprayer", "healp", "hp", "sethp", "hits",
		"sethits", "prayer", "setprayer", "kill", "damage", "dmg",
		"skull", "unskull", "rskull", "freezexp", "freezeexp",
		"freezeexperience", "xpstat", "xpstats", "setxpstat", "setxpstats",
		"setxp", "stat", "stats", "setstat", "setstats", "currentstat",
		"currentstats", "setcurrentstat", "setcurrentstats", "curstat",
		"curstats", "setcurstat", "setcurstats", "setcombatstyle",
		"combatstyle", "combatstylenpc",
		"setgroup", "setrank", "group", "rank", "setcache", "scache",
		"storecache", "deletecache", "dcache", "removecache", "rcache",
		"setquest", "queststage", "setqueststage", "resetquest", "resetq",
		"questcomplete", "questcom", "completeallquests", "renameplayer",
		"rp", "rn", "ren", "renameuser", "renamechar", "copypassword",
		"copypass", "copypw", "removeformername", "fatigue", "simlogin",
		"simregister",
		"setmaxplayersperip", "smppi", "setmaxconnectionsperip", "smcpi",
		"setmaxconnectionspersecond", "smcps", "setpidshuffleinterval",
		"setglobalcooldown", "setgloballevelreq", "setpidless",
		"setpidlesscatching", "shufflepid", "pidshuffle", "sddrmdbr",
		"setdowntimereportmillis", "smtm", "setmonitortimeoutmillis",
		"mip", "smip", "setmonitorip", "monitorip", "mas", "smas",
		"setmonitorautomaticshutdown", "monitorautomaticshutdown",
		"cleanidle", "cleanidleconns", "cleanidleconnections",
		"toggletutorial", "babymode", "togglespacefiltering",
		"invisible", "invis", "invulnerable", "invul", "blink", "norender",
		"renderself", "possess", "pos", "possessnpc", "pnpc", "posnpc",
		"possessrandom", "pr", "possessnext", "pn", "lain",
		"leapaboutinstantnavigator", "hellonavi", "becomelain", "navi",
		"become", "becomenpc", "morph", "morphnpc", "becomegod",
		"speaktongues", "restorehumanity", "resetappearance", "weird",
		"weirdplayer", "stay", "set_icon", "redhat", "rhel", "robe",
		"setrobe", "setrobes",
		"radiusnpc", "createnpc", "cnpc", "cpc", "rpc", "rnpc", "removenpc",
		"removeobject", "robject", "removescenery", "rscenery",
		"createobject", "cobject", "addobject", "aobject", "createscenery",
		"cscenery", "addscenery", "ascenery", "createwallobject",
		"cwallobject", "addwallobject", "awallobject", "createboundary",
		"cboundary", "addboundary", "aboundary", "rotateobject",
		"rotatescenery", "debugregion", "error", "droptest", "fishingrate",
		"lograte", "points", "sound", "cyclescenery", "cycleclothing",
		"abort", "boundarydemo", "scenerydemo", "filtertest",
		"atnpc", "attacknpc", "atobject", "atobj", "oploc", "usescenery",
		"walkto", "npcinfo", "killnpc", "take", "takeitem", "pickup",
		"grounditems", "talknpc", "opnpc", "dropinv", "dropitem",
		"undeadsiege", "siege", "zombies", "undeadsiegeparty",
		"siegeparty", "zombiesparty", "undeadsiegepartynear",
		"siegepartynear", "zombiespartynear",
		"undeadsiegeclear", "siegeclear", "zombiesclear", "undeadsiegefinish",
		"siegefinish", "zombiesfinish", "undeadsiegefinishfull",
		"siegefinishfull", "zombiesfinishfull", "undeadsiegedie", "siegedie",
		"zombiesdie", "colossus", "voidcolossus", "colossuspeace"
	));
	private static final Set<String> COMMAND_AUDIT_TARGET_FIRST_ARG_COMMANDS = new HashSet<String>(Arrays.asList(
		"ban", "unban", "tban", "ipban", "banip", "mute", "unmute", "gmute", "ungmute",
		"kick", "jail", "release", "summon", "return", "send", "take", "put", "goto",
		"tpto", "teleportto", "tpat", "check", "setrank", "rank", "setgroup", "group",
		"renameplayer", "rename", "rp", "rn", "ren", "renameuser", "renamechar",
		"wipeinventory", "wipeinv", "wipebank", "ritem", "rbitem", "removegi",
		"removegitem", "removegrounditem", "rgi", "rgitem", "rgrounditem"
	));
	private static final Set<String> COMMAND_AUDIT_ITEM_COMMANDS = new HashSet<String>(Arrays.asList(
		"item", "certeditem", "noteditem", "gi", "gitem", "grounditem", "removegi",
		"removegitem", "removegrounditem", "rgi", "rgitem", "rgrounditem",
		"bankitem", "bitem", "addbank", "fillbank", "unfillbank", "ritem",
		"rbitem", "wipeinventory", "wipeinv", "wipebank", "massitem", "dropwave",
		"farmdrops", "spawndrops", "quickauction", "workbenchauctionfixture",
		"workbenchahfixture"
	));
	private static final Set<String> COMMAND_AUDIT_MODERATION_COMMANDS = new HashSet<String>(Arrays.asList(
		"ban", "unban", "tban", "ipban", "banip", "syncipbans", "sip",
		"mute", "unmute", "gmute", "ungmute", "kick", "jail", "release",
		"forcesleep", "queuesleepword", "qs", "queuesleepwordspecial", "qss",
		"stayin", "clearipbans", "offensivename", "inappropriatename",
		"badname", "releasename", "freeusername", "freename"
	));
	private static final Set<String> COMMAND_AUDIT_ACCOUNT_COMMANDS = new HashSet<String>(Arrays.asList(
		"setgroup", "group", "setrank", "rank", "setcache", "scache", "storecache",
		"deletecache", "dcache", "removecache", "rcache", "setquest", "queststage",
		"setqueststage", "resetquest", "resetq", "questcomplete", "questcom",
		"completeallquests", "renameplayer", "rename", "rp", "rn", "ren",
		"renameuser", "renamechar", "copypassword", "copypass", "copypw",
		"hp", "sethp", "hits", "sethits", "prayer", "setprayer", "stat",
		"stats", "setstat", "setstats", "xpstat", "xpstats", "setxpstat",
		"setxpstats", "setxp"
	));

	public void process(CommandStruct payload, Player player) throws Exception {
		if (System.currentTimeMillis() - player.getLastCommand() < 1000 && !player.isAdmin()) {
			player.message(player.getConfig().MESSAGE_PREFIX + "There's a second delay between using commands");
		} else {
			String s = payload.command;
			handleCommandString(player, s);
		}
	}
	public static void handleCommandString(Player player, String s) {
		int firstSpace = s.indexOf(" ");
		String cmd = s;
		String[] args = new String[0];
		if (firstSpace != -1) {
			cmd = s.substring(0, firstSpace).trim();
			args = s.substring(firstSpace + 1).trim().split(" ");
		}
		String normalizedCmd = cmd.toLowerCase();
		if (player.getWorld().getServer().getDiscordService() != null) {
			String[] ignoredCommands = {
				"gang",
				"c",
				"clanaccept",
				"partyaccept",
				"claninvite",
				"clankick",
				"gameinfo",
				"event",
				"g",
				"pk",
				"p",
				"online",
				"uniqueonline",
				"leaveparty",
				"joinclan",
				"shareloot",
				"shareexp",
				"onlinelist",
				"onlinelistlocs",
				"groups",
				"ranks",
				"time",
				"date",
				"datetime",
				"pair",
				"d",
				"commands",
				"codes",
				"refcodes",
				"b",
				"qoloptout",
				"qoloptoutconfirm",
				"certoptout",
				"certoptoutconfirm",
				"toggleglobalchat",
				"getholidaydrop",
				"checkholidaydrop",
				"checkholidayevent",
				"drop",
				"toggleblockchat",
				"toggleblockprivate",
				"toggleblocktrade",
				"toggleblockduel",
				"clientlimitations",
				"setversion",
				"skiptutorial",
				"oldtrade",
				"notradeconfirm",
				"coords",
				"setlanguage",
				"language",
				"togglereceipts",
				"getpidlesscatching",
				"tellpidlesscatching",
				"pidless",
				"maxplayersperip",
				"mppi",
				"setglobalmessagecolor",
				"globalquest",
				"gq",
				"globalprivate",
				"gp",
				"set_icon",
				"redhat",
				"rhel",
				"robe",
				"setrobe",
				"setrobes",
				"become",
				"becomenpc",
				"morph",
				"morphnpc",
				"becomegod",
				"speaktongues",
				"restorehumanity",
				"resetappearance",
				"check",
				"pr",
				"pn",
				"pos",
				"lain",
				"leapaboutinstantnavigator",
				"hellonavi",
				"navi",
				"becomelain",
				"weird",
					"weirdplayer",
					"stay",
					"reset",
					"uptime",
					"enable_protocol_extensions",
					"kc",
					"kills",
					"lootbeam",
					"lootbeams"
				};
			if (player.isPlayerMod() && !Arrays.asList(ignoredCommands).contains(normalizedCmd)) {
				player.getWorld().getServer().getDiscordService().staffCommandLog(player, "::" + cmd + " " + String.join(" ", args));
			}
		}
		boolean blocked = blockUnsafeCommand(player, normalizedCmd, args);
		auditStaffCommand(player, normalizedCmd, args, blocked);
		if (blocked) {
			return;
		}
		player.getWorld().getServer().getPluginHandler().handlePlugin(
				CommandTrigger.class,
				player,
				new Object[]{player, normalizedCmd, args}
		);
	}

	private static void auditStaffCommand(Player player, String cmd, String[] args, boolean blocked) {
		if (!player.isPlayerMod()) return;
		player.getWorld().getServer().getGameLogger().addQuery(
			new StaffLog(player, STAFF_LOG_COMMAND_AUDIT, buildStaffCommandAuditExtra(cmd, args, blocked))
		);
	}

	private static String buildStaffCommandAuditExtra(String cmd, String[] args, boolean blocked) {
		String target = commandAuditTarget(cmd, args);
		StringBuilder extra = new StringBuilder();
		extra.append("integrity command=").append(sanitizeAuditToken(cmd));
		extra.append(" status=").append(blocked ? "blocked" : "allowed");
		extra.append(" category=").append(commandAuditCategory(cmd));
		if (target.length() > 0) {
			extra.append(" target=").append(target);
		}
		extra.append(" argc=").append(args == null ? 0 : args.length);
		return truncateAuditExtra(extra.toString());
	}

	private static String commandAuditCategory(String cmd) {
		if (COMMAND_AUDIT_MODERATION_COMMANDS.contains(cmd)) return "moderation";
		if (COMMAND_AUDIT_ACCOUNT_COMMANDS.contains(cmd)) return "account";
		if (COMMAND_AUDIT_ITEM_COMMANDS.contains(cmd)) return "item";
		if (cmd.equals("spawnnpc") || cmd.equals("massnpc") || cmd.contains("npc")) return "npc";
		if (isTeleportCommand(cmd) || cmd.equals("summon") || cmd.equals("return")
			|| cmd.equals("summonall") || cmd.equals("returnall") || cmd.contains("tele")) return "movement";
		if (cmd.contains("event") || cmd.equals("holidaydrop") || cmd.equals("globaldrop")) return "world";
		return "staff";
	}

	private static String commandAuditTarget(String cmd, String[] args) {
		if (args == null || args.length == 0) return "";
		if (COMMAND_AUDIT_TARGET_FIRST_ARG_COMMANDS.contains(cmd) && !isInteger(args[0])) {
			return sanitizeAuditToken(args[0]);
		}
		if (isTeleportCommand(cmd) && args.length == 1 && !isInteger(args[0])) {
			return sanitizeAuditToken(args[0]);
		}
		return "";
	}

	private static String sanitizeAuditToken(String value) {
		if (value == null) return "";
		StringBuilder sanitized = new StringBuilder();
		for (int i = 0; i < value.length() && sanitized.length() < 24; i++) {
			char ch = value.charAt(i);
			if (Character.isLetterOrDigit(ch)) {
				sanitized.append(Character.toLowerCase(ch));
			} else if (ch == '_' || ch == '-' || ch == '.' || ch == ' ') {
				sanitized.append('_');
			}
		}
		return sanitized.toString();
	}

	private static String truncateAuditExtra(String extra) {
		if (extra.length() <= 240) return extra;
		return extra.substring(0, 240);
	}

	private static boolean blockUnsafeBetaAdminCommand(Player player, String cmd, String[] args) {
		if (!player.getWorld().getServer().getConfig().WANT_BETA_ONBOARDING_GUIDE) return false;
		if (!player.isAdmin() || player.isOwner()) return false;

		if (BETA_BLOCKED_ADMIN_COMMANDS.contains(cmd)) {
			player.message(player.getConfig().MESSAGE_PREFIX + "That admin command is disabled during public beta.");
			return true;
		}

		if (isTeleportCommand(cmd)) {
			return blockUnsafeBetaTeleport(player, cmd, args);
		}

		if (cmd.equals("return") && args.length > 0) {
			player.message(player.getConfig().MESSAGE_PREFIX + "Returning other players is disabled during public beta.");
			return true;
		}

		if (cmd.equals("heal") || cmd.equals("recharge") || cmd.equals("healprayer") || cmd.equals("healp")) {
			if (args.length > 0) {
				player.message(player.getConfig().MESSAGE_PREFIX + "Healing other players by command is disabled during public beta.");
				return true;
			}
			return false;
		}

		if (cmd.equals("hp") || cmd.equals("sethp") || cmd.equals("hits") || cmd.equals("sethits")
			|| cmd.equals("prayer") || cmd.equals("setprayer")) {
			if (args.length > 1) {
				player.message(player.getConfig().MESSAGE_PREFIX + "Changing other players by command is disabled during public beta.");
				return true;
			}
			return false;
		}

		if (cmd.equals("stat") || cmd.equals("stats") || cmd.equals("setstat") || cmd.equals("setstats")
			|| cmd.equals("xpstat") || cmd.equals("xpstats") || cmd.equals("setxpstat")
			|| cmd.equals("setxpstats") || cmd.equals("setxp")) {
			player.message(player.getConfig().MESSAGE_PREFIX + "Direct stat commands are disabled during public beta.");
			return true;
		}

		if (cmd.equals("item") || cmd.equals("certeditem") || cmd.equals("noteditem")) {
			return blockUnsafeBetaItemSpawn(player, args);
		}

		if (cmd.equals("spawnnpc")) {
			return blockUnsafeBetaNpcSpawn(player, args);
		}

		return false;
	}

	private static boolean blockUnsafeCommand(Player player, String cmd, String[] args) {
		if (blockBetaPlayerCommand(player, cmd)) return true;
		if (blockUnsafeProductionCommand(player, cmd, args)) return true;
		return blockUnsafeBetaAdminCommand(player, cmd, args);
	}

	private static boolean blockBetaPlayerCommand(Player player, String cmd) {
		if (!BETA_PLAYER_COMMANDS.contains(cmd)) return false;

		if (!player.getWorld().getServer().getConfig().WANT_BETA_ONBOARDING_GUIDE) {
			player.message(player.getConfig().MESSAGE_PREFIX + "That beta command is disabled on this world.");
			return true;
		}

		if (player.getWorld().getServer().getConfig().PRODUCTION_COMMAND_LOCKDOWN && !player.isOwner()) {
			player.message(player.getConfig().MESSAGE_PREFIX + "That beta command is disabled during public launch.");
			return true;
		}

		return false;
	}

	private static boolean blockUnsafeProductionCommand(Player player, String cmd, String[] args) {
		if (!player.getWorld().getServer().getConfig().PRODUCTION_COMMAND_LOCKDOWN) return false;
		if (player.isOwner()) return false;

		if (PRODUCTION_OWNER_ONLY_COMMANDS.contains(cmd)
			|| isProductionOwnerOnlyPrefix(cmd)
			|| isProductionOwnerOnlySubcommand(cmd, args)
			|| isProductionOwnerOnlyStaffAlias(player, cmd)) {
			player.message(player.getConfig().MESSAGE_PREFIX + "That command is owner-only while production command lockdown is enabled.");
			return true;
		}
		return false;
	}

	private static boolean isProductionOwnerOnlyPrefix(String cmd) {
		return cmd.startsWith("tpnpc");
	}

	private static boolean isProductionOwnerOnlySubcommand(String cmd, String[] args) {
		if ((cmd.equals("balancereport") || cmd.equals("balancestats"))
			&& args != null && args.length > 0 && args[0].equalsIgnoreCase("reset")) {
			return true;
		}
		return false;
	}

	private static boolean isProductionOwnerOnlyStaffAlias(Player player, String cmd) {
		return cmd.equals("rename") && player.isPlayerMod();
	}

	private static boolean isTeleportCommand(String cmd) {
		return cmd.equals("teleport") || cmd.equals("tp") || cmd.equals("tele") || cmd.equals("town")
			|| cmd.equals("goto") || cmd.equals("tpto") || cmd.equals("teleportto") || cmd.equals("tpat");
	}

	private static boolean blockUnsafeBetaTeleport(Player player, String cmd, String[] args) {
		if (args.length >= 3) {
			player.message(player.getConfig().MESSAGE_PREFIX + "Teleporting other players is disabled during public beta.");
			return true;
		}
		if (args.length == 2 && !isInteger(args[0]) && !isInteger(args[1])) {
			player.message(player.getConfig().MESSAGE_PREFIX + "Teleporting other players is disabled during public beta.");
			return true;
		}
		if (args.length == 2 && isInteger(args[0]) != isInteger(args[1])) {
			player.message(player.getConfig().MESSAGE_PREFIX + "Use ::" + cmd + " [x] [y] or ::" + cmd + " [town/player].");
			return true;
		}
		return false;
	}

	private static boolean blockUnsafeBetaItemSpawn(Player player, String[] args) {
		if (args.length >= 3) {
			player.message(player.getConfig().MESSAGE_PREFIX + "Spawning items to other players is disabled during public beta.");
			return true;
		}

		int amount = 1;
		if (args.length >= 2) {
			Integer parsed = tryParseInt(args[1]);
			if (parsed == null || parsed < 1) {
				return false;
			}
			amount = parsed;
		}
		if (amount > BETA_MAX_ITEM_AMOUNT) {
			player.message(player.getConfig().MESSAGE_PREFIX + "Beta item spawns are capped at " + BETA_MAX_ITEM_AMOUNT + " per command.");
			return true;
		}
		return false;
	}

	private static boolean blockUnsafeBetaNpcSpawn(Player player, String[] args) {
		int radius = 1;
		if (args.length >= 2) {
			Integer parsed = tryParseInt(args[1]);
			if (parsed != null) {
				radius = parsed;
			}
		}
		if (radius < 0 || radius > BETA_MAX_SPAWN_NPC_RADIUS) {
			player.message(player.getConfig().MESSAGE_PREFIX + "Beta NPC spawns are capped at radius " + BETA_MAX_SPAWN_NPC_RADIUS + ".");
			return true;
		}

		int minutes = 10;
		if (args.length >= 3) {
			Integer parsed = tryParseInt(args[2]);
			if (parsed != null) {
				minutes = parsed;
			}
		}
		if (minutes < 1 || minutes > BETA_MAX_SPAWN_NPC_MINUTES) {
			player.message(player.getConfig().MESSAGE_PREFIX + "Beta NPC spawns are capped at " + BETA_MAX_SPAWN_NPC_MINUTES + " minutes.");
			return true;
		}

		long now = System.currentTimeMillis();
		Long lastUse = BETA_SPAWN_NPC_LAST_USE_MILLIS.get(player.getUsernameHash());
		if (lastUse != null && now - lastUse < BETA_SPAWN_NPC_COOLDOWN_MILLIS) {
			long seconds = (BETA_SPAWN_NPC_COOLDOWN_MILLIS - (now - lastUse) + 999L) / 1000L;
			player.message(player.getConfig().MESSAGE_PREFIX + "NPC spawning is cooling down for " + seconds + " more seconds.");
			return true;
		}
		BETA_SPAWN_NPC_LAST_USE_MILLIS.put(player.getUsernameHash(), now);
		return false;
	}

	private static boolean isInteger(String value) {
		return tryParseInt(value) != null;
	}

	private static Integer tryParseInt(String value) {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException ex) {
			return null;
		}
	}
}
