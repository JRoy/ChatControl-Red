package org.mineacademy.chatcontrol;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.mineacademy.chatcontrol.command.ChannelCommands;
import org.mineacademy.chatcontrol.command.ChatControlCommands;
import org.mineacademy.chatcontrol.command.CommandIgnore;
import org.mineacademy.chatcontrol.command.CommandList;
import org.mineacademy.chatcontrol.command.CommandMail;
import org.mineacademy.chatcontrol.command.CommandMe;
import org.mineacademy.chatcontrol.command.CommandMotd;
import org.mineacademy.chatcontrol.command.CommandMute;
import org.mineacademy.chatcontrol.command.CommandRealName;
import org.mineacademy.chatcontrol.command.CommandReply;
import org.mineacademy.chatcontrol.command.CommandSpy;
import org.mineacademy.chatcontrol.command.CommandTag;
import org.mineacademy.chatcontrol.command.CommandTell;
import org.mineacademy.chatcontrol.command.CommandToggle;
import org.mineacademy.chatcontrol.listener.BookListener;
import org.mineacademy.chatcontrol.listener.BungeeListener;
import org.mineacademy.chatcontrol.listener.ChatListener;
import org.mineacademy.chatcontrol.listener.CommandListener;
import org.mineacademy.chatcontrol.listener.PlayerListener;
import org.mineacademy.chatcontrol.listener.TabListener;
import org.mineacademy.chatcontrol.listener.ThirdPartiesListener;
import org.mineacademy.chatcontrol.model.Book;
import org.mineacademy.chatcontrol.model.Bungee;
import org.mineacademy.chatcontrol.model.Bungee.BungeePacket;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.chatcontrol.model.Discord;
import org.mineacademy.chatcontrol.model.Filter;
import org.mineacademy.chatcontrol.model.Format;
import org.mineacademy.chatcontrol.model.Log;
import org.mineacademy.chatcontrol.model.Newcomer;
import org.mineacademy.chatcontrol.model.Packets;
import org.mineacademy.chatcontrol.model.Placeholders;
import org.mineacademy.chatcontrol.model.UserMap;
import org.mineacademy.chatcontrol.model.WarningPoints;
import org.mineacademy.chatcontrol.operator.Groups;
import org.mineacademy.chatcontrol.operator.PlayerMessages;
import org.mineacademy.chatcontrol.operator.Rules;
import org.mineacademy.chatcontrol.operator.Tag;
import org.mineacademy.chatcontrol.settings.ClassicLocalization;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.chatcontrol.settings.Settings.Integration.BungeeCord;
import org.mineacademy.chatcontrol.settings.Settings.MySQL;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.FileUtil;
import org.mineacademy.fo.Messenger;
import org.mineacademy.fo.MinecraftVersion;
import org.mineacademy.fo.MinecraftVersion.V;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.bungee.SimpleBungee;
import org.mineacademy.fo.command.SimpleCommandGroup;
import org.mineacademy.fo.model.HookManager;
import org.mineacademy.fo.model.SpigotUpdater;
import org.mineacademy.fo.model.Variable;
import org.mineacademy.fo.model.Variables;
import org.mineacademy.fo.plugin.SimplePlugin;
import org.mineacademy.fo.remain.Remain;
import org.mineacademy.fo.settings.YamlStaticConfig;

import lombok.Getter;

/**
 * ChatControl is a simple chat management plugin.
 *
 * @since last major code audit September-November 2020
 */
public final class ChatControl extends SimplePlugin {

	/**
	 * The main command
	 */
	@Getter
	private final SimpleCommandGroup mainCommand = ChatControlCommands.getInstance();

	/**
	 * The BungeeCord hook
	 */
	@Getter
	private final SimpleBungee bungeeCord = new SimpleBungee("plugin:chcred", BungeeListener.getInstance(), BungeePacket.values());

	/**
	 * The settings classes
	 */
	@Getter
	private final List<Class<? extends YamlStaticConfig>> settings = Arrays.asList(Settings.class, ClassicLocalization.class);

