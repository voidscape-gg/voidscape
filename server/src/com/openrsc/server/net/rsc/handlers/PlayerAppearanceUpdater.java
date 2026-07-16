package com.openrsc.server.net.rsc.handlers;

import com.openrsc.server.content.PlayerClass;
import com.openrsc.server.content.BetaReferralReward;
import com.openrsc.server.content.GlobalChatCountryFlags;
import com.openrsc.server.content.VoidOnboarding;
import com.openrsc.server.content.VoidPath;
import com.openrsc.server.content.VoidStarterIntro;
import com.openrsc.server.plugins.triggers.VoidWelcomeTrigger;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.PlayerAppearance;
import com.openrsc.server.model.container.Inventory;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.struct.UnequipRequest;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.net.rsc.PayloadProcessor;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.struct.incoming.PlayerAppearanceStruct;

public class PlayerAppearanceUpdater implements PayloadProcessor<PlayerAppearanceStruct, OpcodeIn> {
	public static int decodeUnsignedAppearanceId(byte encodedAppearanceId) {
		return (encodedAppearanceId & 0xFF) + 1;
	}

	public static String paperdollV2EvaluationRejectionReason(boolean evaluationEnabled,
		boolean developer, int clientVersion, int headRestrictions, int hairStyle,
		int headSprite, int bodySprite) {
		if (hairStyle <= 0) return "";
		if (!evaluationEnabled) return "evaluation-server-disabled";
		if (!developer) return "developer-role-required";
		if (clientVersion < PlayerAppearance.MODERN_HAIR_CLIENT_VERSION) {
			return "modern-hair-client-required";
		}
		if (headRestrictions != 1 && headRestrictions != 2) {
			return "invalid-gender-restriction";
		}
		boolean male = headRestrictions == 1;
		return PlayerAppearance.isPaperdollV2EvaluationIdentity(hairStyle,
			headSprite, bodySprite, male) ? "" : "selector-base-identity-mismatch";
	}

