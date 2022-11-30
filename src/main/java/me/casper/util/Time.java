package me.casper.util;

public final class Time {
	
	public static String formatTime(long millis) {
		
		if (millis < 1_000) return millis + " ms";
		
		final double seconds = millis / 1_000d;
		if (seconds < 60) return String.format("%.2f s", seconds);
		
		final double minutes = seconds / 60;
		if (minutes < 60) return String.format("%.2f min", minutes);
		
		final double hours = minutes / 60;
		if (hours < 24) return String.format("%.2f h", hours);
		
		final double days = hours / 24;
		return String.format("%.2f d", days);
	}
	
	/**
	 * Calculates the estimated load time of a task.
	 *
	 * @param elapsed The time elapsed since the task started.
	 * @param loaded  The current progress of the task.
	 * @param total   The total progress of the task.
	 * @return The estimated load time in milliseconds.
	 */
	public static long loadTimeEstimate(long elapsed, int loaded, int total) {
		
		return (elapsed / loaded) * (total - loaded);
	}
}
