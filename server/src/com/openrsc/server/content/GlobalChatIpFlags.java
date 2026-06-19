package com.openrsc.server.content;

import com.openrsc.server.model.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class GlobalChatIpFlags {
	public static final int CLIENT_VERSION = 10069;
	public static final int SETTING_SHOW_COUNTRY_FLAG = 56;

	private static final Logger LOGGER = LogManager.getLogger(GlobalChatIpFlags.class);
	private static final String CACHE_SHOW_FLAG = "setting_global_chat_country_flag";
	private static final String CACHE_IP = "global_chat_country_ip";
	private static final String CACHE_CODE = "global_chat_country_code";
	private static final Map<String, String> COUNTRY_BY_IP = new ConcurrentHashMap<>();
	private static final Set<String> PENDING_IPS = ConcurrentHashMap.newKeySet();
	private static final ExecutorService LOOKUP_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
		Thread thread = new Thread(r, "GlobalChatCountryLookup");
		thread.setDaemon(true);
		return thread;
	});

	private GlobalChatIpFlags() {
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
		String code = getCachedCountryCode(player);
		if (code.isEmpty()) {
			resolvePlayerAsync(player);
			return "";
		}
		return "@flg@" + code + " ";
	}

	public static void resolvePlayerAsync(Player player) {
		if (!player.getConfig().WANT_GLOBAL_CHAT_COUNTRY_FLAGS) {
			return;
		}
		if (!player.getConfig().WANT_GLOBAL_CHAT && !player.getConfig().WANT_GLOBAL_FRIEND) {
			return;
		}
		final String ip = normalizeIp(player.getCurrentIP());
		if (ip.isEmpty()) {
			return;
		}
		String localCode = configuredLocalCountryCode(player, ip);
		if (!localCode.isEmpty()) {
			applyCountryCode(player, ip, localCode);
			return;
		}
		if (!isPublicAddress(ip)) {
			return;
		}
		String cached = COUNTRY_BY_IP.get(ip);
		if (cached != null) {
			applyCountryCode(player, ip, cached);
			return;
		}
		if (!PENDING_IPS.add(ip)) {
			return;
		}
		final String lookupUrl = player.getConfig().GLOBAL_CHAT_COUNTRY_LOOKUP_URL;
		final int lookupTimeoutMs = player.getConfig().GLOBAL_CHAT_COUNTRY_LOOKUP_TIMEOUT_MS;
		LOOKUP_EXECUTOR.submit(() -> {
			try {
				String code = lookupCountryCode(ip, lookupUrl, lookupTimeoutMs);
				if (!code.isEmpty()) {
					COUNTRY_BY_IP.put(ip, code);
				}
			} finally {
				PENDING_IPS.remove(ip);
			}
		});
	}

	public static void setShow(Player player, boolean show) {
		player.getCache().store(CACHE_SHOW_FLAG, show);
	}

	private static String getCachedCountryCode(Player player) {
		final String ip = normalizeIp(player.getCurrentIP());
		if (ip.isEmpty()) {
			return "";
		}
		String localCode = configuredLocalCountryCode(player, ip);
		if (!localCode.isEmpty()) {
			applyCountryCode(player, ip, localCode);
			return localCode;
		}
		if (player.getCache().hasKey(CACHE_IP)
			&& player.getCache().hasKey(CACHE_CODE)
			&& ip.equals(player.getCache().getString(CACHE_IP))) {
			return sanitizeCountryCode(player.getCache().getString(CACHE_CODE));
		}
		String code = COUNTRY_BY_IP.get(ip);
		if (code != null) {
			applyCountryCode(player, ip, code);
			return code;
		}
		return "";
	}

	private static void applyCountryCode(Player player, String ip, String code) {
		if (!ip.equals(normalizeIp(player.getCurrentIP()))) {
			return;
		}
		String sanitized = sanitizeCountryCode(code);
		if (sanitized.isEmpty()) {
			return;
		}
		player.getCache().store(CACHE_IP, ip);
		player.getCache().store(CACHE_CODE, sanitized);
	}

	private static String lookupCountryCode(String ip, String template, int timeoutMs) {
		if (template == null || template.trim().isEmpty()) {
			return "";
		}
		HttpURLConnection http = null;
		try {
			String encodedIp = URLEncoder.encode(ip, StandardCharsets.UTF_8.name());
			String endpoint = template.contains("%s") ? String.format(template, encodedIp) : template + encodedIp;
			URL url = new URL(endpoint);
			http = (HttpURLConnection) url.openConnection();
			http.setRequestMethod("GET");
			http.setConnectTimeout(timeoutMs);
			http.setReadTimeout(timeoutMs);
			http.setUseCaches(true);
			http.setRequestProperty("Accept", "application/json");
			http.setRequestProperty("User-Agent", "Voidscape");
			int status = http.getResponseCode();
			if (status < 200 || status >= 300) {
				LOGGER.warn("Global chat country lookup failed for {} with HTTP {}", ip, status);
				return "";
			}
			try (InputStream in = http.getInputStream();
				 BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
				StringBuilder body = new StringBuilder();
				String line;
				while ((line = reader.readLine()) != null) {
					body.append(line);
				}
				JSONObject json = new JSONObject(body.toString());
				String code = json.optString("country", json.optString("country_code", ""));
				return sanitizeCountryCode(code);
			}
		} catch (Exception ex) {
			LOGGER.warn("Global chat country lookup failed for {}", ip, ex);
			return "";
		} finally {
			if (http != null) {
				http.disconnect();
			}
		}
	}

	private static String normalizeIp(String ip) {
		if (ip == null) {
			return "";
		}
		return ip.trim();
	}

	private static boolean isPublicAddress(String ip) {
		try {
			InetAddress address = InetAddress.getByName(ip);
			return !address.isAnyLocalAddress()
				&& !address.isLoopbackAddress()
				&& !address.isLinkLocalAddress()
				&& !address.isSiteLocalAddress()
				&& !address.isMulticastAddress();
		} catch (Exception ex) {
			return false;
		}
	}

	private static String configuredLocalCountryCode(Player player, String ip) {
		String code = sanitizeCountryCode(player.getConfig().GLOBAL_CHAT_LOCAL_COUNTRY_CODE);
		if (code.isEmpty() || !isLocalAddress(ip)) {
			return "";
		}
		return code;
	}

	private static boolean isLocalAddress(String ip) {
		try {
			InetAddress address = InetAddress.getByName(ip);
			return address.isAnyLocalAddress() || address.isLoopbackAddress();
		} catch (Exception ex) {
			return false;
		}
	}

	private static String sanitizeCountryCode(String code) {
		if (code == null) {
			return "";
		}
		String sanitized = code.trim().toUpperCase(Locale.ENGLISH);
		return sanitized.matches("[A-Z]{2}") ? sanitized : "";
	}
}