	/**
	 * The text shown at startup
	 *
	 * @return
	 */
	@Override
	protected String[] getStartupLogo() {

		return new String[] {
				"&c ____ _  _ ____ ___ ____ ____ _  _ ___ ____ ____ _     ",
				"&4 |    |__| |__|  |  |    |  | |\\ |  |  |__/ |  | |    ",
				"&4 |___ |  | |  |  |  |___ |__| | \\|  |  |  \\ |__| |___",
				" ",
		};
	}

	/**
	 * Called automatically once the plugin starts before settings are loaded
	 */
	@Override
	protected void onPluginPreStart() {

		if (!Bukkit.getVersion().contains("Paper"))
			Common.logFramed(
					"WARNING: You are not using Paper!",
					"",
					"Third party forks such as Tuinity are known to alter",
					"our plugin's behavior. If you have any problems, test",
					"using Paper only otherwise you will get NO SUPPORT.");

		// Use the new way of sending messages
		Messenger.ENABLED = true;

		// Document all variables and use comments to save 'em
		Variable.PROTOTYPE_PATH = fileName -> {

			// Return different prototypes for different variable types since MESSAGE
			// variables do not support all keys
			final File file = FileUtil.getFile("variables/" + fileName + ".yml");
			final FileConfiguration config = file.exists() ? FileUtil.loadConfigurationStrict(file) : null;

			final String key = config.getString("Key");
			String prototype = "variable-format";

			if ((!config.isSet("Type") && ("{item}".equals(key) || "item".equals(key))) || "MESSAGE".equalsIgnoreCase(config.getString("Type", "FORMAT")))
				prototype = "variable-message";

			return "prototype/" + prototype + ".yml";
		};

		// Load internalization file before it is called in commands
		Lang.init();
	}

	/**
	 * Called automatically once when the plugin starts
	 */
	@Override
	protected void onPluginStart() {

		// Add console filters - no reload support
		Filter.inject();

		// Register third party early to prevent duplication on reload
		ThirdPartiesListener.registerEvents();
	}

