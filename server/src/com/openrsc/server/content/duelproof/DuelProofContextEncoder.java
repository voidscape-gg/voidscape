package com.openrsc.server.content.duelproof;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.constants.ItemId;
import com.openrsc.server.content.SkillCapes;
import com.openrsc.server.model.container.Equipment;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.voidscape.duelproof.DuelProofCodec;
import com.voidscape.duelproof.DuelProofSpec;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

/** Canonical v3 pre-combat identity, rules, stake, and initial-fighter snapshot. */
final class DuelProofContextEncoder {
	private static final byte[] MAGIC = DuelProofCodec.ascii("VSDPCTX3");

	private DuelProofContextEncoder() {
	}

	static byte[] encode(final Player canonicalFirst, final Player canonicalSecond,
						 final byte[] proofId) {
		if (canonicalFirst == null || canonicalSecond == null || canonicalFirst == canonicalSecond
			|| canonicalFirst.getDatabaseID() >= canonicalSecond.getDatabaseID()) {
			throw new IllegalArgumentException("duel proof players must be canonically ordered");
		}
		if (proofId == null || proofId.length != DuelProofSpec.PROOF_ID_BYTES) {
			throw new IllegalArgumentException("proofId must contain 16 bytes");
		}

		try {
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream(512);
			final DataOutputStream output = new DataOutputStream(bytes);
			output.write(MAGIC);
			output.writeInt(DuelProofSpec.CONTEXT_VERSION);
			output.writeInt(DuelProofSpec.PROTOCOL_VERSION);
			output.writeInt(DuelProofSpec.RNG_VERSION);
			output.writeInt(DuelProofSpec.CLASSIC_MELEE_FORMULA_VERSION);
			output.write(proofId);
			final int firstRuleMask = ruleMask(canonicalFirst);
			if (firstRuleMask != ruleMask(canonicalSecond)) {
				throw new IllegalArgumentException("duel proof rules must match for both players");
			}
			output.writeByte(firstRuleMask);
			final int recoilLimit = canonicalFirst.getWorld().getServer().getConfig()
				.RING_OF_RECOIL_LIMIT;
			if (recoilLimit <= 0 || recoilLimit != canonicalSecond.getWorld().getServer()
				.getConfig().RING_OF_RECOIL_LIMIT) {
				throw new IllegalArgumentException("duel proof recoil limit is invalid");
			}
			output.writeInt(recoilLimit);
			writeParticipant(output, canonicalFirst, 0);
			writeParticipant(output, canonicalSecond, 1);
			output.flush();
			return bytes.toByteArray();
		} catch (final IOException impossible) {
			throw new IllegalStateException("unable to encode in-memory duel context", impossible);
		}
	}

	private static int ruleMask(final Player player) {
		int mask = 0;
		for (int setting = 0; setting < 4; setting++) {
			if (player.getDuel().getDuelSetting(setting)) {
				mask |= 1 << setting;
			}
		}
		return mask;
	}

