package com.openrsc.server.model.world;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.external.ItemDefinition;
import com.openrsc.server.external.SpellDef;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;

import java.util.Locale;

public final class WildernessRules {
	private static final String VOID_DUNGEON_PVP_OPPONENT_ATTRIBUTE = "void_dungeon_pvp_opponent_hash";
	private static final String VOID_DUNGEON_PVP_TICK_ATTRIBUTE = "void_dungeon_pvp_tick";
	private static final String VOID_DUNGEON_PVP_MESSAGE = "The Void Dungeon only allows one-on-one player fights.";

	private WildernessRules() {
	}

	public static boolean isF2PWilderness(Point point) {
		return point != null && point.inWilderness() && !point.isMembersWild();
	}

	public static boolean appliesVoidDungeonPvpRule(Player attacker, Player victim) {
		return attacker != null
			&& victim != null
			&& (isInVoidDungeonUnderground(attacker) || isInVoidDungeonUnderground(victim));
	}

	public static boolean canAttackVoidDungeonPvp(Player attacker, Player victim) {
		if (!appliesVoidDungeonPvpRule(attacker, victim)) {
			return true;
		}

		Long attackerOpponent = getVoidDungeonPvpOpponentHash(attacker);
		Long victimOpponent = getVoidDungeonPvpOpponentHash(victim);
		long attackerHash = attacker.getUsernameHash();
		long victimHash = victim.getUsernameHash();

		return (attackerOpponent == null || attackerOpponent == victimHash)
			&& (victimOpponent == null || victimOpponent == attackerHash);
	}

	public static void markVoidDungeonPvp(Player attacker, Player victim) {
		if (!appliesVoidDungeonPvpRule(attacker, victim)) {
			return;
		}

		long currentTick = attacker.getWorld().getServer().getCurrentTick();
		attacker.setAttribute(VOID_DUNGEON_PVP_OPPONENT_ATTRIBUTE, victim.getUsernameHash());
		attacker.setAttribute(VOID_DUNGEON_PVP_TICK_ATTRIBUTE, currentTick);
		victim.setAttribute(VOID_DUNGEON_PVP_OPPONENT_ATTRIBUTE, attacker.getUsernameHash());
		victim.setAttribute(VOID_DUNGEON_PVP_TICK_ATTRIBUTE, currentTick);
	}

	public static String voidDungeonPvpMessage() {
		return VOID_DUNGEON_PVP_MESSAGE;
	}

	private static boolean isInVoidDungeonUnderground(Player player) {
		return player.getLocation() != null && player.getLocation().inVoidDungeonUnderground();
	}

	private static Long getVoidDungeonPvpOpponentHash(Player player) {
		if (player.inCombat() && player.getOpponent() != null && player.getOpponent().isPlayer()) {
			return ((Player) player.getOpponent()).getUsernameHash();
		}

		Long opponentHash = player.getAttribute(VOID_DUNGEON_PVP_OPPONENT_ATTRIBUTE, null);
		Long lastAttackTick = player.getAttribute(VOID_DUNGEON_PVP_TICK_ATTRIBUTE, null);
		if (opponentHash == null || lastAttackTick == null) {
			return null;
		}

		long currentTick = player.getWorld().getServer().getCurrentTick();
		long expiryTick = lastAttackTick + Math.max(0, player.getConfig().PVP_REATTACK_TIMER);
		if (expiryTick > currentTick) {
			return opponentHash;
		}

		player.removeAttribute(VOID_DUNGEON_PVP_OPPONENT_ATTRIBUTE);
		player.removeAttribute(VOID_DUNGEON_PVP_TICK_ATTRIBUTE);
		return null;
	}

	public static boolean canUseItem(Player player, Item item) {
		if (item == null) {
			return true;
		}
		return canUseItem(player, item.getDef(player.getWorld()), item.getCatalogId());
	}

	public static boolean canUseItem(Player player, ItemDefinition def, int itemId) {
		if (def == null || !def.isMembersOnly()) {
			return true;
		}
		if (player == null || !player.getConfig().MEMBER_WORLD) {
			return false;
		}
		if (!isF2PWilderness(player.getLocation())) {
			return true;
		}
		return isAllowedVoidscapeF2PWildernessItem(def, itemId);
	}

	public static boolean canUseItemAt(boolean memberWorld, Point location, ItemDefinition def, int itemId) {
		if (def == null || !def.isMembersOnly()) {
			return true;
		}
		if (!memberWorld) {
			return false;
		}
		if (!isF2PWilderness(location)) {
			return true;
		}
		return isAllowedVoidscapeF2PWildernessItem(def, itemId);
	}

	/**
	 * Ground loot follows world membership, not the local Wilderness use rules.
	 * Players may carry members loot out of F2P Wilderness, but cannot use it there.
	 */
	public static boolean canAppearAsGroundLoot(boolean memberWorld, ItemDefinition def) {
		return def == null || !def.isMembersOnly() || memberWorld;
	}

	public static boolean canUseSpell(Player player, SpellDef spell) {
		if (spell == null || !spell.isMembers()) {
			return true;
		}
		return player != null && player.getConfig().MEMBER_WORLD && !isF2PWilderness(player.getLocation());
	}

	public static boolean isAllowedVoidscapeF2PWildernessItem(ItemDefinition def, int itemId) {
		switch (ItemId.getById(itemId)) {
			case ASHES:
			case SODA_ASH:
			case IBANS_ASHES:
			case BONES:
			case BAT_BONES:
			case BIG_BONES:
			case DRAGON_BONES:
			case SPOOKIES_BONES:
			case SCARIES_BONES:
			case NECRONOMICON:
			case ZOMBITE_AMULET:
			case BOOMSTICK:
			case VOID_SCIMITAR:
			case VOID_BOW:
			case VOID_AMULET:
			case VOID_MACE:
			case VOID_KEY:
			case VOID_SPARROW:
			case WARM_ASHES:
			case BRIGHT_ASHES:
			case SACRED_ASHES:
			case BLESSED_ASHES:
			case VOID_ASHES:
				return true;
			default:
				break;
		}

		if (def == null || def.getName() == null) {
			return false;
		}
		String name = def.getName().toLowerCase(Locale.ROOT);
		return name.contains("void") || name.contains("ash") || name.contains("ashes");
	}

	public static String wildernessMemberMessage() {
		return "Members content can only be used outside the F2P wilderness.";
	}
}
