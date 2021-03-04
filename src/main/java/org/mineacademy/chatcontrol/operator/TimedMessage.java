package org.mineacademy.chatcontrol.operator;

import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.model.SimpleTime;
import org.mineacademy.fo.model.Tuple;

/**
 * Represents join, leave, kick or timed message broadcast
 */
public final class TimedMessage extends PlayerMessage {

	/**
	 * @param group
	 */
	public TimedMessage(String group) {
		super(Type.TIMED, group);
	}

	/**
	 * Return the broadcast specific delay or the default one
	 *
	 * @return
	 */
	@Override
	public Tuple<SimpleTime, String> getDelay() {
		return Common.getOrDefault(super.getDelay(), new Tuple<>(Settings.Messages.TIMED_DELAY, null));
	}
}