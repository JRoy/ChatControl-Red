package org.mineacademy.chatcontrol.settings;

import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;

import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.SerializeUtil;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.exception.FoException;
import org.mineacademy.fo.model.JavaScriptExecutor;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.settings.SimpleSettings;
import org.mineacademy.fo.settings.YamlConfig;

/**
 * Represents the new way of internalization, with the greatest
 * upside of saving development time.
 *
 * The downside is that keys are not checked during load so any
 * malformed or missing key will fail later and may be unnoticed.
 */
public final class Lang extends YamlConfig {

	/**
	 * The instance of this class
	 */
	private static volatile Lang instance = new Lang();

	/*
	 * Create a new instance and load
	 */
	private Lang() {
		this.loadConfiguration("localization/messages_" + SimpleSettings.LOCALE_PREFIX + ".yml");
	}

	/**
	 * @see org.mineacademy.fo.settings.YamlConfig#saveComments()
	 */
	@Override
	protected boolean saveComments() {
		return true;
	}

	/*
	 * Return a key from our localization, failing if not exists
	 */
	private String getStringStrict(String path) {
		final String key = getString(path);
		Valid.checkNotNull(key, "Missing localization key '" + path + "' from " + getFileName());

		return key;
	}

	/**
	 * Called when plugin is first started to load the file
	 */
	public static void init() {
	}

	/**
	 * Reload this file
	 */
	public static void reloadFile() {
		synchronized (instance) {
			instance.reload();
		}
	}

	/**
	 * Return a boolean at path
	 *
	 * @param path
	 * @return
	 */
	public static boolean getOption(String path) {
		return instance.getBoolean(path);
	}

	/**
	 * Return a component list from the localization file with {0} {1} etc. variables replaced.
	 *
	 * @param path
	 * @param variables
	 * @return
	 */
	public static List<SimpleComponent> ofComponentList(String path, @Nullable Object... variables) {
		return Common.convert(ofList(path, variables), item -> SimpleComponent.of(item));
	}

	/**
	 * Return a list from the localization file with {0} {1} etc. variables replaced.
	 *
	 * @param path
	 * @param variables
	 * @return
	 */
	public static List<String> ofList(String path, @Nullable Object... variables) {
		return Arrays.asList(ofArray(path, variables));
	}

	/**
	 * Return an array from the localization file with {0} {1} etc. variables replaced.
	 *
	 * @param path
	 * @param variables
	 * @return
	 */
	public static String[] ofArray(String path, @Nullable Object... variables) {
		return of(path, variables).split("\n");
	}

	/**
	 * Return a component from the localization file with {0} {1} etc. variables replaced.
	 *
	 * @param path
	 * @param variables
	 * @return
	 */
	public static SimpleComponent ofComponent(String path, @Nullable Object... variables) {
		return SimpleComponent.of(of(path, variables));
	}

	/**
	 * Return the given key for the given amount automatically
	 * singular or plural form including the amount
	 *
	 * @param amount
	 * @param path
	 * @return
	 */
	public static String ofCase(long amount, String path) {
		return amount + " " + ofCaseNoAmount(amount, path);
	}

	/**
	 * Return the given key for the given amount automatically
	 * singular or plural form excluding the amount
	 *
	 * @param amount
	 * @param path
	 * @return
	 */
	public static String ofCaseNoAmount(long amount, String path) {
		final String key = of(path);
		final String[] split = key.split(", ");

		Valid.checkBoolean(split.length == 1 || split.length == 2, "Invalid syntax of key at '" + path + "', this key is a special one and "
				+ "it needs singular and plural form separated with , such as: second, seconds");

		final String singular = split[0];
		final String plural = split[split.length == 2 ? 1 : 0];

		return amount == 0 || amount > 1 ? plural : singular;
	}

	/**
	 * Return an array from the localization file with {0} {1} etc. variables replaced.
	 * and script variables parsed. We treat the locale key as a valid JavaScript
	 *
	 * @param path
	 * @param scriptVariables
	 * @param variables
	 * @return
	 */
	public static String ofScript(String path, SerializedMap scriptVariables, @Nullable Object... variables) {
		String script = of(path, variables);
		Object result;

		// Our best guess is that the user has removed the script completely but forgot to put the entire message in '',
		// so we attempt to do so
		if (!script.contains("?") && !script.contains(":") && !script.contains("+") && !script.startsWith("'") && !script.endsWith("'"))
			script = "'" + script + "'";

		try {
			result = JavaScriptExecutor.run(script, scriptVariables.asMap());

		} catch (final Throwable t) {
			throw new FoException(t, "Failed to compile localization key '" + path + "' with script: " + script + " (this must be a valid JavaScript code)");
		}

		return result.toString();
	}

	/**
	 * Return a key from the localization file with {0} {1} etc. variables replaced.
	 *
	 * @param path
	 * @param variables
	 * @return
	 */
	public static String of(String path, @Nullable Object... variables) {
		synchronized (instance) {
			final String key = instance.getStringStrict(path);

			return translate(key, variables);
		}
	}

	/*
	 * Replace placeholders in the message
	 */
	private static String translate(String key, @Nullable Object... variables) {
		if (variables != null)
			for (int i = 0; i < variables.length; i++) {
				Object variable = variables[i];

				// Auto serialize some variables
				if (variable instanceof Channel)
					variable = ((Channel) variable).getName();

				variable = Common.getOrDefaultStrict(SerializeUtil.serialize(variable), ClassicLocalization.NONE);
				Valid.checkNotNull("Failed to replace {" + i + "} as " + variable + "(raw = " + variables[i] + ")");

				key = key.replace("{" + i + "}", variable.toString());
			}

		return key;
	}
}
