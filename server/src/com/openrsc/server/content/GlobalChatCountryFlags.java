package com.openrsc.server.content;

import com.openrsc.server.model.entity.player.Player;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class GlobalChatCountryFlags {
	public static final int CLIENT_VERSION = 10069;
	public static final int COUNTRY_PICKER_CLIENT_VERSION = 10125;
	public static final int SETTING_SHOW_COUNTRY_FLAG = 56;

	private static final String CACHE_SHOW_FLAG = "setting_global_chat_country_flag";
	private static final String CACHE_CODE = "global_chat_country_code";
	private static final String CACHE_LEGACY_IP = "global_chat_country_ip";

	private static final String[][] COUNTRY_OPTIONS = {
		{"", "None"},
		{"US", "United States"},
		{"CA", "Canada"},
		{"GB", "United Kingdom"},
		{"AU", "Australia"},
		{"NZ", "New Zealand"},
		{"IE", "Ireland"},
		{"DE", "Germany"},
		{"FR", "France"},
		{"NL", "Netherlands"},
		{"BE", "Belgium"},
		{"DK", "Denmark"},
		{"NO", "Norway"},
		{"SE", "Sweden"},
		{"FI", "Finland"},
		{"IS", "Iceland"},
		{"ES", "Spain"},
		{"PT", "Portugal"},
		{"IT", "Italy"},
		{"CH", "Switzerland"},
		{"AT", "Austria"},
		{"PL", "Poland"},
		{"CZ", "Czechia"},
		{"SK", "Slovakia"},
		{"HU", "Hungary"},
		{"RO", "Romania"},
		{"BG", "Bulgaria"},
		{"GR", "Greece"},
		{"TR", "Turkey"},
		{"UA", "Ukraine"},
		{"LT", "Lithuania"},
		{"LV", "Latvia"},
		{"EE", "Estonia"},
		{"BR", "Brazil"},
		{"AR", "Argentina"},
		{"CL", "Chile"},
		{"CO", "Colombia"},
		{"MX", "Mexico"},
		{"JP", "Japan"},
		{"KR", "South Korea"},
		{"CN", "China"},
		{"HK", "Hong Kong"},
		{"TW", "Taiwan"},
		{"SG", "Singapore"},
		{"MY", "Malaysia"},
		{"PH", "Philippines"},
		{"ID", "Indonesia"},
		{"TH", "Thailand"},
		{"VN", "Vietnam"},
		{"IN", "India"},
		{"PK", "Pakistan"},
		{"BD", "Bangladesh"},
		{"ZA", "South Africa"},
		{"EG", "Egypt"},
		{"MA", "Morocco"},
		{"NG", "Nigeria"},
		{"KE", "Kenya"},
		{"IL", "Israel"},
		{"AE", "United Arab Emirates"},
		{"SA", "Saudi Arabia"}
	};

	private static final Set<String> VALID_COUNTRY_CODES = buildValidCountryCodes();

	private GlobalChatCountryFlags() {
	}

	public static boolean shouldShow(Player player) {
		if (!player.getConfig().WANT_GLOBAL_CHAT_COUNTRY_FLAGS) {
			return false;
		}
		if (player.getCache().hasKey(CACHE_SHOW_FLAG)) {
			return player.getCache().getBoolean(CACHE_SHOW_FLAG);
		}
		return true;
	}

	public static String flagTokenFor(Player player) {
		if (!shouldShow(player)) {
			return "";
		}
		String code = selectedCountryCode(player);
		return code.isEmpty() ? "" : "@flg@" + code + " ";
	}

	public static void setShow(Player player, boolean show) {
		player.getCache().store(CACHE_SHOW_FLAG, show);
	}

	public static String selectedCountryCode(Player player) {
		if (!player.getCache().hasKey(CACHE_CODE)) {
			return "";
		}
		String code = normalizeCountryCode(player.getCache().getString(CACHE_CODE));
		return isValidCountryCode(code) ? code : "";
	}

	public static void setSelectedCountryCode(Player player, String code) {
		String normalized = normalizeCountryCode(code);
		player.getCache().remove(CACHE_LEGACY_IP);
		if (normalized.isEmpty() || !isValidCountryCode(normalized)) {
			player.getCache().remove(CACHE_CODE);
			return;
		}
		player.getCache().store(CACHE_CODE, normalized);
	}

	public static boolean isValidCountryCode(String code) {
		return VALID_COUNTRY_CODES.contains(code);
	}

	public static String normalizeCountryCode(String code) {
		if (code == null) {
			return "";
		}
		String normalized = code.trim().toUpperCase(Locale.ENGLISH);
		if (normalized.equals("NONE")) {
			return "";
		}
		return normalized.matches("[A-Z]{2}") ? normalized : "";
	}

	private static Set<String> buildValidCountryCodes() {
		Set<String> codes = new LinkedHashSet<>();
		for (String[] option : COUNTRY_OPTIONS) {
			if (!option[0].isEmpty()) {
				codes.add(option[0]);
			}
		}
		return Collections.unmodifiableSet(codes);
	}
}
