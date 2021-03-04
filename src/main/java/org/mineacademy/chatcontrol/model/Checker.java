package org.mineacademy.chatcontrol.model;

import java.util.List;

import javax.annotation.Nullable;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.mineacademy.chatcontrol.PlayerCache;
import org.mineacademy.chatcontrol.SenderCache;
import org.mineacademy.chatcontrol.SenderCache.Output;
import org.mineacademy.chatcontrol.model.Log.Type;
import org.mineacademy.chatcontrol.operator.Rule;
import org.mineacademy.chatcontrol.operator.Rule.RuleCheck;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings.AntiBot;
import org.mineacademy.chatcontrol.settings.Settings.AntiCaps;
import org.mineacademy.chatcontrol.settings.Settings.AntiSpam;
import org.mineacademy.chatcontrol.settings.Settings.Grammar;
import org.mineacademy.chatcontrol.settings.Settings.WarningPoints;
import org.mineacademy.fo.ChatUtil;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.Messenger;
import org.mineacademy.fo.PlayerUtil;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.exception.EventHandledException;
import org.mineacademy.fo.exception.FoException;
import org.mineacademy.fo.model.SimpleTime;
import org.mineacademy.fo.model.Variables;
import org.mineacademy.fo.remain.Remain;

import lombok.Getter;

/**
 * Represents a singular check for antispam
 */
public class Checker {

	/**
	 * The type of the check
	 */
	private final Log.Type type;

	/**
	 * The sender
	 */
	private final CommandSender sender;

	/**
	 * The message
	 */
	@Getter
	private String message;

	/**
	 * Channel associated with this check
	 */
	@Nullable
	private final Channel channel;

	/**
	 * Is the {@link #sender} a {@link Player}?
	 */
	protected boolean isPlayer;

	/**
	 * The player if {@link #isPlayer} is true
	 */
	protected Player player;

	/**
	 * The player cache if {@link #isPlayer} is true
	 */
	protected PlayerCache cache;

	/**
	 * The sender cache
	 */
	protected SenderCache senderCache;

	/**
	 * Should we cancel the event silently and only send the message
	 * to the sender himself?
	 */
	@Getter
	protected boolean cancelledSilently;

	/**
	 * Create a new checker
	 *
	 * @param sender
	 * @param message
	 * @param channel
	 */
	private Checker(Log.Type type, CommandSender sender, String message, @Nullable Channel channel) throws EventHandledException {
		this.type = type;
		this.sender = sender;
		this.message = message;
		this.channel = channel;

		this.isPlayer = sender instanceof Player;
		this.player = isPlayer ? (Player) sender : null;
		this.cache = isPlayer ? PlayerCache.from(player) : null;
		this.senderCache = sender != null ? SenderCache.from(sender) : null;

		this.filter();
	}

	/**
	 * Parse antispam
	 *
	 * @param sender
	 * @param message
	 * @param channel the write channel of the player
	 *
	 * @return
	 * @throws EventHandledException
	 */
	public static Checker filterCommand(CommandSender sender, String message, @Nullable Channel channel) throws EventHandledException {
		return new Checker(Type.COMMAND, sender, message, channel);
	}

	/**
	 * Parse antispam
	 *
	 * @param sender
	 * @param message
	 * @param channel
	 * @return
	 * @throws EventHandledException
	 */
	public static Checker filterChannel(CommandSender sender, String message, @Nullable Channel channel) throws EventHandledException {
		return new Checker(Type.CHAT, sender, message, channel);
	}

