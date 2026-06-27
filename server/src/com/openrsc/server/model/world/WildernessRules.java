package com.openrsc.server.model.world;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.external.ItemDefinition;
import com.openrsc.server.external.SpellDef;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;

import java.util.Locale;

public final class WildernessRules {
	private WildernessRules() {
	}

	public static boolean isF2PWilderness(Point point) {
		return point != null && point.inWilderness() && !point.isMembersWild();
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
