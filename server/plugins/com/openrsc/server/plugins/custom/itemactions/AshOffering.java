package com.openrsc.server.plugins.custom.itemactions;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.SceneryId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.UseLocTrigger;
import com.openrsc.server.util.rsc.MessageType;

import java.util.Optional;

import static com.openrsc.server.plugins.Functions.*;

public class AshOffering implements UseLocTrigger {
	private static final int[] PRAYER_ALTARS = {
		SceneryId.ALTAR.id(),
		SceneryId.ALTAR_ZAMORAK.id(),
		SceneryId.ALTAR_MONKS.id(),
		SceneryId.ALTAR_OF_GUTHIX.id(),
		SceneryId.ALTAR_ZAMORAK_WILDERNESS.id(),
		SceneryId.CHAOS_ALTAR.id(),
		SceneryId.ALTAR_ZAMORAK_REPLACED.id()
	};

	@Override
	public boolean blockUseLoc(Player player, GameObject obj, Item item) {
		return isPrayerAltar(obj.getID()) && isAsh(item.getCatalogId());
	}

	@Override
	public void onUseLoc(Player player, GameObject obj, Item item) {
		if (item.getNoted()) {
			player.message("You can't offer noted ashes");
			return;
		}

		int offerAmount = 1;
		if (config().BATCH_PROGRESSION) {
			offerAmount = player.getCarriedItems().getInventory().countId(item.getCatalogId(), Optional.of(false));
		}

		int prayerSkillId = player.getConfig().DIVIDED_GOOD_EVIL
			? Skill.PRAYGOOD.id()
			: Skill.PRAYER.id();
		startskillbatch(offerAmount, prayerSkillId);
		offerAsh(player, item.getCatalogId());
	}

	private void offerAsh(Player player, int ashId) {
		Item ash = player.getCarriedItems().getInventory().get(
			player.getCarriedItems().getInventory().getLastIndexById(ashId, Optional.of(false)));
		if (ash == null) {
			return;
		}

		thinkbubble(ash);
		player.playerServerMessage(MessageType.QUEST, "You offer the ashes at the altar");
		delay(2);
		if (player.getCarriedItems().remove(ash) != -1) {
			givePrayerExperience(player, ashId);
		}

		updatebatch();
		if (!ifinterrupted() && !isbatchcomplete()) {
			offerAsh(player, ashId);
		}
	}

	private void givePrayerExperience(Player player, int ashId) {
		int xp = getPrayerXp(ashId);
		if (xp <= 0) {
			player.message("Nothing interesting happens");
			return;
		}

		int[] prayerSkillIds = player.getConfig().DIVIDED_GOOD_EVIL
			? new int[]{Skill.PRAYGOOD.id(), Skill.PRAYEVIL.id()}
			: new int[]{Skill.PRAYER.id()};
		for (int prayerSkillId : prayerSkillIds) {
			player.incExp(prayerSkillId, xp, true);
		}
	}

	private int getPrayerXp(int ashId) {
		switch (ItemId.getById(ashId)) {
			case ASHES:
				return 4;
			case WARM_ASHES:
				return 8;
			case BRIGHT_ASHES:
				return 14;
			case SACRED_ASHES:
				return 24;
			case BLESSED_ASHES:
				return 40;
			case VOID_ASHES:
				return 70;
			default:
				return 0;
		}
	}

	private boolean isPrayerAltar(int objectId) {
		return inArray(objectId, PRAYER_ALTARS);
	}

	private boolean isAsh(int itemId) {
		switch (ItemId.getById(itemId)) {
			case ASHES:
			case WARM_ASHES:
			case BRIGHT_ASHES:
			case SACRED_ASHES:
			case BLESSED_ASHES:
			case VOID_ASHES:
				return true;
			default:
				return false;
		}
	}
}
