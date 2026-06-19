package com.openrsc.server.plugins.custom.misc;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.OpLocTrigger;
import com.openrsc.server.util.rsc.MessageType;

import static com.openrsc.server.plugins.Functions.*;

public class VoidEnclaveHealingPool implements OpLocTrigger {

	private static final int HEALING_POOL_ID = 1296;
	private static final int MIN_X = 98, MAX_X = 128;
	private static final int MIN_Y = 300, MAX_Y = 330;

	private static boolean isEnclaveHealingPool(GameObject obj) {
		if (obj.getID() != HEALING_POOL_ID) return false;
		int x = obj.getX(), y = obj.getY();
		return x >= MIN_X && x <= MAX_X && y >= MIN_Y && y <= MAX_Y;
	}

	@Override
	public boolean blockOpLoc(Player player, GameObject obj, String command) {
		return isEnclaveHealingPool(obj);
	}

	@Override
	public void onOpLoc(Player player, GameObject obj, String command) {
		if (!isEnclaveHealingPool(obj)) return;

		int currentHits = getCurrentLevel(player, Skill.HITS.id());
		int maxHits = getMaxLevel(player, Skill.HITS.id());

		mes("You drink from the pool");
		delay(2);

		if (currentHits >= maxHits) {
			player.playerServerMessage(MessageType.QUEST, "The water is refreshing");
			return;
		}

		player.playerServerMessage(MessageType.QUEST, "Your wounds heal as the water touches your lips");
		player.playSound("recharge");
		boolean sendUpdate = player.getClientLimitations().supportsSkillUpdate;
		player.getSkills().setLevel(Skill.HITS.id(), maxHits, sendUpdate);
		if (!sendUpdate) {
			player.getSkills().sendUpdateAll();
		}
	}
}
