package com.openrsc.server.login;

import com.openrsc.server.util.PlayerList;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class PlayerSaveRequestPolicyTest {
	private PlayerSaveRequestPolicyTest() {
	}

	public static void main(String[] args) throws Exception {
		check(PlayerSaveRequest.mayWrite(true, true, false, true), "current live incarnation may write");
		check(!PlayerSaveRequest.mayWrite(false, true, false, true), "stale lifecycle may not write");
		check(!PlayerSaveRequest.mayWrite(true, false, false, true), "logged-out incarnation may not write");
		check(!PlayerSaveRequest.mayWrite(true, true, true, true), "removed incarnation may not write");
		check(!PlayerSaveRequest.mayWrite(true, true, false, false), "replaced world instance may not write");
		check(!PlayerSaveRequest.mayFinalizeLogout(false, true, true), "failed fifth write cannot finalize logout");
		check(!PlayerSaveRequest.mayFinalizeLogout(true, false, true), "stale commit cannot remove replacement");
		check(!PlayerSaveRequest.mayFinalizeLogout(true, true, false),
			"logout snapshot predating a force mutation cannot remove the player");
		check(PlayerSaveRequest.mayFinalizeLogout(true, true, true),
			"committed current latest-generation logout may finalize");

		assertSynchronized("getPlayerByHash", long.class);
		assertSynchronized("removePlayerByHash", long.class);
		assertSynchronized("isCurrent", com.openrsc.server.model.entity.player.Player.class);
		assertSynchronized("removeIfCurrent", com.openrsc.server.model.entity.player.Player.class);
		System.out.println("Player save lifecycle policy tests passed.");
	}

	private static void assertSynchronized(String methodName, Class<?>... parameters) throws Exception {
		Method method = PlayerList.class.getDeclaredMethod(methodName, parameters);
		check(Modifier.isSynchronized(method.getModifiers()), methodName + " is an atomic PlayerList fence");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
