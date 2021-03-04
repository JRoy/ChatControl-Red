package org.mineacademy.chatcontrol.model;

import java.io.PrintStream;
import java.util.logging.LogRecord;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.message.Message;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.MinecraftVersion;
import org.mineacademy.fo.MinecraftVersion.V;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Represents the console filtering module
 */
public final class Filter {

	/**
	 * Start filtering the console
	 */
	public static void inject() {

		// Set filter for System out
		System.setOut(new FilterSystem());

		// Set filter for Bukkit
		final FilterLegacy filter = new FilterLegacy();

		for (final Plugin plugin : Bukkit.getPluginManager().getPlugins())
			plugin.getLogger().setFilter(filter);

		Bukkit.getLogger().setFilter(filter);

		// Set Log4j filter
		if (MinecraftVersion.atLeast(V.v1_7))
			FilterLog4j.inject();
	}

	/*
	 * Return true if the message is filtered
	 */
	static boolean isFiltered(String message) {
		if (message == null || message.isEmpty())
			return false;

		message = Common.stripColors(message);

		// Filter inbuilt Foundation or ChatControl commands
		if (message.contains("issued server command: /" + Settings.MAIN_COMMAND_ALIASES.get(0) + " internal") || message.contains("issued server command: /#flp"))
			return true;

		// Filter private message commands since we print them out in our own way
		/*if (Settings.PrivateMessages.ENABLED)
			for (final String alias : Common.joinArrays(Settings.PrivateMessages.TELL_ALIASES, Settings.PrivateMessages.REPLY_ALIASES))
				if (message.contains("issued server command: /" + alias + " "))
					return true;*/

		message = message.toLowerCase();

		// Filter user-defined commands
		if (Settings.ConsoleFilter.ENABLED)
			for (String filter : Settings.ConsoleFilter.MESSAGES) {
				filter = filter.toLowerCase();

				if (message.startsWith(filter) || message.contains(filter))
					return true;
			}

		return false;
	}
}

/**
 * The old Bukkit filter
 */
class FilterLegacy implements java.util.logging.Filter {

	@Override
	public boolean isLoggable(LogRecord record) {
		final String message = record.getMessage();

		return !Filter.isFiltered(message);
	}
}

/**
 * The System out filter
 */
class FilterSystem extends PrintStream {

	FilterSystem() {
		super(System.out);
	}

	@Override
	public void println(Object x) {
		if (x != null && !Filter.isFiltered(x.toString()))
			super.println(x);
	}

	@Override
	public void println(String x) {
		if (x != null && !Filter.isFiltered(x))
			super.println(x);
	}
}

/**
 * The new Log4j filter
 */
@NoArgsConstructor(access = AccessLevel.PACKAGE)
class FilterLog4j implements org.apache.logging.log4j.core.Filter {

	/*
	 * Starts logging for this filter
	 */
	static void inject() {
		try {
			((Logger) LogManager.getRootLogger()).addFilter(new FilterLog4j());

		} catch (final Throwable ex) {
			// Unsupported
		}
	}

	@Override
	public Result filter(LogEvent record) {
		return checkMessage(record.getMessage().getFormattedMessage());
	}

	@Override
	public Result filter(Logger arg0, Level arg1, Marker arg2, String message, Object... arg4) {
		return checkMessage(message);
	}

	@Override
	public Result filter(Logger arg0, Level arg1, Marker arg2, Object message, Throwable arg4) {
		return checkMessage(message.toString());
	}

	@Override
	public Result filter(Logger arg0, Level arg1, Marker arg2, Message message, Throwable arg4) {
		return checkMessage(message.getFormattedMessage());
	}

	/*
	 * Return if the message should be filtered
	 */
	private final Result checkMessage(String message) {
		return org.mineacademy.chatcontrol.model.Filter.isFiltered(message) ? Result.DENY : Result.NEUTRAL;
	}

	/* ------------------------------------------------------------ */
	/* Implementation required methods */
	/* ------------------------------------------------------------ */

	@Override
	public Result getOnMatch() {
		return Result.NEUTRAL;
	}

	@Override
	public Result getOnMismatch() {
		return Result.NEUTRAL;
	}

	@Override
	public State getState() {
		try {
			return State.STARTED;
		} catch (final Throwable t) {
			return null;
		}
	}

	@Override
	public void initialize() {
	}

	@Override
	public boolean isStarted() {
		return true;
	}

	@Override
	public boolean isStopped() {
		return false;
	}

	@Override
	public void start() {
	}

	@Override
	public void stop() {
	}

	@Override
	public Result filter(Logger arg0, Level arg1, Marker arg2, String arg3, Object arg4) {
		return null;
	}

	@Override
	public Result filter(Logger arg0, Level arg1, Marker arg2, String arg3, Object arg4, Object arg5) {
		return null;
	}

	@Override
	public Result filter(Logger arg0, Level arg1, Marker arg2, String arg3, Object arg4, Object arg5, Object arg6) {
		return null;
	}

	@Override
	public Result filter(Logger arg0, Level arg1, Marker arg2, String arg3, Object arg4, Object arg5, Object arg6, Object arg7) {
		return null;
	}

	@Override
	public Result filter(Logger arg0, Level arg1, Marker arg2, String arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8) {
		return null;
	}

	@Override
	public Result filter(Logger arg0, Level arg1, Marker arg2, String arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9) {
		return null;
	}

	@Override
	public Result filter(Logger arg0, Level arg1, Marker arg2, String arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10) {
		return null;
	}

	@Override
	public Result filter(Logger arg0, Level arg1, Marker arg2, String arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11) {
		return null;
	}

	@Override
	public Result filter(Logger arg0, Level arg1, Marker arg2, String arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11, Object arg12) {
		return null;
	}

	@Override
	public Result filter(Logger arg0, Level arg1, Marker arg2, String arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13) {
		return null;
	}
}