	public void process(PlayerAppearanceStruct payload, Player player) throws Exception {

		if (!player.isChangingAppearance()) {
			player.setSuspiciousPlayer(true, "player appearance packet without changing appearance");
			return;
		}

		byte headRestrictions = payload.headRestrictions;
		byte headType = payload.headType;
		byte bodyType = payload.bodyType;

		// Check to see if we've been sent a bearded lady
		if (!player.getConfig().ALLOW_BEARDED_LADIES
			&& (headType == 6 && bodyType == 4)) {
			player.setSuspiciousPlayer(true, "player attempted to create a bearded lady");
			ActionSender.sendAppearanceScreen(player);
			return;
		}

		boolean tutorialAppearance = player.getCache().hasKey("tutorial_appearance");
		if (tutorialAppearance)
			player.getCache().remove("tutorial_appearance");

		player.setChangingAppearance(false);

		// This value is always "2" and is not very useful.
		// I looked in the  v40 client deob, and the 4th byte is also always 2 there.
		// I looked in the v127 client deob, and the 4th byte is also always 2 there.
		// I looked in the v204 client deob, and the 4th byte is also always 2 there.
		// I looked in the v233 client deob, and the 4th byte is also always 2 there.
		byte mustEqual2 = payload.mustEqual2;
		if (mustEqual2 != 2) {
			player.setSuspiciousPlayer(true, "4th byte of player appearance packet wasn't equal to 2");
			return;
		}

		int hairColour = payload.hairColour;
		int topColour = payload.topColour;
		int trouserColour = payload.trouserColour;
		int skinColour = payload.skinColour;
		int hairStyle = payload.hairStyle;
		int ironmanMode = payload.ironmanMode; // custom protocol
		int isOneXp = payload.isOneXp; // custom protocol

		// This existing wire byte contains appearanceId - 1. Decode it unsigned so
		// stable selectable head IDs can use the full legacy range 1 through 256.
		int headSprite = decodeUnsignedAppearanceId(headType);
		int bodySprite = bodyType + 1;
		boolean maleAppearance = headRestrictions == 1;
		boolean positiveHairStyle = hairStyle > 0;
		String evaluationRejection = paperdollV2EvaluationRejectionReason(
			player.getConfig().isPaperdollV2EvaluationEnabled(), player.isDev(),
			player.getClientVersion(), headRestrictions, hairStyle, headSprite, bodySprite);
		if (evaluationRejection.length() > 0) {
			player.setSuspiciousPlayer(true,
				"player selected unavailable Paperdoll V2 evaluation appearance: "
					+ evaluationRejection);
			return;
		}

		PlayerAppearance appearance = positiveHairStyle
			? PlayerAppearance.forPaperdollV2Evaluation(hairColour, topColour,
				trouserColour, skinColour, headSprite, bodySprite, hairStyle)
			: new PlayerAppearance(hairColour, topColour, trouserColour,
				skinColour, headSprite, bodySprite, hairStyle);
		if (!appearance.isValid(player)) {
			player.setSuspiciousPlayer(true, "player invalid appearance");
			return;
		}

		player.setMale(maleAppearance);

		if (player.isMale()) {
			if (player.getConfig().WANT_EQUIPMENT_TAB) {
				Item top = player.getCarriedItems().getEquipment().get(1);
				if (top != null && top.getDef(player.getWorld()).isFemaleOnly()) {
					if(!player.getCarriedItems().getEquipment().unequipItem(new UnequipRequest(player, top, UnequipRequest.RequestType.FROM_EQUIPMENT, false))) {
						player.getCarriedItems().getEquipment().unequipItem(new UnequipRequest(player, top, UnequipRequest.RequestType.FROM_BANK, false));
					}
					ActionSender.sendEquipmentStats(player, 1);
				}
			} else {
				Inventory inv = player.getCarriedItems().getInventory();
				for (int slot = 0; slot < inv.size(); slot++) {
					Item i = inv.get(slot);
					if (i.isWieldable(player.getWorld()) && i.getDef(player.getWorld()).getWieldPosition() == 1
						&& i.isWielded() && i.getDef(player.getWorld()).isFemaleOnly()) {
						player.getCarriedItems().getEquipment().unequipItem(new UnequipRequest(player, i, UnequipRequest.RequestType.FROM_INVENTORY, false));
						ActionSender.sendInventoryUpdateItem(player, slot);
						break;
					}
				}
			}
		}
		int[] oldWorn = player.getWornItems();
		int[] oldAppearance = player.getSettings().getAppearance().getSprites();
		player.getSettings().setAppearance(appearance);
		int[] newAppearance = player.getSettings().getAppearance().getSprites();
		for (int i = 0; i < 12; i++) {
			if (oldWorn[i] == oldAppearance[i]) {
				player.updateWornItems(i, newAppearance[i]);
			}
		}

		if (payload.countryCodePresent) {
			GlobalChatCountryFlags.setSelectedCountryCode(player, payload.countryCode);
		}

		if (player.getLastLogin() == 0L || tutorialAppearance) {
			if (player.getConfig().USES_CLASSES) {
				new PlayerClass(player, payload.chosenClass).init();
				player.getWorld().getServer().getPlayerService().savePlayerMaxStats(player);
			}

			if (player.getConfig().USES_PK_MODE) {
				player.setPkMode(payload.pkMode);
				player.setPkChanges(2);
				ActionSender.sendGameSettings(player);
			}

			if (player.getConfig().CHARACTER_CREATION_MODE == 1) {
				player.setIronMan(ironmanMode);
				player.setOneXp(isOneXp == 1);
			}

			if (player.getLastLogin() == 0L) {
				BetaReferralReward.creditFromAppearance(player, payload.referralName);
				player.getWorld().getWorldAnnouncementService().announceNewPlayerJoined(player);
			}

			if (!VoidPath.hasChosen(player)) {
				Point entryPoint = VoidStarterIntro.entryPoint(player);
				player.teleport(entryPoint.getX(), entryPoint.getY(), false);
				if (VoidOnboarding.needsWelcome(player)) {
					// Blocking dialogue must run in plugin context, never here (packet context).
					player.getWorld().getServer().getPluginHandler()
						.handlePlugin(VoidWelcomeTrigger.class, player, new Object[]{player});
				} else {
					VoidStarterIntro.prompt(player);
				}
			}
		}
	}
}
