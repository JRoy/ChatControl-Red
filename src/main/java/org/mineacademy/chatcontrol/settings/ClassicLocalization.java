package org.mineacademy.chatcontrol.settings;

import org.mineacademy.fo.settings.SimpleLocalization;

/**
 * Represents the classic way of localization, we use it no
 * longer due to cumbersome process of adding new keys and managing
 * localization at scale to save time.
 *
 * @deprecated slow to program at scale, use {@link Lang} instead
 */
@Deprecated
@SuppressWarnings("unused")
public final class ClassicLocalization extends SimpleLocalization {

	@Override
	protected int getConfigVersion() {
		return 2;
	}

	/**
	 * Denotes the "none" message
	 */
	public static String NONE;

	/*
	 * Automatically load this class via reflection
	 */
	private static void init() {
		pathPrefix(null);

		NONE = getFallbackString("None");
	}
}
