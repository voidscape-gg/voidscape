package com.openrsc.server.constants.custom;

import java.util.HashMap;
import java.util.Map;

public enum InterfaceOptions {

	SWAP_CERT(0), // market cert
	SWAP_NOTE(1), // bank cert
	BANK_SWAP(2),
	BANK_INSERT(3),
	INVENTORY_SWAP(4),
	INVENTORY_INSERT(5),
	CANCEL_BATCH(6),
	IRONMAN_MODE(7),
	BANK_PIN(8),
	INPUT_BOX_REPLY(9), // generic SEND_INPUT_BOX text reply
	AUCTION(10),
	CLAN(11),
	PARTY(12),
	POINTS(13),
	BANK_CLEAR_PRESET(14),
	ACCOUNT_VALIDATE(15),
	BESTIARY_REQUEST(16),
	XP_LOCK(17),
	VOID_ARENA(18),
	DUEL_PROOF(19);

	private int option;

	private static final Map<Integer, InterfaceOptions> byId = new HashMap<Integer, InterfaceOptions>();

	static {
		for (InterfaceOptions option : InterfaceOptions.values()) {
			if (byId.put(option.id(), option) != null) {
				throw new IllegalArgumentException("duplicate id: " + option.id());
			}
		}
	}

	public static InterfaceOptions getById(Integer id) {
		return byId.getOrDefault(id, null);
	}

	InterfaceOptions(int option) {
		this.option = option;
	}

	public int id() {
		return this.option;
	}
}