	/**
	 * @see org.mineacademy.chatcontrol.model.Checkable#filter()
	 */
	protected void filter() throws EventHandledException {

		// The time is always now -Jesus
		final long now = System.currentTimeMillis();

		final List<Output> lastOutputs = senderCache.getLastOutputs(type, get(AntiSpam.Chat.SIMILARITY_PAST_MESSAGES, AntiSpam.Commands.SIMILARITY_PAST_COMMANDS), channel);
		final Output lastOutput = lastOutputs.isEmpty() ? Output.NO_OUTPUT : lastOutputs.get(lastOutputs.size() - 1);

		// If the check relates to a command, this is the command label such as /tell
		final String label = message.split(" ")[0];
		final String lastLabel = lastOutput.getOutput().split(" ")[0];

		// Antibot -- the checker has been called from join event
		if (isPlayer && senderCache.hasJoinLocation() && !senderCache.isMovedFromJoin()) {

			// Prevent antimove firing again when player returns to his join location
			if (!Valid.locationEquals(senderCache.getJoinLocation(), player.getLocation()))
				senderCache.setMovedFromJoin(true);

			else if (!hasPerm(Permissions.Bypass.MOVE))
				if (get(AntiBot.BLOCK_CHAT_UNTIL_MOVED, AntiBot.BLOCK_CMDS_UNTIL_MOVED.isInList(label)))
					cancel(Lang.ofScript("Checker.Move", SerializedMap.of("chat", this.type == Type.CHAT)));

			final long inputDelay = get(AntiBot.COOLDOWN_CHAT_AFTER_JOIN, AntiBot.COOLDOWN_COMMAND_AFTER_JOIN).getTimeSeconds();
			final long lastLogin = senderCache.getLastLogin();
			final long playtime = (now - lastLogin) / 1000;

			if (playtime < inputDelay)
				cancel(Lang.ofScript("Checker.Delay_After_Join", SerializedMap.of("chat", this.type == Type.CHAT)).replace("{seconds}", Lang.ofCase(inputDelay - playtime, "Cases.Second")));
		}

		// Delay
		if (!(sender instanceof ConsoleCommandSender) && !hasPerm(Permissions.Bypass.DELAY) && lastOutput.getTime() != -1) {
			final boolean isWhitelisted = type == Type.CHAT ? AntiSpam.Chat.WHITELIST_DELAY.isInListRegex(lastOutput.getOutput()) : AntiSpam.Commands.WHITELIST_DELAY.isInList(lastLabel);

			if (!isWhitelisted) {
				// Pull the default one
				final SimpleTime channelSpecificDelay = channel != null && channel.getMessageDelay() != null ? channel.getMessageDelay() : null;

				final long delay = get(AntiSpam.Chat.DELAY, AntiSpam.Commands.DELAY).getFor(sender, channelSpecificDelay).getTimeSeconds();
				final long lastMessageDelay = (now - lastOutput.getTime()) / 1000;

				if (delay > lastMessageDelay) {
					final long remainingTime = delay - lastMessageDelay;

					get(WarningPoints.TRIGGER_CHAT_DELAY, WarningPoints.TRIGGER_COMMAND_DELAY)
							.execute(sender,
									Lang.ofScript("Checker.Delay",
											SerializedMap.of("chat", this.type == Type.CHAT ? true : false)).replace("{seconds}", Lang.ofCase(remainingTime, "Cases.Second")),
									SerializedMap.of("remaining_time", delay));
				}
			}
		}

		// Period
		if (!hasPerm(Permissions.Bypass.PERIOD)) {
			final SimpleTime period = get(AntiSpam.Chat.LIMIT_PERIOD, AntiSpam.Commands.LIMIT_PERIOD);
			final long periodTime = now - (period.getTimeSeconds() * 1000);
			final int periodLimit = get(AntiSpam.Chat.LIMIT_MAX, AntiSpam.Commands.LIMIT_MAX);

			if (senderCache.getOutputsAfter(type, periodTime, channel).size() >= periodLimit)
				get(WarningPoints.TRIGGER_CHAT_LIMIT, WarningPoints.TRIGGER_COMMAND_LIMIT).execute(sender,
						Lang.of("Checker.Period")
								.replace("{type_amount}", String.valueOf(periodLimit))
								.replace("{type}", Lang.ofCaseNoAmount(periodLimit, get("Cases.Message", "Cases.Command")))
								.replace("{period_amount}", String.valueOf(period.getTimeSeconds()))
								.replace("{period}", Lang.ofCaseNoAmount(period.getTimeSeconds(), "Cases.Second")),
						SerializedMap.of("messages_in_period", periodLimit));
		}

		// Anticaps
		if (get(AntiCaps.ENABLED, AntiCaps.ENABLED_IN_COMMANDS.isInList(label)) && !hasPerm(Permissions.Bypass.CAPS) && message.length() >= AntiCaps.MIN_MESSAGE_LENGTH) {
			final int capsPercentage = (int) (ChatUtil.getCapsPercentage(Common.stripColors(message)) * 100);
			String messageAfter = message;

			if (capsPercentage >= AntiCaps.MIN_CAPS_PERCENTAGE || ChatUtil.getCapsInRow(Common.stripColors(message), AntiCaps.WHITELIST) >= AntiCaps.MIN_CAPS_IN_A_ROW) {
				final String[] words = message.split(" ");

				boolean capsAllowed = false;
				boolean whitelisted = false;

				for (int i = 0; i < words.length; i++) {
					final String word = words[i];

					if (ChatUtil.isDomain(word)) {
						whitelisted = true;
						capsAllowed = true;
					}

					// Filter whitelist
					if (AntiCaps.WHITELIST.isInList(word)) {
						whitelisted = true;
						capsAllowed = true;

						continue;
					}

					// Exclude user names
					for (final Player online : Remain.getOnlinePlayers())
						if (online.getName().equalsIgnoreCase(word)) {
							whitelisted = true;
							capsAllowed = true;

							continue;
						}

					if (!whitelisted) {
						if (!capsAllowed) {
							final char firstChar = word.charAt(0);

							words[i] = firstChar + word.toLowerCase().substring(1);

						} else
							words[i] = word.toLowerCase();

						capsAllowed = !words[i].endsWith(".") && !words[i].endsWith("!") && !words[i].endsWith("?");
					}

					whitelisted = false;
				}

				messageAfter = String.join(" ", words);

				if (!Common.stripColors(message).equals(Common.stripColors(messageAfter))) {
					message = messageAfter;

					try {
						WarningPoints.TRIGGER_CAPS.execute(sender,
								Lang.of("Checker.Caps").replace("{type}", Lang.ofCaseNoAmount(1, get("Cases.Message", "Cases.Command"))),
								SerializedMap.of("caps_percentage_double", capsPercentage / 100D));

					} catch (final EventHandledException ex) {

						// Do not cancel, just warn
						for (final String message : ex.getMessages())
							Messenger.warn(sender, message);
					}
				}
			}
		}

		// Filter rules
		final RuleCheck<Rule> rulesCheck = Rule.filter(get(Rule.Type.CHAT, Rule.Type.COMMAND), sender, message, channel);

		message = rulesCheck.getMessage();

		if (!isCancelledSilently() && rulesCheck.isCancelledSilently())
			this.cancelledSilently = true;

		// Similarity -- filter after rules since rules can change the message
		if (!hasPerm(Permissions.Bypass.SIMILARITY)) {
			final double threshold = get(AntiSpam.Chat.SIMILARITY, AntiSpam.Commands.SIMILARITY).getFor(this.sender);

			if (threshold > 0)
				for (final Output output : lastOutputs) {

					if ((now - output.getTime()) > get(AntiSpam.Chat.SIMILARITY_TIME, AntiSpam.Commands.SIMILARITY_TIME).getTimeSeconds() * 1000)
						continue;

					final boolean isWhitelisted = type == Type.CHAT ? AntiSpam.Chat.WHITELIST_SIMILARITY.isInListRegex(lastOutput.getOutput())
							: AntiSpam.Commands.WHITELIST_SIMILARITY.isInList(output.getOutput().split(" ")[0]);

					if (isWhitelisted)
						continue;

					final double similarity = ChatUtil.getSimilarityPercentage(output.getOutput(), message);

					if (similarity >= threshold)
						get(WarningPoints.TRIGGER_CHAT_SIMILARITY, WarningPoints.TRIGGER_COMMAND_SIMILARITY).execute(sender,
								Lang.ofScript("Checker.Similarity", SerializedMap.of("chat", this.type == Type.CHAT)).replace("{similarity}", String.valueOf(Math.round(similarity * 100))),
								SerializedMap.of("similarity_percentage_double", threshold));
				}
		}

		// Cache last message before grammar
		if (type == Type.CHAT)
			senderCache.cacheMessage(message, channel);

		else if (type == Type.COMMAND)
			senderCache.cacheCommand(message);

		else
			throw new FoException("Caching of " + type + " not implemented yet");

		// Grammar
		if (type == Type.CHAT && !hasPerm(Permissions.Bypass.GRAMMAR)) {
			if (Grammar.CAPITALIZE_MSG_LENGTH != 0 && message.length() >= Grammar.CAPITALIZE_MSG_LENGTH)
				message = ChatUtil.capitalize(message);

			if (Grammar.INSERT_DOT_MSG_LENGTH != 0 && message.length() >= Grammar.INSERT_DOT_MSG_LENGTH)
				message = ChatUtil.insertDot(message);
		}
	}

	/*
	 * Return if the sender has the given permission
	 *
	 * @param permission
	 * @return
	 */
	private boolean hasPerm(String permission) {
		return PlayerUtil.hasPerm(this.sender, permission);
	}

	/*
	 * Cancels the pipeline by throgin a {@link EventHandledException}
	 * and send an error message to the player
	 *
	 * @param errorMessage
	 */
	private void cancel(@NotNull String errorMessage) {
		throw new EventHandledException(true, Variables.replace(errorMessage, sender));
	}

	/*
	 * If the checker checks chat, return first argument, if checker checks commands
	 * return the second
	 */
	private <T> T get(T returnThisIfChat, T returnThisIfCommand) {
		return this.type == Type.CHAT ? returnThisIfChat : returnThisIfCommand;
	}
}
