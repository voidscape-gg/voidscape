package com.openrsc.server.appearance;

import com.openrsc.server.database.DatabaseType;
import com.openrsc.server.model.PlayerAppearance;

/** Dependency-light truth table for the disposable Paperdoll V2 QA server. */
public final class PaperdollV2EvaluationPolicy {
	private PaperdollV2EvaluationPolicy() { }

	public static String configurationRejectionReason(int maximum,
		boolean jvmGate, DatabaseType databaseType, String databaseName,
		boolean productionCommandLockdown, boolean avatarGenerator,
		int characterCreationMode) {
		if (maximum == 0 && !jvmGate) return "";
		if (maximum != 6 || !jvmGate) {
			return "Paperdoll V2 evaluation requires both the exact config maximum 6 "
				+ "and -Dvoidscape.paperdollV2.evaluationServer=true";
		}
		if (databaseType != DatabaseType.SQLITE || databaseName == null
			|| !databaseName.endsWith("_qa")) {
			return "Paperdoll V2 evaluation requires a disposable SQLite *_qa database";
		}
		if (productionCommandLockdown) {
			return "Paperdoll V2 evaluation refuses production command lockdown mode";
		}
		if (avatarGenerator) {
			return "Paperdoll V2 evaluation requires avatar_generator=false";
		}
		if (characterCreationMode != 0) {
			return "Paperdoll V2 evaluation requires character_creation_mode=0";
		}
		return "";
	}

	public static boolean configurationEnabled(int maximum, boolean jvmGate,
		DatabaseType databaseType, String databaseName,
		boolean productionCommandLockdown, boolean avatarGenerator,
		int characterCreationMode) {
		return maximum == 6 && jvmGate
			&& configurationRejectionReason(maximum, jvmGate, databaseType, databaseName,
				productionCommandLockdown, avatarGenerator, characterCreationMode)
				.length() == 0;
	}

	public static String appearanceBlockReason(boolean evaluationEnabled,
		boolean developer, int[] worn) {
		if (!evaluationEnabled || !developer) return "";
		if (worn == null || worn.length < 3) {
			return "player-appearance-state-unavailable";
		}
		if (worn[0] <= 0) return "base-head-hidden-by-equipment";
		if (worn[1] != PlayerAppearance.PAPERDOLL_V2_BASE_MALE_BODY
			&& worn[1] != PlayerAppearance.PAPERDOLL_V2_BASE_FEMALE_BODY) {
			return "base-body-hidden-by-equipment";
		}
		if (worn[2] != PlayerAppearance.PAPERDOLL_V2_BASE_LEGS) {
			return "base-legs-hidden-by-equipment";
		}
		return "";
	}
}
