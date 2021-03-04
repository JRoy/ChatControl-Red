package org.mineacademy.chatcontrol.command;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.PlayerCache;
import org.mineacademy.chatcontrol.SenderCache;
import org.mineacademy.chatcontrol.ServerCache;
import org.mineacademy.chatcontrol.model.Bungee.BungeePacket;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.chatcontrol.model.Channel.Mode;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.Players;
import org.mineacademy.chatcontrol.model.UserMap;
import org.mineacademy.chatcontrol.operator.Group;
import org.mineacademy.chatcontrol.operator.Groups;
import org.mineacademy.chatcontrol.operator.PlayerMessage;
import org.mineacademy.chatcontrol.operator.PlayerMessages;
import org.mineacademy.chatcontrol.operator.Rule;
import org.mineacademy.chatcontrol.operator.Rules;
import org.mineacademy.chatcontrol.operator.Tag;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.chatcontrol.settings.Settings.Integration.BungeeCord;
import org.mineacademy.fo.BungeeUtil;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.Messenger;
import org.mineacademy.fo.TabUtil;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.collection.StrictList;
import org.mineacademy.fo.command.DebugCommand;
import org.mineacademy.fo.command.PermsCommand;
import org.mineacademy.fo.command.ReloadCommand;
import org.mineacademy.fo.command.SimpleCommand;
import org.mineacademy.fo.command.SimpleCommandGroup;
import org.mineacademy.fo.command.SimpleSubCommand;
import org.mineacademy.fo.exception.CommandException;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.plugin.SimplePlugin;
import org.mineacademy.fo.settings.SimpleLocalization;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * Holds all /chc main commands
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ChatControlCommands extends SimpleCommandGroup {

	/**
	 * The singleton of this class
	 */
	@Getter
	private final static SimpleCommandGroup instance = new ChatControlCommands();

	/**
	 * @see org.mineacademy.fo.command.SimpleCommandGroup#getHeaderPrefix()
	 */
	@Override
	protected String getHeaderPrefix() {
		return "" + ChatColor.DARK_RED + ChatColor.BOLD;
	}

	@Override
	protected List<SimpleComponent> getNoParamsHeader(CommandSender sender) {
		return Lang.getOption("Commands.Use_Alternative_Header")
				? Lang.ofComponentList("Commands.Alternative_Header", Settings.MAIN_COMMAND_ALIASES.get(0))
				: super.getNoParamsHeader(sender);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommandGroup#registerSubcommands()
	 */
	@Override
	protected void registerSubcommands() {

		registerSubcommand(new ChatControlAnnounce());
		registerSubcommand(new ChatControlBook());
		registerSubcommand(new ChatControlForward());
		registerSubcommand(new ChatControlClear());
		registerSubcommand(new ChatControlColor());
		registerSubcommand(new ChatControlInfo());
		registerSubcommand(new ChatControlInspect());
		registerSubcommand(new ChatControlInternal());
		registerSubcommand(new ChatControlLog());
		registerSubcommand(new ChatControlMessage());
		registerSubcommand(new ChatControlMigrate());
		registerSubcommand(new ChatControlPoints());
		registerSubcommand(new ChatControlPurge());
		registerSubcommand(new ChatControlRegion());
		registerSubcommand(new ChatControlRule());
		registerSubcommand(new ChatControlScript());
		registerSubcommand(new ChatControlTag());
		registerSubcommand(new ChatControlTour());
		registerSubcommand(new ChatControlUpdate());

		// Register the premade commands from Foundation
		final PermsCommand permsCommand = new PermsCommand(Permissions.class, SerializedMap.ofArray(
				"label", Settings.MAIN_COMMAND_ALIASES.get(0),
				"label_channel", Settings.Channels.COMMAND_ALIASES.get(0)));

		permsCommand.setPermission(Permissions.Command.PERMISSIONS);
		registerSubcommand(permsCommand);

		final DebugCommand debugCommand = new DebugCommand();

		debugCommand.setPermission(Permissions.Command.DEBUG);
		registerSubcommand(debugCommand);

		final ReloadCommand reloadCommand = new ReloadCommand();

		reloadCommand.setPermission(Permissions.Command.RELOAD);
		registerSubcommand(reloadCommand);

	}

	/* ------------------------------------------------------------------------------- */
	/* Classes */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Represents the core for a bunch of other commands having same flags
	 * such as -bungee -silent etc.
	 */
	public static abstract class CommandFlagged extends ChatControlSubCommand {

		protected CommandFlagged(String label, String usage, String description) {
			super(label);

			setUsage(usage);
			setDescription(description);
		}

		/**
		 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
		 */
		@Override
		protected abstract String[] getMultilineUsageMessage();

		/**
		 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
		 */
		@Override
		protected final void execute() {
			final List<String> params = Arrays.asList(args);

			final boolean console = params.contains("-console") || params.contains("-c");
			final boolean silent = params.contains("-silent") || params.contains("-s");
			final boolean anonymous = params.contains("-anonymous") || params.contains("-a");
			final boolean raw = params.contains("-raw") || params.contains("-r");

			final String reason = String.join(" ", params.stream().filter(param -> !param.startsWith("-")).collect(Collectors.toList()));

			if (!console && !silent && !anonymous && !params.isEmpty() && reason.isEmpty())
				returnUsage();

			if (silent && !reason.isEmpty())
				returnTell(Lang.of("Commands.No_Reason_While_Silent"));

			if (silent && anonymous)
				returnTell(Lang.of("Commands.No_Silent_And_Anonymous"));

			execute(console, anonymous, silent, raw, reason);
		}

		/**
		 * Execute this command
		 *
		 * @param console
		 * @param anonymous
		 * @param silent
		 * @param raw
		 *
		 * @param reason
		 */
		protected abstract void execute(boolean console, boolean anonymous, boolean silent, boolean raw, String reason);

		/**
		 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
		 */
		@Override
		protected List<String> tabComplete() {
			return NO_COMPLETE;
		}
	}

	/**
	 * Represents the foundation for plugin commands
	 */
	public static abstract class ChatControlCommand extends SimpleCommand {

		protected ChatControlCommand(String label) {
			super(label);
		}

		protected ChatControlCommand(StrictList<String> labels) {
			super(labels);
		}

		/**
		 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
		 */
		@Override
		protected final void onCommand() {

			if (!ServerCache.getInstance().isTourCompleted()) {
				checkBoolean(sender.isOp(), Lang.of("Commands.Tour.Requires_Op"));

				returnTell(Lang.of("Commands.Tour.Not_Completed", Settings.MAIN_COMMAND_ALIASES.get(0)));
			}

			this.execute();
		}

		/**
		 * Execute this command
		 */
		protected abstract void execute();

		/**
		 * Return a channel by name or send error message to player if wrong name
		 *
		 * @param channelName
		 * @return
		 */
		protected final Channel findChannel(@NonNull String channelName) {
			final Channel channel = Channel.findChannel(channelName);
			checkNotNull(channel, Lang.of("Commands.Invalid_Channel", channelName, Common.join(Channel.getChannelNames())));

			return channel;
		}

		/**
		 * Return channel mode by name or send error to player if wrong mode
		 *
		 * @param modeName
		 * @return
		 */
		protected final Channel.Mode findMode(@NonNull String modeName) {
			try {
				return Mode.fromKey(modeName);

			} catch (final IllegalArgumentException ex) {
				tellError(Lang.of("Commands.Invalid_Mode", modeName, Common.join(Mode.values())));

				throw new CommandException();
			}
		}

		/**
		 * Return a rule by the name or send error message to the player
		 *
		 * @param ruleName
		 * @return
		 */
		protected final Rule findRule(String ruleName) {
			final List<Rule> namedRules = Rules.getInstance().getRulesWithName();
			final Rule rule = Rules.getInstance().findRule(ruleName);
			final String available = namedRules.isEmpty() ? Lang.of("None") : Common.join(namedRules, ", ", Rule::getName);

			checkNotNull(ruleName, Lang.of("Commands.No_Rule_Name", available));
			checkNotNull(rule, Lang.of("Commands.Invalid_Rule", ruleName, available));

			return rule;
		}

		/**
		 * Parse rule type from name or send error message to the player
		 *
		 * @param typeName
		 * @return
		 */
		protected final Rule.Type findRuleType(String typeName) {
			try {
				return Rule.Type.fromKey(typeName);

			} catch (final IllegalArgumentException ex) {
				returnTell(Lang.of("Commands.Invalid_Type", typeName, Common.join(Rule.Type.values())));

				return null;
			}
		}

		/**
		 * Return a rule group by the name or send error message to the player
		 *
		 * @param groupName
		 * @return
		 */
		protected final Group findRuleGroup(String groupName) {
			final List<String> groups = Groups.getInstance().getGroupNames();
			final Group group = Groups.getInstance().findGroup(groupName);
			final String available = (groups.isEmpty() ? Lang.of("None") : Common.join(groups));

			checkNotNull(groupName, Lang.of("Commands.No_Group_Name", available));
			checkNotNull(group, Lang.of("Commands.Invalid_Group", groupName, available));

			return group;
		}

		/**
		 * Return a rule by the name or send error message to the player
		 *
		 * @param groupName
		 * @return
		 */
		protected final PlayerMessage findMessage(PlayerMessage.Type type, String groupName) {
			final Set<String> itemNames = PlayerMessages.getInstance().getMessageNames(type);
			final PlayerMessage items = PlayerMessages.getInstance().findMessage(type, groupName);
			final String available = itemNames.isEmpty() ? Lang.of("None") : Common.join(itemNames);

			checkNotNull(groupName, Lang.of("Commands.No_Message_Name", available));
			checkNotNull(items, Lang.of("Commands.Invalid_Message", groupName, available));

			return items;
		}

		/**
		 * Parse rule type from name or send error message to the player
		 *
		 * @param typeName
		 * @return
		 */
		protected final PlayerMessage.Type findMessageType(String typeName) {
			try {
				final PlayerMessage.Type type = PlayerMessage.Type.fromKey(typeName);

				if (!Settings.Messages.APPLY_ON.contains(type))
					throw new IllegalArgumentException();

				return type;

			} catch (final IllegalArgumentException ex) {
				returnTell(Lang.of("Commands.Invalid_Type", typeName, Common.join(PlayerMessage.Type.values())));

				return null;
			}
		}

		/**
		 * Return the disk cache for name or if name is null and we are not console then return it for the sender
		 *
		 * @param nameOrNick
		 * @return
		 * @throws CommandException
		 */
		protected final void pollDiskCacheOrSelf(@Nullable final String nameOrNick, Consumer<PlayerCache> syncCallback) throws CommandException {
			if (nameOrNick == null) {
				checkBoolean(isPlayer(), Lang.of("Commands.Console_Missing_Player_Name"));

				syncCallback.accept(PlayerCache.from(getPlayer()));
				return;
			}

			this.pollCache(nameOrNick, syncCallback);
		}

		/**
		 * Attempts to get a player cache by his name or nick from either
		 * data.db or database. Since this is a blocking operation, we have a synced
		 * callback here.
		 *
		 * @param nameOrNick
		 * @param syncCallback
		 */
		protected final void pollCache(String nameOrNick, Consumer<PlayerCache> syncCallback) {
			final SenderCache senderCache = SenderCache.from(sender);

			// Prevent calling this again when loading
			senderCache.setLoadingMySQL(true);

			PlayerCache.poll(nameOrNick, cache -> {

				handleCallbackCommand(sender,
						() -> {
							checkNotNull(cache, Lang.of("Player.Not_Stored", nameOrNick));

							// Prevent calling save when handling callback
							//cache.setAllowSave(false);

							syncCallback.accept(cache);

						}, () -> {
							senderCache.setLoadingMySQL(false);

							// Update the cache all at once, saving db/data.db blocking calls
							if (cache != null) {
								//cache.setAllowSave(true);
								cache.save();
							}
						});
			});
		}

		/**
		 * Get certain information async and then process this information in the sync callback
		 *
		 * @param <T>
		 * @param asyncGetter
		 * @param syncCallback
		 */
		protected final <T> void syncCallback(Supplier<T> asyncGetter, Consumer<T> syncCallback) {
			final SenderCache senderCache = SenderCache.from(sender);

			// Prevent calling this again when loading
			senderCache.setLoadingMySQL(true);

			// Run the getter async
			asyncCallbackCommand(sender, () -> {
				final T value = asyncGetter.get();

				// Then run callback on the main thread
				syncCallbackCommand(sender,
						() -> syncCallback.accept(value),
						() -> senderCache.setLoadingMySQL(false));

			}, () -> senderCache.setLoadingMySQL(false));
		}

		/**
		 * Handles callback for all caches on disk or database
		 *
		 * @param syncCallback
		 */
		protected final void pollCaches(Consumer<List<PlayerCache>> syncCallback) {
			final SenderCache senderCache = SenderCache.from(sender);

			tellInfo(Lang.of("Commands.Compiling_Data"));

			// Prevent calling this again when loading
			senderCache.setLoadingMySQL(true);

			PlayerCache.pollAll(caches -> handleCallbackCommand(sender,
					() -> syncCallback.accept(caches),
					() -> senderCache.setLoadingMySQL(false)));
		}

		/**
		 * Parse dog tag or send error message to the player
		 *
		 * @param name
		 * @return
		 */
		protected final Tag.Type findTag(String name) {
			try {
				final Tag.Type tag = Tag.Type.fromKey(name);

				if (!Settings.Tag.APPLY_ON.contains(tag))
					throw new IllegalArgumentException();

				return tag;

			} catch (final IllegalArgumentException ex) {
				returnTell(Lang.ofArray("Commands.Invalid_Tag", name, Common.join(Tag.Type.values())));

				return null;
			}
		}

		/**
		 * Special method used for nicks/prefixes/suffixes
		 */
		protected final void setTag(Tag.Type type, PlayerCache cache, String newTag) {
			final boolean remove = "off".equals(Common.stripColors(newTag));
			final boolean self = sender.getName().equals(cache.getPlayerName());

			checkBoolean(!remove || cache.hasTag(type), Lang.ofScript("Commands.Tag.No_Tag", SerializedMap.of("self", self), self ? Lang.of("You") : cache.getPlayerName(), type.getKey()));

			if (newTag.startsWith("\"") || newTag.startsWith("'"))
				newTag = newTag.substring(1);

			if (newTag.endsWith("\"") || newTag.endsWith("'"))
				newTag = newTag.substring(0, newTag.length() - 1);

			final String colorlessTag = Common.stripColors(newTag);

			if (type == Tag.Type.NICK && !colorlessTag.equalsIgnoreCase(sender.getName())) {
				if (Settings.Tag.NICK_DISABLE_IMPERSONATION)
					checkBoolean(!UserMap.getInstance().isPlayerNameStored(colorlessTag), Lang.of("Commands.Tag.No_Impersonation"));

				final String nickOwner = UserMap.getInstance().getName(newTag);
				checkBoolean(nickOwner == null || nickOwner.equalsIgnoreCase(cache.getPlayerName()), Lang.of("Commands.Tag.Already_Used"));
			}

			cache.setTag(type, remove ? null : newTag);

			if (!BungeeCord.ENABLED && type == Tag.Type.NICK && self) {
				final Player cachePlayer = cache.toPlayer();

				if (cachePlayer != null)
					Players.setTablistName(sender, cachePlayer);
			}

			final String message = Lang.ofScript("Commands.Tag.Success", SerializedMap.of("remove", remove), type, "\"" + newTag + "\"");

			if (!BungeeCord.ENABLED || !self)
				tell(message.replace("{person}", self ? Lang.of("Your")
						: Lang.of("Player.Possessive_Form", cache.getPlayerName())
								.replace("{player}", cache.getPlayerName())));

			// Notify BungeeCord so that players connected on another server get their channel updated
			updateBungeeData(cache, message.replace("{person}", Lang.of("Your")));
		}

		/**
		 * Makes all other servers on the network get database updated for
		 * the given player cache
		 *
		 * @param cache
		 * @throws CommandException
		 */
		protected final void updateBungeeData(PlayerCache cache) throws CommandException {
			this.updateBungeeData(cache, null);
		}

		/**
		 * Makes all other servers on the network get database updated for
		 * the given player cache, sending the player the specified message
		 *
		 * @param cache
		 * @param message
		 * @throws CommandException
		 */
		protected final void updateBungeeData(PlayerCache cache, @Nullable String message) throws CommandException {
			if (BungeeCord.ENABLED) {
				message = message == null ? "" : message.replace("{person}", Lang.of("You").replace("{pronoun}", Lang.of("Your").toLowerCase()));
				final String nick = UserMap.getInstance().getNick(cache.getPlayerName());

				BungeeUtil.tellBungee(BungeePacket.DB_UPDATE,
						cache.getPlayerName(),
						cache.getUniqueId(),
						Common.getOrDefaultStrict(nick, ""),
						cache.serialize(),
						message);
			}
		}

		/**
		 * @see org.mineacademy.fo.command.SimpleCommand#findPlayerInternal(java.lang.String)
		 */
		@Override
		protected final Player findPlayerInternal(String name) {
			return Players.getPlayer(name);
		}

		/**
		 * @see org.mineacademy.fo.command.SimpleCommand#completeLastWordPlayerNames()
		 */
		@Override
		protected List<String> completeLastWordPlayerNames() {
			return TabUtil.complete(getLastArg(), Players.getPlayerNames(sender));
		}
	}

	/**
	 * Represents the main plugin command
	 */
	public static abstract class ChatControlSubCommand extends GenericSubCommand {

		protected ChatControlSubCommand(String sublabel) {
			super(SimplePlugin.getInstance().getMainCommand(), sublabel);
		}
	}

	/**
	 * Represents the foundation for plugin commands
	 * used for /channel and /chc subcommands
	 */
	public static abstract class GenericSubCommand extends SimpleSubCommand {

		protected GenericSubCommand(SimpleCommandGroup group, String sublabel) {
			super(group, sublabel);
		}

		/**
		 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
		 */
		@Override
		protected final void onCommand() {

			if (!ServerCache.getInstance().isTourCompleted() && !(this instanceof ChatControlTour)) {
				checkBoolean(sender.isOp(), Lang.of("Commands.Tour.Requires_Op"));

				returnTell(Lang.of("Commands.Tour.Not_Completed", Settings.MAIN_COMMAND_ALIASES.get(0)));
			}

			this.execute();
		}

		/**
		 * Execute this command
		 */
		protected abstract void execute();

		/**
		 * Return a channel by name or send error message to player if wrong name
		 *
		 * @param channelName
		 * @return
		 */
		protected final Channel findChannel(@NonNull String channelName) {
			final Channel channel = Channel.findChannel(channelName);
			checkNotNull(channel, Lang.of("Commands.Invalid_Channel", channelName, Common.join(Channel.getChannelNames())));

			return channel;
		}

		/**
		 * Return channel mode by name or send error to player if wrong mode
		 *
		 * @param modeName
		 * @return
		 */
		protected final Channel.Mode findMode(@NonNull String modeName) {
			try {
				return Mode.fromKey(modeName);

			} catch (final IllegalArgumentException ex) {
				tellError(Lang.of("Commands.Invalid_Mode", modeName, Common.join(Mode.values())));

				throw new CommandException();
			}
		}

		/**
		 * Return a rule by the name or send error message to the player
		 *
		 * @param ruleName
		 * @return
		 */
		protected final Rule findRule(String ruleName) {
			final List<Rule> namedRules = Rules.getInstance().getRulesWithName();
			final Rule rule = Rules.getInstance().findRule(ruleName);
			final String available = namedRules.isEmpty() ? Lang.of("None") : Common.join(namedRules, ", ", Rule::getName);

			checkNotNull(ruleName, Lang.of("Commands.No_Rule_Name", available));
			checkNotNull(rule, Lang.of("Commands.Invalid_Rule", ruleName, available));

			return rule;
		}

		/**
		 * Parse rule type from name or send error message to the player
		 *
		 * @param typeName
		 * @return
		 */
		protected final Rule.Type findRuleType(String typeName) {
			try {
				return Rule.Type.fromKey(typeName);

			} catch (final IllegalArgumentException ex) {
				returnTell(Lang.of("Commands.Invalid_Type", typeName, Common.join(Rule.Type.values())));

				return null;
			}
		}

		/**
		 * Return a rule group by the name or send error message to the player
		 *
		 * @param groupName
		 * @return
		 */
		protected final Group findRuleGroup(String groupName) {
			final List<String> groups = Groups.getInstance().getGroupNames();
			final Group group = Groups.getInstance().findGroup(groupName);
			final String available = (groups.isEmpty() ? Lang.of("None") : Common.join(groups));

			checkNotNull(groupName, Lang.of("Commands.No_Group_Name", available));
			checkNotNull(group, Lang.of("Commands.Invalid_Group", groupName, available));

			return group;
		}

		/**
		 * Return a rule by the name or send error message to the player
		 *
		 * @param groupName
		 * @return
		 */
		protected final PlayerMessage findMessage(PlayerMessage.Type type, String groupName) {
			final Set<String> itemNames = PlayerMessages.getInstance().getMessageNames(type);
			final PlayerMessage items = PlayerMessages.getInstance().findMessage(type, groupName);
			final String available = itemNames.isEmpty() ? Lang.of("None") : Common.join(itemNames);

			checkNotNull(groupName, Lang.of("Commands.No_Message_Name", available));
			checkNotNull(items, Lang.of("Commands.Invalid_Message", groupName, available));

			return items;
		}

		/**
		 * Parse rule type from name or send error message to the player
		 *
		 * @param typeName
		 * @return
		 */
		protected final PlayerMessage.Type findMessageType(String typeName) {
			try {
				final PlayerMessage.Type type = PlayerMessage.Type.fromKey(typeName);

				if (!Settings.Messages.APPLY_ON.contains(type))
					throw new IllegalArgumentException();

				return type;

			} catch (final IllegalArgumentException ex) {
				returnTell(Lang.of("Commands.Invalid_Type", typeName, Common.join(PlayerMessage.Type.values())));

				return null;
			}
		}

		/**
		 * Return the disk cache for name or if name is null and we are not console then return it for the sender
		 *
		 * @param nameOrNick
		 * @return
		 * @throws CommandException
		 */
		protected final void pollDiskCacheOrSelf(@Nullable final String nameOrNick, Consumer<PlayerCache> syncCallback) throws CommandException {
			if (nameOrNick == null) {
				checkBoolean(isPlayer(), Lang.of("Commands.Console_Missing_Player_Name"));

				syncCallback.accept(PlayerCache.from(getPlayer()));
				return;
			}

			this.pollCache(nameOrNick, syncCallback);
		}

		/**
		 * Attempts to get a player cache by his name or nick from either
		 * data.db or database. Since this is a blocking operation, we have a synced
		 * callback here.
		 *
		 * @param nameOrNick
		 * @param syncCallback
		 */
		protected final void pollCache(String nameOrNick, Consumer<PlayerCache> syncCallback) {
			final SenderCache senderCache = SenderCache.from(sender);

			// Prevent calling this again when loading
			senderCache.setLoadingMySQL(true);

			PlayerCache.poll(nameOrNick, cache -> {

				handleCallbackCommand(sender,
						() -> {
							checkNotNull(cache, Lang.of("Player.Not_Stored", nameOrNick));

							// Prevent calling save when handling callback
							//cache.setAllowSave(false);

							syncCallback.accept(cache);

						}, () -> {
							senderCache.setLoadingMySQL(false);

							// Update the cache all at once, saving db/data.db blocking calls
							if (cache != null) {
								//cache.setAllowSave(true);
								cache.save();
							}
						});
			});
		}

		/**
		 * Get certain information async and then process this information in the sync callback
		 *
		 * @param <T>
		 * @param asyncGetter
		 * @param syncCallback
		 */
		protected final <T> void syncCallback(Supplier<T> asyncGetter, Consumer<T> syncCallback) {
			final SenderCache senderCache = SenderCache.from(sender);

			// Prevent calling this again when loading
			senderCache.setLoadingMySQL(true);

			// Run the getter async
			asyncCallbackCommand(sender, () -> {
				final T value = asyncGetter.get();

				// Then run callback on the main thread
				syncCallbackCommand(sender,
						() -> syncCallback.accept(value),
						() -> senderCache.setLoadingMySQL(false));

			}, () -> senderCache.setLoadingMySQL(false));
		}

		/**
		 * Handles callback for all caches on disk or database
		 *
		 * @param syncCallback
		 */
		protected final void pollCaches(Consumer<List<PlayerCache>> syncCallback) {
			final SenderCache senderCache = SenderCache.from(sender);

			tellInfo(Lang.of("Commands.Compiling_Data"));

			// Prevent calling this again when loading
			senderCache.setLoadingMySQL(true);

			PlayerCache.pollAll(caches -> handleCallbackCommand(sender,
					() -> syncCallback.accept(caches),
					() -> senderCache.setLoadingMySQL(false)));
		}

		/**
		 * Parse dog tag or send error message to the player
		 *
		 * @param name
		 * @return
		 */
		protected final Tag.Type findTag(String name) {
			try {
				final Tag.Type tag = Tag.Type.fromKey(name);

				if (!Settings.Tag.APPLY_ON.contains(tag))
					throw new IllegalArgumentException();

				return tag;

			} catch (final IllegalArgumentException ex) {
				returnTell(Lang.ofArray("Commands.Invalid_Tag", name, Common.join(Tag.Type.values())));

				return null;
			}
		}

		/**
		 * Special method used for nicks/prefixes/suffixes
		 */
		protected final void setTag(Tag.Type type, PlayerCache cache, String newTag) {
			final boolean remove = "off".equals(Common.stripColors(newTag));
			final boolean self = sender.getName().equals(cache.getPlayerName());

			checkBoolean(!remove || cache.hasTag(type), Lang.ofScript("Commands.Tag.No_Tag", SerializedMap.of("self", self), self ? Lang.of("You") : cache.getPlayerName(), type.getKey()));

			if (newTag.startsWith("\"") || newTag.startsWith("'"))
				newTag = newTag.substring(1);

			if (newTag.endsWith("\"") || newTag.endsWith("'"))
				newTag = newTag.substring(0, newTag.length() - 1);

			final String colorlessTag = Common.stripColors(newTag);

			if (type == Tag.Type.NICK && !colorlessTag.equalsIgnoreCase(sender.getName())) {
				if (Settings.Tag.NICK_DISABLE_IMPERSONATION)
					checkBoolean(!UserMap.getInstance().isPlayerNameStored(colorlessTag), Lang.of("Commands.Tag.No_Impersonation"));

				final String nickOwner = UserMap.getInstance().getName(newTag);
				checkBoolean(nickOwner == null || nickOwner.equalsIgnoreCase(cache.getPlayerName()), Lang.of("Commands.Tag.Already_Used"));
			}

			cache.setTag(type, remove ? null : newTag);

			if (!BungeeCord.ENABLED && type == Tag.Type.NICK && self) {
				final Player cachePlayer = cache.toPlayer();

				if (cachePlayer != null)
					Players.setTablistName(sender, cachePlayer);
			}

			final String message = Lang.ofScript("Commands.Tag.Success", SerializedMap.of("remove", remove), type, "\"" + newTag + "\"");

			if (!BungeeCord.ENABLED || !self)
				tell(message.replace("{person}", self ? Lang.of("Your")
						: Lang.of("Player.Possessive_Form", cache.getPlayerName())
								.replace("{player}", cache.getPlayerName())));

			// Notify BungeeCord so that players connected on another server get their channel updated
			updateBungeeData(cache, message.replace("{person}", Lang.of("Your")));
		}

		/**
		 * Makes all other servers on the network get database updated for
		 * the given player cache
		 *
		 * @param cache
		 * @throws CommandException
		 */
		protected final void updateBungeeData(PlayerCache cache) throws CommandException {
			this.updateBungeeData(cache, null);
		}

		/**
		 * Makes all other servers on the network get database updated for
		 * the given player cache, sending the player the specified message
		 *
		 * @param cache
		 * @param message
		 * @throws CommandException
		 */
		protected final void updateBungeeData(PlayerCache cache, @Nullable String message) throws CommandException {
			if (BungeeCord.ENABLED) {
				message = message == null ? "" : message.replace("{person}", Lang.of("You").replace("{pronoun}", Lang.of("Your").toLowerCase()));
				final String nick = UserMap.getInstance().getNick(cache.getPlayerName());

				BungeeUtil.tellBungee(BungeePacket.DB_UPDATE,
						cache.getPlayerName(),
						cache.getUniqueId(),
						Common.getOrDefaultStrict(nick, ""),
						cache.serialize(),
						message);
			}
		}

		/**
		 * @see org.mineacademy.fo.command.SimpleCommand#findPlayerInternal(java.lang.String)
		 */
		@Override
		protected final Player findPlayerInternal(String name) {
			return Players.getPlayer(name);
		}

		/**
		 * @see org.mineacademy.fo.command.SimpleCommand#completeLastWordPlayerNames()
		 */
		@Override
		protected List<String> completeLastWordPlayerNames() {
			return TabUtil.complete(getLastArg(), Players.getPlayerNames(sender));
		}
	}

	/*
	 * See below, but everything is wrapped and run async
	 */
	private static void asyncCallbackCommand(CommandSender sender, Runnable callback, Runnable finallyCallback) {
		Common.runAsync(() -> handleCallbackCommand(sender, callback, finallyCallback));
	}

	/*
	 * See below, but everything is wrapped and run on the main thread
	 */
	private static void syncCallbackCommand(CommandSender sender, Runnable callback, Runnable finallyCallback) {
		Common.runLater(() -> handleCallbackCommand(sender, callback, finallyCallback));
	}

	/*
	 * Wraps the given callback in try-catch clause and sends all CommandExceptions to the sender,
	 * then runs the finally clause with the finallyCallback
	 */
	private static void handleCallbackCommand(CommandSender sender, Runnable callback, Runnable finallyCallback) {
		try {
			callback.run();

		} catch (final CommandException ex) {
			if (ex.getMessages() != null)
				for (final String message : ex.getMessages())
					Messenger.error(sender, message);

		} catch (final Throwable t) {
			Messenger.error(sender, SimpleLocalization.Commands.ERROR.replace("{error}", t.toString()));

			throw t;

		} finally {
			finallyCallback.run();
		}
	}
}
