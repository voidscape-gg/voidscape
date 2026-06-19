package com.openrsc.server.util;

import com.openrsc.server.LoginExecutor;
import com.openrsc.server.Server;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Lightweight rolling server performance telemetry.
 *
 * Records one sample per game tick and emits compact summaries at a configured
 * interval. Heavy work, including sorting for percentiles, only happens when a
 * summary is due.
 */
public final class ServerPerformanceTracker {
	private static final Logger LOGGER = LogManager.getLogger(ServerPerformanceTracker.class);

	private SampleWindow tick;
	private SampleWindow late;
	private SampleWindow incoming;
	private SampleWindow events;
	private SampleWindow outgoing;
	private SampleWindow worldUpdate;
	private SampleWindow processPlayers;
	private SampleWindow processNpcs;
	private SampleWindow messageQueues;
	private SampleWindow updateClients;
	private SampleWindow cleanup;
	private SampleWindow walkToActions;

	private int configuredWindowTicks = -1;
	private long lastLogNanos = System.nanoTime();
	private long windowStartTick = -1;
	private long samplesSinceLog;
	private long lateTicksSinceLog;
	private long skippedTicksSinceLog;
	private long incomingPacketsSinceLog;
	private long outgoingPacketsSinceLog;
	private int maxPlayersSinceLog;
	private int maxNpcsSinceLog;
	private int maxLoginQueueSinceLog;
	private int maxSaveQueueSinceLog;
	private int maxGenericLoginQueueSinceLog;
	private int maxSqlQueueSinceLog;
	private int maxSqlLoggingQueueSinceLog;
	private int maxOnlineMonitorQueueSinceLog;

	public void record(final Server server, final long currentTick, final boolean tickLate,
	                   final long skippedTicks, final int incomingPackets, final int outgoingPackets) {
		if (!server.getConfig().PERF_TELEMETRY) return;
		ensureWindows(server.getConfig().PERF_TELEMETRY_WINDOW_TICKS);

		if (windowStartTick < 0) {
			windowStartTick = currentTick;
		}

		tick.add(micros(server.getLastTickDuration()));
		late.add(micros(server.getTimeLate()));
		incoming.add(micros(server.getLastIncomingPacketsDuration()));
		events.add(micros(server.getLastEventsDuration()));
		outgoing.add(micros(server.getLastOutgoingPacketsDuration()));
		worldUpdate.add(micros(server.getLastWorldUpdateDuration()));
		processPlayers.add(micros(server.getLastProcessPlayersDuration()));
		processNpcs.add(micros(server.getLastProcessNpcsDuration()));
		messageQueues.add(micros(server.getLastProcessMessageQueuesDuration()));
		updateClients.add(micros(server.getLastUpdateClientsDuration()));
		cleanup.add(micros(server.getLastDoCleanupDuration()));
		walkToActions.add(micros(server.getLastExecuteWalkToActionsDuration()));

		samplesSinceLog++;
		if (tickLate) {
			lateTicksSinceLog++;
		}
		skippedTicksSinceLog += skippedTicks;
		incomingPacketsSinceLog += incomingPackets;
		outgoingPacketsSinceLog += outgoingPackets;
		maxPlayersSinceLog = Math.max(maxPlayersSinceLog, server.getWorld().countPlayers());
		maxNpcsSinceLog = Math.max(maxNpcsSinceLog, server.getWorld().countNpcs());

		final LoginExecutor loginExecutor = server.getLoginExecutor();
		maxLoginQueueSinceLog = Math.max(maxLoginQueueSinceLog, loginExecutor.getLoginQueueSize());
		maxSaveQueueSinceLog = Math.max(maxSaveQueueSinceLog, loginExecutor.getSaveQueueSize());
		maxGenericLoginQueueSinceLog = Math.max(maxGenericLoginQueueSinceLog, loginExecutor.getGenericQueueSize());
		maxSqlQueueSinceLog = Math.max(maxSqlQueueSinceLog, server.getSqlQueueSize());
		maxSqlLoggingQueueSinceLog = Math.max(maxSqlLoggingQueueSinceLog, server.getSqlLoggingQueueSize());
		maxOnlineMonitorQueueSinceLog = Math.max(maxOnlineMonitorQueueSinceLog, server.getOnlineMonitorQueueSize());

		final long now = System.nanoTime();
		final long intervalNanos = TimeUnit.SECONDS.toNanos(server.getConfig().PERF_TELEMETRY_INTERVAL_SECONDS);
		if (now - lastLogNanos >= intervalNanos) {
			logSummary(currentTick);
			resetInterval(now, currentTick + 1);
		}
	}

	private void ensureWindows(final int requestedWindowTicks) {
		if (configuredWindowTicks == requestedWindowTicks && tick != null) return;
		configuredWindowTicks = requestedWindowTicks;
		resetWindows();
	}