	private static void writeParticipant(final DataOutputStream output, final Player player,
									 final int ordinal) throws IOException {
		output.writeByte(ordinal);
		output.writeInt(player.getDatabaseID());
		writeAscii(output, player.getUsername());
		output.writeInt(player.getCombatLevel());

		writeSkill(output, player, Skill.ATTACK.id());
		writeSkill(output, player, Skill.DEFENSE.id());
		writeSkill(output, player, Skill.STRENGTH.id());
		writeSkill(output, player, Skill.HITS.id());
		writeSkill(output, player, Skill.PRAYER.id());
		output.writeByte(player.getCombatStyle());
		output.writeInt(player.getWeaponAimPoints());
		output.writeInt(player.getWeaponPowerPoints());
		output.writeInt(player.getArmourPoints());
		output.writeBoolean(SkillCapes.canActivate(player, ItemId.ATTACK_CAPE));
		output.writeBoolean(SkillCapes.canActivate(player, ItemId.STRENGTH_CAPE));
		output.writeBoolean(SkillCapes.canActivate(player, ItemId.DEFENSE_CAPE));

		int prayerMask = 0;
		final boolean[] activePrayers = player.getPrayers().getActivePrayers();
		for (int prayer = 0; prayer < activePrayers.length && prayer < 31; prayer++) {
			if (activePrayers[prayer]) {
				prayerMask |= 1 << prayer;
			}
		}
		output.writeInt(prayerMask);

		final Item[] equipment = logicalEquipment(player);
		output.writeByte(Equipment.SLOT_COUNT);
		synchronized (equipment) {
			for (int slot = 0; slot < Equipment.SLOT_COUNT; slot++) {
				final Item item = equipment[slot];
				output.writeInt(item == null ? -1 : item.getCatalogId());
				output.writeInt(item == null ? 0 : item.getAmount());
				output.writeBoolean(item != null && item.getNoted());
			}
		}
		final boolean recoilEquipped = player.getCarriedItems().getEquipment()
			.hasEquipped(ItemId.RING_OF_RECOIL.id());
		output.writeBoolean(recoilEquipped);
		output.writeInt(recoilRemaining(player, recoilEquipped));

		final List<Item> offered = player.getDuel().getDuelOffer().getItems();
		synchronized (offered) {
			output.writeByte(offered.size());
			for (int slot = 0; slot < offered.size(); slot++) {
				final Item item = offered.get(slot);
				output.writeByte(slot);
				output.writeInt(item.getCatalogId());
				output.writeInt(item.getAmount());
				output.writeBoolean(item.getNoted());
			}
		}
	}

	/** Normalizes classic inventory-wielded and equipment-tab worlds into the same 14 slots. */
	private static Item[] logicalEquipment(final Player player) {
		final Item[] logical = new Item[Equipment.SLOT_COUNT];
		if (player.getConfig().WANT_EQUIPMENT_TAB) {
			final Item[] equipment = player.getCarriedItems().getEquipment().getList();
			synchronized (equipment) {
				System.arraycopy(equipment, 0, logical, 0,
					Math.min(equipment.length, logical.length));
			}
			return logical;
		}

		final List<Item> inventory = player.getCarriedItems().getInventory().getItems();
		synchronized (inventory) {
			for (final Item item : inventory) {
				if (item == null || !item.isWielded() || item.getDef(player.getWorld()) == null) {
					continue;
				}
				final int slot = item.getDef(player.getWorld()).getWieldPosition();
				if (slot < 0 || slot >= logical.length || logical[slot] != null) {
					throw new IllegalArgumentException("duel proof equipment is not canonical");
				}
				logical[slot] = item;
			}
		}
		return logical;
	}

	private static int recoilRemaining(final Player player, final boolean recoilEquipped) {
		if (!recoilEquipped) {
			return 0;
		}
		final int limit = player.getWorld().getServer().getConfig().RING_OF_RECOIL_LIMIT;
		final int used = player.getCache().hasKey("ringofrecoil")
			? player.getCache().getInt("ringofrecoil") : 0;
		if (limit <= 0 || used < 0 || used >= limit) {
			throw new IllegalArgumentException("duel proof recoil state is invalid");
		}
		return limit - used;
	}

	private static void writeSkill(final DataOutputStream output, final Player player,
								   final int skill) throws IOException {
		output.writeInt(player.getSkills().getLevel(skill));
		output.writeInt(player.getSkills().getMaxStat(skill));
	}

	private static void writeAscii(final DataOutputStream output, final String value) throws IOException {
		final byte[] encoded = DuelProofCodec.ascii(value == null ? "" : value);
		if (encoded.length > 255) {
			throw new IllegalArgumentException("duel proof text field is too long");
		}
		output.writeByte(encoded.length);
		output.write(encoded);
	}
}