	/**
	* Called automatically once when the plugin starts and each time is reloaded
	*/
	@Override
	protected void onReloadablesStart() {

		// Hide plugin name before console messages
		Common.ADD_LOG_PREFIX = false;

		Valid.checkBoolean(HookManager.isVaultLoaded(), "You need to install Vault so that we can work with packets, offline player data, prefixes and groups.");

		if (BungeeCord.ENABLED && !MySQL.ENABLED)
			Common.logFramed(true,
					"You need to enable MySQL when having BungeeCord",
					"because transfering player data using plugin",
					"messaging overflows the maximum length limit,",
					"so we use database instead.",
					"",
					"PLUGIN WILL NOT WORK UNTIL YOU ENABLE",
					"MYSQL DATABASE IN MYSQL.YML FILE");

		ServerCache.getInstance().load();
		UserMap.getInstance().load();
		Packets.getInstance().load();
		Lang.reloadFile();

		Log.purgeOldEntries();
		Book.copyDefaults();

		// Load parts of the plugin
		Variable.loadVariables();
		Channel.loadChannels();
		Format.loadFormats();

		// Load messenger prefixes
		Messenger.setAnnouncePrefix(Lang.of("Prefix.Announce"));
		Messenger.setErrorPrefix(Lang.of("Prefix.Error"));
		Messenger.setInfoPrefix(Lang.of("Prefix.Info"));
		Messenger.setQuestionPrefix(Lang.of("Prefix.Question"));
		Messenger.setSuccessPrefix(Lang.of("Prefix.Success"));
		Messenger.setWarnPrefix(Lang.of("Prefix.Warn"));

		// Register commands
		registerCommands(Settings.Channels.COMMAND_ALIASES, ChannelCommands.getInstance());

		if (!isEnabled || !isEnabled())
			return;

		if (Settings.Ignore.ENABLED)
			registerCommand(new CommandIgnore());

		if (Settings.Mail.ENABLED)
			registerCommand(new CommandMail());

		if (Settings.Me.ENABLED)
			registerCommand(new CommandMe());

		if (Settings.ListPlayers.ENABLED)
			registerCommand(new CommandList());

		if (Settings.Motd.ENABLED)
			registerCommand(new CommandMotd());

		if (Settings.Mute.ENABLED)
			registerCommand(new CommandMute());

		if (!Settings.Tag.APPLY_ON.isEmpty())
			registerCommand(new CommandTag());

		if (Settings.Tag.APPLY_ON.contains(Tag.Type.NICK))
			registerCommand(new CommandRealName());

		if (!Settings.Spy.APPLY_ON.isEmpty())
			registerCommand(new CommandSpy());

		if (!Settings.Toggle.APPLY_ON.isEmpty()) {
			final boolean deregister = !Common.doesPluginExist("PvPManager");

			new CommandToggle().register(deregister, deregister);
		}

		if (Settings.PrivateMessages.ENABLED) {
			registerCommand(new CommandReply());

			new CommandTell().register(!Common.doesPluginExist("Towny"));
		}

		// Register events
		registerEvents(ChatListener.getInstance());
		registerEvents(CommandListener.getInstance());
		registerEvents(PlayerListener.getInstance());

		if (Settings.TabComplete.ENABLED && MinecraftVersion.atLeast(V.v1_13))
			registerEvents(TabListener.getInstance());

		if (Remain.hasBookEvent())
			registerEvents(BookListener.getInstance());

		if (HookManager.isDiscordSRVLoaded())
			registerEvents(Discord.getInstance());

		// Load rule system
		Rules.getInstance().load();
		Groups.getInstance().load();
		PlayerMessages.getInstance().load();

		// Run tasks
		WarningPoints.scheduleTask();
		Newcomer.scheduleTask();
		Bungee.scheduleTask();

		// Copy sample image but only if folder doesn't exist so people can remove it
		if (!FileUtil.getFile("images").exists())
			FileUtil.extractRaw("images/creeper-head.png");

		// Register variables
		Variables.addExpansion(Placeholders.getInstance());

		if (Common.doesPluginExist("ViaVersion"))
			Common.log("&6Warning: Detected ViaVersion. If you're getting kicked out, set "
					+ "Integration.ProtocolLib.Listen_For_Packets and Tab_Complete.Enabled both to false in settings.yml.");

		if (!ServerCache.getInstance().isTourCompleted())
			Common.logFramed(
					" Welcome to ChatControl!",
					" ",
					" Before you start using this product, you need to",
					" agree to our Terms of Service and complete a quick",
					" tour with '/chc tour' command.",
					"",
					" We recommend you complete this process before",
					" making any changes to your configuration.");

		else if (Settings.SHOW_TIPS)
			Common.log(
					" ",
					"Tutorial:",
					"&chttps://github.com/kangarko/ChatControl-Red/wiki",
					" ",
					"Get help:",
					"&chttps://github.com/kangarko/ChatControl-Red/issues",
					isReloading() ? "" : "&8" + Common.consoleLineSmooth());

		// Finally, place plugin name before console messages after plugin has (re)loaded
		Common.runLater(() -> Common.ADD_LOG_PREFIX = true);

		//SenderCache.getCaches().clear();
		//PlayerCache.clear();
	}

	/**
	* Called automatically to enable checking for updates
	*
	* @return
	*/
	@Override
	public SpigotUpdater getUpdateCheck() {
		return new SpigotUpdater(85965);
	}

	/**
	 * @see org.mineacademy.fo.plugin.SimplePlugin#regexStripColors()
	 */
	@Override
	public boolean regexStripColors() {
		return Settings.Rules.STRIP_COLORS;
	}

	/**
	 * @see org.mineacademy.fo.plugin.SimplePlugin#regexStripAccents()
	 */
	@Override
	public boolean regexStripAccents() {
		return Settings.Rules.STRIP_ACCENTS;
	}

	/**
	 * @see org.mineacademy.fo.plugin.SimplePlugin#regexCaseInsensitive()
	 */
	@Override
	public boolean regexCaseInsensitive() {
		return Settings.Rules.CASE_INSENSITIVE;
	}

	/**
	 * @see org.mineacademy.fo.plugin.SimplePlugin#regexUnicode()
	 */
	@Override
	public boolean regexUnicode() {
		return Settings.Rules.CASE_INSENSITIVE;
	}

	/**
	 * The inception year -- whoa long time ago!
	 *
	 * @param
	 */
	@Override
	public int getFoundedYear() {
		return 2013;
	}
}