	private void resetWindows() {
		tick = new SampleWindow(configuredWindowTicks);
		late = new SampleWindow(configuredWindowTicks);
		incoming = new SampleWindow(configuredWindowTicks);
		events = new SampleWindow(configuredWindowTicks);
		outgoing = new SampleWindow(configuredWindowTicks);
		worldUpdate = new SampleWindow(configuredWindowTicks);
		processPlayers = new SampleWindow(configuredWindowTicks);
		processNpcs = new SampleWindow(configuredWindowTicks);
		messageQueues = new SampleWindow(configuredWindowTicks);
		updateClients = new SampleWindow(configuredWindowTicks);
		cleanup = new SampleWindow(configuredWindowTicks);
		walkToActions = new SampleWindow(configuredWindowTicks);
	}

	private void logSummary(final long currentTick) {
		LOGGER.info("PERF ticks={}..{} samples={} players={} npcs={} tick_ms p50/p95/p99/max={} late_ms p95/max={} late_ticks={} skipped_ticks={} packets_in/out={}/{} queues login/save/generic={} sql/sqlLog/online={}",
			windowStartTick, currentTick, samplesSinceLog, maxPlayersSinceLog, maxNpcsSinceLog,
			tick.summary(),
			late.p95MaxSummary(),
			lateTicksSinceLog, skippedTicksSinceLog,
			incomingPacketsSinceLog, outgoingPacketsSinceLog,
			queueSummary(maxLoginQueueSinceLog, maxSaveQueueSinceLog, maxGenericLoginQueueSinceLog),
			queueSummary(maxSqlQueueSinceLog, maxSqlLoggingQueueSinceLog, maxOnlineMonitorQueueSinceLog));

		LOGGER.info("PERF stages_p95_ms world/npc/player/events/walk/msg/update/in/out/cleanup={}/{}/{}/{}/{}/{}/{}/{}/{}/{}",
			worldUpdate.p95(), processNpcs.p95(), processPlayers.p95(), events.p95(), walkToActions.p95(),
			messageQueues.p95(), updateClients.p95(), incoming.p95(), outgoing.p95(), cleanup.p95());
	}

	private void resetInterval(final long now, final long nextStartTick) {
		lastLogNanos = now;
		windowStartTick = nextStartTick;
		resetWindows();
		samplesSinceLog = 0;
		lateTicksSinceLog = 0;
		skippedTicksSinceLog = 0;
		incomingPacketsSinceLog = 0;
		outgoingPacketsSinceLog = 0;
		maxPlayersSinceLog = 0;
		maxNpcsSinceLog = 0;
		maxLoginQueueSinceLog = 0;
		maxSaveQueueSinceLog = 0;
		maxGenericLoginQueueSinceLog = 0;
		maxSqlQueueSinceLog = 0;
		maxSqlLoggingQueueSinceLog = 0;
		maxOnlineMonitorQueueSinceLog = 0;
	}

	private static long micros(final long nanos) {
		return TimeUnit.NANOSECONDS.toMicros(Math.max(0L, nanos));
	}

	private static String queueSummary(final int first, final int second, final int third) {
		return first + "/" + second + "/" + third;
	}

	private static String formatMicros(final long micros) {
		return String.format(Locale.US, "%.1f", micros / 1000.0D);
	}

	private static final class SampleWindow {
		private final long[] values;
		private int cursor;
		private int count;

		private SampleWindow(final int size) {
			this.values = new long[Math.max(32, size)];
		}

		private void add(final long value) {
			values[cursor] = value;
			cursor = (cursor + 1) % values.length;
			if (count < values.length) {
				count++;
			}
		}

		private String summary() {
			final Snapshot snapshot = snapshot();
			return snapshot.p50 + "/" + snapshot.p95 + "/" + snapshot.p99 + "/" + snapshot.max;
		}

		private String p95MaxSummary() {
			final Snapshot snapshot = snapshot();
			return snapshot.p95 + "/" + snapshot.max;
		}

		private String p95() {
			return snapshot().p95;
		}

		private Snapshot snapshot() {
			if (count == 0) {
				return new Snapshot("0.0", "0.0", "0.0", "0.0");
			}
			final long[] copy = Arrays.copyOf(values, count);
			Arrays.sort(copy);
			return new Snapshot(
				formatMicros(percentile(copy, 0.50D)),
				formatMicros(percentile(copy, 0.95D)),
				formatMicros(percentile(copy, 0.99D)),
				formatMicros(copy[copy.length - 1]));
		}

		private static long percentile(final long[] sorted, final double percentile) {
			final int index = Math.min(sorted.length - 1,
				Math.max(0, (int)Math.ceil(percentile * sorted.length) - 1));
			return sorted[index];
		}
	}

	private static final class Snapshot {
		private final String p50;
		private final String p95;
		private final String p99;
		private final String max;

		private Snapshot(final String p50, final String p95, final String p99, final String max) {
			this.p50 = p50;
			this.p95 = p95;
			this.p99 = p99;
			this.max = max;
		}
	}
}
