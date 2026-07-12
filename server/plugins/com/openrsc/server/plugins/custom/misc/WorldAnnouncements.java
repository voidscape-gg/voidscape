package com.openrsc.server.plugins.custom.misc;

import com.openrsc.server.content.WorldPkEvaluation;
import com.openrsc.server.content.WorldPkSettlementRequest;
import com.openrsc.server.content.WorldPkSettlementResult;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.PlayerDeathContext;
import com.openrsc.server.model.entity.player.PvpKillEvidence;
import com.openrsc.server.plugins.triggers.PlayerDeathDropTrigger;
import com.openrsc.server.plugins.triggers.PlayerKilledPlayerTrigger;

public final class WorldAnnouncements implements PlayerKilledPlayerTrigger,
	PlayerDeathDropTrigger {
	@Override
	public void onPlayerKilledPlayer(Player killer, Player killed) {
		// Passive trigger: blockPlayerKilledPlayer preserves the legacy disabled-mode event.
	}

	@Override
	public boolean blockPlayerKilledPlayer(Player killer, Player killed) {
		if (!killer.getConfig().WANT_WORLD_ACHIEVEMENTS) {
			killer.getWorld().getWorldAnnouncementService()
				.announceSkulledWildernessKill(killer, killed);
		}
		return false;
	}

	@Override
	public void onPlayerDeathDrop(Player player, PlayerDeathContext context) {
		// Passive trigger: blockPlayerDeathDrop settles after the complete drop list exists.
	}

	@Override
	public boolean blockPlayerDeathDrop(Player player, PlayerDeathContext context) {
		if (!player.getConfig().WANT_WORLD_ACHIEVEMENTS || context == null) {
			return false;
		}
		final PvpKillEvidence evidence = context.getPvpKillEvidence();
		if (evidence == null) {
			return false;
		}

		final String preliminaryRejectReason =
			WorldPkEvaluation.preliminaryRejectReason(evidence);
		final long lootValue = WorldPkEvaluation.eligibleLootValue(
			evidence, context.getGroundItems());
		final WorldPkSettlementRequest request = new WorldPkSettlementRequest(
			evidence.getCanonicalDeathId(),
			evidence.getKillerPlayerId(), evidence.getKillerAccountId(),
			evidence.getKillerName(), evidence.getVictimPlayerId(),
			evidence.getVictimAccountId(), evidence.getVictimName(),
			evidence.wasVictimSkulled(), evidence.getVictimDamageToKiller(),
			lootValue, evidence.getWildernessLevel(), evidence.getOccurredAtMs(),
			preliminaryRejectReason);
		final WorldPkSettlementResult result = player.getWorld()
			.getWorldPkSettlementService().settle(request);
		if (result.isPublishable() && result.isQualified()) {
			player.getWorld().getWorldAnnouncementService()
				.announceQualifiedWildernessKill(result);
		}
		return false;
	}
}
