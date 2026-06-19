package com.openrsc.server.model.container;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ItemKillLog {

	private final Map<Integer, Integer> npcKills = new LinkedHashMap<>();
	private int pvpKills;

	private ItemKillLog() {
	}

	public static ItemKillLog parse(String encoded) {
		ItemKillLog log = new ItemKillLog();
		if (encoded == null || encoded.isEmpty()) {
			return log;
		}
		try {
			int sep = encoded.indexOf(';');
			String npcPart = sep >= 0 ? encoded.substring(0, sep) : encoded;
			String pvpPart = sep >= 0 ? encoded.substring(sep + 1) : "";
			if (!npcPart.isEmpty()) {
				for (String pair : npcPart.split(",")) {
					int colon = pair.indexOf(':');
					if (colon <= 0) continue;
					int id = Integer.parseInt(pair.substring(0, colon));
					int count = Integer.parseInt(pair.substring(colon + 1));
					if (count > 0) {
						log.npcKills.merge(id, count, Integer::sum);
					}
				}
			}
			if (!pvpPart.isEmpty()) {
				log.pvpKills = Math.max(0, Integer.parseInt(pvpPart));
			}
		} catch (NumberFormatException ex) {
			return new ItemKillLog();
		}
		return log;
	}

	public String serialize() {
		if (npcKills.isEmpty() && pvpKills == 0) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (Map.Entry<Integer, Integer> e : npcKills.entrySet()) {
			if (!first) sb.append(',');
			sb.append(e.getKey()).append(':').append(e.getValue());
			first = false;
		}
		sb.append(';').append(pvpKills);
		return sb.toString();
	}

	public void incrementNpc(int npcId) {
		npcKills.merge(npcId, 1, Integer::sum);
	}

	public void incrementPvp() {
		pvpKills++;
	}

	public Map<Integer, Integer> getNpcKills() {
		return npcKills;
	}

	public int getPvpKills() {
		return pvpKills;
	}

	public boolean isEmpty() {
		return npcKills.isEmpty() && pvpKills == 0;
	}

	public static void recordNpcKillOn(Item item, int npcId) {
		ItemKillLog log = parse(item.getItemStatus().getKillLog());
		log.incrementNpc(npcId);
		item.getItemStatus().setKillLog(log.serialize());
	}

	public static void recordPvpKillOn(Item item) {
		ItemKillLog log = parse(item.getItemStatus().getKillLog());
		log.incrementPvp();
		item.getItemStatus().setKillLog(log.serialize());
	}
}
