package com.openrsc.server.content.voidarena;

import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.container.ItemStatus;
import com.openrsc.server.model.entity.player.Player;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;

final class VoidArenaKitSnapshot {
	private static final int VERSION = 1;

	static String capture(Player player) {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			DataOutputStream out = new DataOutputStream(bytes);
			out.writeInt(VERSION);

			int[] worn = player.getWornItems();
			out.writeInt(worn.length);
			for (int wornItem : worn) {
				out.writeInt(wornItem);
			}

			synchronized (player.getCarriedItems().getInventory().getItems()) {
				out.writeInt(player.getCarriedItems().getInventory().getItems().size());
				for (Item item : player.getCarriedItems().getInventory().getItems()) {
					writeItem(out, item);
				}
			}

			synchronized (player.getCarriedItems().getEquipment().getList()) {
				Item[] equipment = player.getCarriedItems().getEquipment().getList();
				out.writeInt(equipment.length);
				for (Item item : equipment) {
					out.writeBoolean(item != null);
					if (item != null) {
						writeItem(out, item);
					}
				}
			}

			out.flush();
			return Base64.getEncoder().encodeToString(bytes.toByteArray());
		} catch (IOException e) {
			throw new IllegalStateException("Unable to capture Void Arena kit snapshot", e);
		}
	}

	static void restore(Player player, String encoded) {
		try {
			DataInputStream in = new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)));
			int version = in.readInt();
			if (version != VERSION) {
				throw new IllegalArgumentException("Unsupported Void Arena kit snapshot version " + version);
			}

			int wornLength = in.readInt();
			int[] worn = new int[wornLength];
			for (int i = 0; i < wornLength; i++) {
				worn[i] = in.readInt();
			}

			clearContainers(player);

			int inventoryCount = in.readInt();
			synchronized (player.getCarriedItems().getInventory().getItems()) {
				for (int i = 0; i < inventoryCount; i++) {
					player.getCarriedItems().getInventory().getItems().add(readItem(in));
				}
			}

			int equipmentCount = in.readInt();
			synchronized (player.getCarriedItems().getEquipment().getList()) {
				Item[] equipment = player.getCarriedItems().getEquipment().getList();
				for (int i = 0; i < equipmentCount; i++) {
					boolean present = in.readBoolean();
					if (i < equipment.length) {
						equipment[i] = present ? readItem(in) : null;
					} else if (present) {
						readItem(in);
					}
				}
			}

			player.setWornItems(worn);
		} catch (IOException | IllegalArgumentException e) {
			throw new IllegalStateException("Unable to restore Void Arena kit snapshot", e);
		}
	}

	static void clearContainers(Player player) {
		synchronized (player.getCarriedItems().getInventory().getItems()) {
			player.getCarriedItems().getInventory().getItems().clear();
		}
		synchronized (player.getCarriedItems().getEquipment().getList()) {
			Arrays.fill(player.getCarriedItems().getEquipment().getList(), null);
		}
		player.setWornItems(player.getSettings().getAppearance().getSprites());
	}

	private static void writeItem(DataOutputStream out, Item item) throws IOException {
		ItemStatus status = item.getItemStatus();
		out.writeLong(item.getItemId());
		out.writeInt(status.getCatalogId());
		out.writeInt(status.getAmount());
		out.writeBoolean(status.getNoted());
		out.writeBoolean(status.isWielded());
		out.writeInt(status.getDurability());
		String killLog = status.getKillLog();
		out.writeBoolean(killLog != null);
		if (killLog != null) {
			out.writeUTF(killLog);
		}
	}

	private static Item readItem(DataInputStream in) throws IOException {
		long itemId = in.readLong();
		ItemStatus status = new ItemStatus();
		status.setCatalogId(in.readInt());
		status.setAmount(in.readInt());
		status.setNoted(in.readBoolean());
		status.setWielded(in.readBoolean());
		status.setDurability(in.readInt());
		if (in.readBoolean()) {
			status.setKillLog(in.readUTF());
		}
		return new Item(itemId, status);
	}

	private VoidArenaKitSnapshot() {
	}
}
