package org.mineacademy.chatcontrol.operator;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.mineacademy.chatcontrol.PlayerCache;
import org.mineacademy.chatcontrol.SenderCache;
import org.mineacademy.chatcontrol.api.PlayerMessageEvent;
import org.mineacademy.chatcontrol.operator.DeathMessage.DeathMessageCheck;
import org.mineacademy.chatcontrol.operator.Operator.OperatorCheck;
import org.mineacademy.chatcontrol.operator.PlayerMessage.PlayerMessageCheck;
import org.mineacademy.chatcontrol.operator.PlayerMessage.Type;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.FileUtil;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.exception.FoException;
import org.mineacademy.fo.model.RuleSetReader;
import org.mineacademy.fo.model.SimpleTime;

import lombok.Getter;

/**
 * Represents the core engine for player message broadcasting
 */
public final class PlayerMessages extends RuleSetReader<PlayerMessage> {

	@Getter
	private static final PlayerMessages instance = new PlayerMessages();

	/**
	 * The loaded items sorted by group
	 */
	private final Map<JoinQuitKickMessage.Type, List<PlayerMessage>> messages = new HashMap<>();

	/**
	 * The task responsible for sending timed message broadcasts
	 */
	private BukkitTask broadcastTask;

	/*
	 * Create this class
	 */
	private PlayerMessages() {
		super("group");
	}

	/**
	 * Reloads the content of this class.
	 */
	@Override
	public void load() {
		this.messages.clear();

		for (final JoinQuitKickMessage.Type type : PlayerMessage.Type.values())
			this.messages.put(type, loadFromFile("messages/" + type.getKey() + ".rs"));

		this.setupTimedTask();
	}

	/*
	 * Reschedule the timed message broadcasting task
	 */
	private void setupTimedTask() {
		if (this.broadcastTask != null)
			this.broadcastTask.cancel();

		// Re/schedule
		final SimpleTime delay = Settings.Messages.TIMED_DELAY;

		if (Settings.Messages.APPLY_ON.contains(Type.TIMED))
			this.broadcastTask = Common.runTimer(delay.getTimeTicks(), PlayerMessages::broadcastTimed);
	}

	/**
	 * @see org.mineacademy.fo.model.RuleSetReader#createRule(java.io.File, java.lang.String)
	 */
	@Override
	protected PlayerMessage createRule(File file, String value) {

		final JoinQuitKickMessage.Type type = PlayerMessage.Type.fromKey(FileUtil.getFileName(file));

		if (type == Type.DEATH)
			return new DeathMessage(value);

		else if (type == Type.TIMED)
			return new TimedMessage(value);

		else if (type == Type.JOIN || type == Type.KICK || type == Type.QUIT)
			return new JoinQuitKickMessage(type, value);

		throw new FoException("Unrecognized message type " + type);
	}

	/**
	 * Attempt to find a rule by name
	 *
	 * @param group
	 * @return
	 */
	public PlayerMessage findMessage(JoinQuitKickMessage.Type type, String group) {
		for (final PlayerMessage item : getMessages(type))
			if (item.getGroup().equalsIgnoreCase(group))
				return item;

		return null;
	}

	/**
	 * Return all player message names
	 *
	 * @return
	 */
	public Set<String> getMessageNames(JoinQuitKickMessage.Type type) {
		return Common.convertSet(this.getMessages(type), PlayerMessage::getGroup);
	}

	/**
	 * Return all player message that are also enabled in Apply_On in settings
	 *
	 * @param type
	 * @return
	 */
	public Set<String> getEnabledMessageNames(JoinQuitKickMessage.Type type) {
		return Common.convertSet(this.getMessages(type).stream().filter(message -> Settings.Messages.APPLY_ON.contains(message.getType())).collect(Collectors.toList()), PlayerMessage::getGroup);
	}

	/**
	 * Return immutable collection of all loaded broadcasts
	 *
	 * @return
	 */
	public <T extends PlayerMessage> List<T> getMessages(JoinQuitKickMessage.Type type) {
		return (List<T>) Collections.unmodifiableList(this.messages.get(type));
	}

	/* ------------------------------------------------------------------------------- */
	/* Static */
	/* ------------------------------------------------------------------------------- */

	/*
	 * Broadcast timed message
	 */
	private static <T extends PlayerMessage> void broadcastTimed() {
		broadcast(Type.TIMED, null, "");
	}

	/**
	 * Broadcast the given message type from the given sender and the original message
	 *
	 * @param type
	 * @param player
	 * @param originalMessage
	 */
	public static void broadcast(PlayerMessage.Type type, Player player, String originalMessage) {
		final OperatorCheck<?> check;

		if (type == Type.DEATH)
			check = new DeathMessageCheck(player, originalMessage);

		else if (type == Type.TIMED)
			check = new TimedMessagesCheck();

		else
			check = new JoinQuitKickCheck(type, player, originalMessage);

		if (Common.callEvent(new PlayerMessageEvent(player, type, check, originalMessage)))
			check.start();
	}

	/* ------------------------------------------------------------------------------- */
	/* Classes */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Represents a singular broadcast
	 */
	public static final class JoinQuitKickCheck extends PlayerMessageCheck<PlayerMessage> {

		private final List<PlayerMessage> messages;

		/*
		 * Create new constructor with handy objects
		 */
		private JoinQuitKickCheck(PlayerMessage.Type type, Player player, String originalMessage) {
			super(type, player, originalMessage);

			Valid.checkBoolean(type != Type.DEATH, "For death messages use separate class");
			this.messages = PlayerMessages.getInstance().getMessages(type);
		}

		/**
		 * @see org.mineacademy.chatcontrol.operator.Operator.OperatorCheck#getOperators()
		 */
		@Override
		public List<PlayerMessage> getOperators() {
			return this.messages;
		}

		@Override
		protected CommandSender getMessagePlayerForVariables() {
			return this.sender;
		}
	}

	/**
	 * Represents timed broadcaster check
	 */
	public static final class TimedMessagesCheck extends PlayerMessageCheck<PlayerMessage> {

		private final List<PlayerMessage> messages;

		/*
		 * Create new constructor with handy objects
		 */
		private TimedMessagesCheck() {
			super(Type.TIMED, null, "");

			this.messages = PlayerMessages.getInstance().getMessages(Type.TIMED);
		}

		/**
		 * @see org.mineacademy.chatcontrol.operator.Operator.OperatorCheck#getOperators()
		 */
		@Override
		public List<PlayerMessage> getOperators() {
			return messages;
		}

		/**
		 * We need to set variables for each player separately.
		 */
		@Override
		protected void setVariablesFor(Player receiver) {
			this.receiver = receiver;
			this.receiverCache = PlayerCache.from(receiver);

			this.player = receiver;
			this.isPlayer = true;

			this.cache = PlayerCache.from(receiver);
			this.sender = receiver;
			this.senderCache = SenderCache.from(receiver);
		}
	}
}
