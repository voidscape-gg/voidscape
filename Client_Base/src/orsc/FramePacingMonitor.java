package orsc;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

final class FramePacingMonitor {
	private static final boolean ENABLED = Boolean.getBoolean("voidscape.framePacing");
	private static final int SAMPLE_FRAMES = Math.max(30, Integer.getInteger("voidscape.framePacing.frames", 600));
	private static final String OUTPUT_PATH = System.getProperty("voidscape.framePacing.out", "").trim();
	private static final Map<String, Recorder> RECORDERS = new LinkedHashMap<String, Recorder>();

	private FramePacingMonitor() {
	}

	static long now() {
		return ENABLED ? System.nanoTime() : 0L;
	}

	static void recordFrame(String name, long startNs, long endNs) {
		if (!ENABLED || startNs == 0L || endNs < startNs) {
			return;
		}
		synchronized (RECORDERS) {
			Recorder recorder = getRecorder(name);
			recorder.recordFrame(startNs, endNs);
			if (recorder.frameCount >= SAMPLE_FRAMES) {
				writeSummary(name, recorder.summaryAndReset(name));
			}
		}
	}

	static void recordDuration(String name, long startNs, long endNs) {
		if (!ENABLED || startNs == 0L || endNs < startNs) {
			return;
		}
		synchronized (RECORDERS) {
			Recorder recorder = getRecorder(name);
			recorder.recordDuration(startNs, endNs);
			if (recorder.durationCount >= SAMPLE_FRAMES) {
				writeSummary(name, recorder.summaryAndReset(name));
			}
		}
	}

	private static Recorder getRecorder(String name) {
		Recorder recorder = RECORDERS.get(name);
		if (recorder == null) {
			recorder = new Recorder(SAMPLE_FRAMES);
			RECORDERS.put(name, recorder);
		}
		return recorder;
	}

	private static void writeSummary(String name, String summary) {
		System.out.println(summary);
		if (OUTPUT_PATH.length() == 0) {
			return;
		}
		File file = new File(OUTPUT_PATH);
		File parent = file.getParentFile();
		if (parent != null) {
			parent.mkdirs();
		}
		FileWriter writer = null;
		try {
			writer = new FileWriter(file, true);
			writer.write(summary);
			writer.write(System.lineSeparator());
		} catch (IOException ex) {
			System.out.println("Unable to write frame pacing summary for " + name + ": " + ex.getMessage());
		} finally {
			if (writer != null) {
				try {
					writer.close();
				} catch (IOException ignored) {
				}
			}
		}
	}

	private static final class Recorder {
		private final long[] frameIntervalsNs;
		private final long[] durationsNs;
		private long lastFrameStartNs;
		private int frameCount;
		private int intervalCount;
		private int durationCount;

		Recorder(int capacity) {
			this.frameIntervalsNs = new long[capacity];
			this.durationsNs = new long[capacity];
		}

		void recordFrame(long startNs, long endNs) {
			if (lastFrameStartNs != 0L && intervalCount < frameIntervalsNs.length) {
				frameIntervalsNs[intervalCount++] = startNs - lastFrameStartNs;
			}
			lastFrameStartNs = startNs;
			if (durationCount < durationsNs.length) {
				durationsNs[durationCount++] = endNs - startNs;
			}
			frameCount++;
		}

		void recordDuration(long startNs, long endNs) {
			if (durationCount < durationsNs.length) {
				durationsNs[durationCount++] = endNs - startNs;
			}
		}

		String summaryAndReset(String name) {
			String summary = buildSummary(name);
			lastFrameStartNs = 0L;
			frameCount = 0;
			intervalCount = 0;
			durationCount = 0;
			return summary;
		}

		private String buildSummary(String name) {
			long[] intervals = copy(frameIntervalsNs, intervalCount);
			long[] durations = copy(durationsNs, durationCount);
			double avgIntervalMs = averageMs(intervals);
			double fps = avgIntervalMs > 0.0D ? 1000.0D / avgIntervalMs : 0.0D;
			return String.format(
				"FRAME_PACING %s frames=%d intervals=%d avgFps=%.1f intervalMs(avg/p50/p95/p99/max)=%.2f/%.2f/%.2f/%.2f/%.2f durationMs(avg/p95/max)=%.2f/%.2f/%.2f spikes(over33/over50)=%d/%d",
				name,
				frameCount,
				intervalCount,
				fps,
				avgIntervalMs,
				percentileMs(intervals, 50),
				percentileMs(intervals, 95),
				percentileMs(intervals, 99),
				maxMs(intervals),
				averageMs(durations),
				percentileMs(durations, 95),
				maxMs(durations),
				countOverMs(intervals, 33.0D),
				countOverMs(intervals, 50.0D));
		}

		private static long[] copy(long[] source, int length) {
			long[] copy = new long[length];
			System.arraycopy(source, 0, copy, 0, length);
			Arrays.sort(copy);
			return copy;
		}

		private static double averageMs(long[] values) {
			if (values.length == 0) {
				return 0.0D;
			}
			long sum = 0L;
			for (long value : values) {
				sum += value;
			}
			return (sum / (double) values.length) / 1000000.0D;
		}

		private static double percentileMs(long[] sortedValues, int percentile) {
			if (sortedValues.length == 0) {
				return 0.0D;
			}
			int index = (int) Math.ceil((percentile / 100.0D) * sortedValues.length) - 1;
			index = Math.max(0, Math.min(sortedValues.length - 1, index));
			return sortedValues[index] / 1000000.0D;
		}

		private static double maxMs(long[] sortedValues) {
			if (sortedValues.length == 0) {
				return 0.0D;
			}
			return sortedValues[sortedValues.length - 1] / 1000000.0D;
		}

		private static int countOverMs(long[] sortedValues, double thresholdMs) {
			int count = 0;
			long thresholdNs = (long) (thresholdMs * 1000000.0D);
			for (long value : sortedValues) {
				if (value > thresholdNs) {
					count++;
				}
			}
			return count;
		}
	}
}
