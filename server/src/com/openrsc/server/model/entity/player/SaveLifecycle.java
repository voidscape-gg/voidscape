package com.openrsc.server.model.entity.player;

import java.util.UUID;

final class SaveLifecycle {
	private SaveLifecycle() {
	}

	static UUID newId() {
		return UUID.randomUUID();
	}
}
