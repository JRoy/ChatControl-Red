package org.mineacademy.chatcontrol.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.Effect;
import org.mineacademy.chatcontrol.ServerCache;
import org.mineacademy.chatcontrol.command.ChatControlCommands.ChatControlSubCommand;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.model.ChatPaginator;
import org.mineacademy.fo.model.HookManager;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.plugin.SimplePlugin;
import org.mineacademy.fo.remain.CompMaterial;
import org.mineacademy.fo.remain.CompSound;
import org.mineacademy.fo.settings.SimpleLocalization;

/**
 * Provides a guide through the plugin setup and use for both
 * beginners and professionals.
 */
public final class ChatControlTour extends ChatControlSubCommand {

	public ChatControlTour() {
		super("tour");

		setUsage("");
		setDescription("Get started with ChatControl.");
		setPermission(Permissions.Command.TOUR);
	}

	/*
	 * Stops any active music
	 */
	private void stopMusic() {

		try {
			if (isPlayer())
				getPlayer().playEffect(getPlayer().getLocation(), Effect.RECORD_PLAY, 0);
		} catch (final Throwable t) {
			// MC incompatible
		}
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void execute() {

		final boolean tourCompleted = ServerCache.getInstance().isTourCompleted();
		final String param = args.length == 0 ? "" : args[0].toLowerCase();

		//
		// Main command
		//
		if (args.length == 0) {
			tellNoPrefix("&8" + Common.chatLineSmooth(),
					"&c<center>Hi {player}, welcome to ChatControl " + SimplePlugin.getVersion(),
					" ");

			if (tourCompleted) {

				tellNoPrefix(
						" Please use the /{label} {sublabel} list to see all commands.");

			} else {
				tellNoPrefix(" &71. &fIf you are &onew &fto ChatControl or doing a fresh");

				SimpleComponent
						.of("    installation, type ").append(replacePlaceholders("&c/{label} {sublabel} start"))
						.onHover("&7Click to learn about ChatControl.")
						.onClickRunCmd("/" + getLabel() + " " + getSublabel() + " start")
						.send(sender);

				tellNoPrefix(
						" ",
						" &72. &fIf you are &omigrating &ffrom ChatControl Free or");

				SimpleComponent
						.of("    ChatControl Pro 8, type ").append(replacePlaceholders("&c/{label} {sublabel} migrate"))
						.onHover("&7Click to learn about migrating.")
						.onClickRunCmd("/" + getLabel() + " " + getSublabel() + " migrate")
						.send(sender);

				tellNoPrefix(
						" ");
			}

			// Always disable music
			stopMusic();

			if (isPlayer())
				CompSound.CAT_MEOW.play(getPlayer());
		}

		else if ("list".equals(param)) {

			tellNoPrefix("&8" + Common.chatLineSmooth(),
					"&c<center>ChatControl Tour",
					" ");

			SimpleComponent
					.of(" - &7/" + getLabel() + " " + getSublabel() + " start &f- Getting started with ChatControl.")
					.onHover("&7Click to run this command.")
					.onClickRunCmd("/" + getLabel() + " " + getSublabel() + " start")
					.send(sender);

			SimpleComponent
					.of(" - &7/" + getLabel() + " " + getSublabel() + " migrate &f- Migrating from ChatControl free or ChatControl 8.")
					.onHover("&7Click to run this command.")
					.onClickRunCmd("/" + getLabel() + " " + getSublabel() + " migrate")
					.send(sender);

			SimpleComponent
					.of(" - &7/" + getLabel() + " " + getSublabel() + " changelog &f- What's new in ChatControl.")
					.onHover("&7Click to run this command.")
					.onClickRunCmd("/" + getLabel() + " " + getSublabel() + " changelog")
					.send(sender);

			SimpleComponent
					.of(" - &7/" + getLabel() + " " + getSublabel() + " hooks &f- What other plugins we support.")
					.onHover("&7Click to run this command.")
					.onClickRunCmd("/" + getLabel() + " " + getSublabel() + " hooks")
					.send(sender);

			SimpleComponent
					.of(" - &7/" + getLabel() + " " + getSublabel() + " docs &f- Where to find our documentation.")
					.onHover("&7Click to run this command.")
					.onClickRunCmd("/" + getLabel() + " " + getSublabel() + " docs")
					.send(sender);

			SimpleComponent
					.of(" - &7/" + getLabel() + " " + getSublabel() + " support &f- Where to get support.")
					.onHover("&7Click to run this command.")
					.onClickRunCmd("/" + getLabel() + " " + getSublabel() + " support")
					.send(sender);

			SimpleComponent
					.of(" - &7/" + getLabel() + " " + getSublabel() + " list &f- Print all tour commands.")
					.onHover("&7Click to run this command.")
					.onClickRunCmd("/" + getLabel() + " " + getSublabel() + " list")
					.send(sender);

			SimpleComponent
					.of(" - &7/" + Settings.MAIN_COMMAND_ALIASES.get(0) + " perms &f- Prints all permissions the plugin supports and those you have.")
					.onHover("&7Click to run this command.")
					.onClickRunCmd("/" + Settings.MAIN_COMMAND_ALIASES.get(0) + " perms")
					.send(sender);

			SimpleComponent
					.of(" - &7/" + Settings.MAIN_COMMAND_ALIASES.get(0) + " ? &f- Prints all main plugin commands.")
					.onHover("&7Click to run this command.")
					.onClickRunCmd("/" + Settings.MAIN_COMMAND_ALIASES.get(0) + " ?")
					.send(sender);

			SimpleComponent
					.of(" - &7/" + Settings.Channels.COMMAND_ALIASES.get(0) + " ? &f- Prints all channel commands.")
					.onHover("&7Click to run this command.")
					.onClickRunCmd("/" + Settings.Channels.COMMAND_ALIASES.get(0) + " ?")
					.send(sender);

			stopMusic();
		}

		else if ("start".equals(param)) {

			try {
				if (isPlayer())
					getPlayer().playEffect(getPlayer().getLocation(), Effect.RECORD_PLAY, CompMaterial.MUSIC_DISC_MALL.getMaterial());
			} catch (final Throwable t) {
				// MC incompatible
			}

			final List<String> pages = Arrays.asList(

					"&c> &7What?",
					" ChatControl helps you make your chat beautiful, organized",
					" and clean. It filters swears, ads, and prevents spam.",
					" It creates chat rooms and connects your BungeeCord,",
					" or even your Discord server.",
					" ",
					"&c> &7How?",
					" We use 'channels' to format your chat, 'rules' to filter",
					" it against inappropriate things and 'messages' to announce",
					" players at different occassions, such as join or death.",
					" ",
					"&c> &7Why?",
					" We believe that, a happy community is the key to any great",
					" server. Our mission is to give people tools to build such",
					" community by managing its #1 meeting point, the game chat.",
					"&c> &7Here's what you find inside ChatControl/ folder:",
					" ",
					" - &7books/ folder:&f You can open books for yourself or",
					"  players, and such books are in this folder.",
					" ",
					" - &7formats/ folder: &fYour chat can have different outlook",
					"  for different players or occasions. Each chat 'style' is",
					"  called a format and has its own file. Each format contains",
					"  parts, each of which can be shown or hidden depending",
					"  on what permission the sender or receiver has, and more.",
					" ",
					" - &7images/ folder: &fPlace jpg or png images there to show",
					"  them in places such as '/chc announce'.",
					" ",
					" ",
					" - &7localization/ folder: &fThe files inside empower you to",
					"  change or hide almost all messages we use. See the 'Locale'",
					"  key in settings.yml for a list of languages we support, or",
					"  create your own one by editing messages_en.yml directly!",
					" ",
					" - &7messages/ folder: &fCreate messages for when your players",
					"  join, quit, get kicked or die. Or create automated broadcasts",
					"  in the 'timed.rs' file. We recommend Notepad++ or Sublime Text",
					"  to edit these files. You'll find tons of examples there! To",
					"  make them work, remove the # letter before them. You can",
					"  edit those files completely and remove everything. See:",
					"  &ohttps://github.com/kangarko/ChatControl-Red/wiki/Messages",
					" ",
					" - &7rules/ folder: &fFilter any message, command, or even",
					"  messages from other plugins. Create new commands or bots",
					"  with our powerful rules. See this link for documentation:",
					"  &ohttps://github.com/kangarko/ChatControl-Red/wiki/Rules",
					" ",
					" - &7variables/ folder: &fCreate placeholders you can use",
					"  in your formats such as {player_rank} or even by players",
					"  in the chat such as 'I hold and [item]' using JavaScript.",
					" ",
					" - &7data.db file: &fWe store player data here, such as",
					"  what channels players are in, and more. Do not edit.",
					" ",
					" - &7error.log file: &fNot created unless there's a problem",
					"  with our plugin. Send this to us and we'll fix it!",
					" ",
					" - &7log.csv file: &fStores everything that happened in",
					"  game, you can view this using '/" + Settings.MAIN_COMMAND_ALIASES.get(0) + " log' command.",
					" - &7mysql.yml file: &fMySQL connection settings",
					" ",
					" - &7settings.yml file: &fOur main configuration file. Do not",
					"  edit! Just kidding, you can edit it as you like :) However,",
					"  any comments will reset back to defaults on restart.",
					" ",
					" - &7usermap.csv file: &fUsed to stored player names, unique IDs",
					"  and nicknames. Internal file, do not edit.",
					" ",
					"&c> &7Where to start?",
					" If you're starting out, begin by opening settings.yml. Things",
					" are documented - you'll see what each function does from",
					" the # comments above it. Then you can view other folders",
					" and start editing them with the help of our Wiki on GitHub.",
					"&c> &7How to change my chat?",
					" You can enable Channels in settings.yml right at the top,",
					" and edit how your chat in formats/. Read commands and our",
					" Wiki on GitHub, then experiment with your changes.",
					"",
					"&c> &7How to enable BungeeCord?",
					" Ensure you have MySQL databases ready. We need this to",
					" send data over your network. Enable MySQL in mysql.yml",
					" and then enable BungeeCord in Integration section",
					" of settings.yml. Finally, install BungeeControl from",
					" the ZIP file it should have bundled with ChatControl",
					" ",
					"&c> &7How to connect channels with Discord?",
					" Install DiscordSRV and follow our quick tutorial on",
					" https://github.com/kangarko/ChatControl-Red/wiki/Discord",
					" You can find more information about our plugin on our",
					" GitHub Wiki (see above), and ask us anything by opening",
					" a ticket on the Issues page (please read Wiki first):",
					" &ohttps://github.com/kangarko/ChatControl-Red/issues",
					"",
					" Thank you for reading. To get started using ChatControl",
					" type '/" + getLabel() + " " + getSublabel() + " confirm'.");

			new ChatPaginator(SimpleLocalization.Commands.HEADER_SECONDARY_COLOR)
					.setFoundationHeader("Welcome to ChatControl Red")
					.setPages(Common.toArray(pages))
					.send(sender);
		}

		else if ("migrate".equals(param)) {

			try {
				if (isPlayer())
					getPlayer().playEffect(getPlayer().getLocation(), Effect.RECORD_PLAY, CompMaterial.MUSIC_DISC_FAR.getMaterial());
			} catch (final Throwable t) {
				// MC incompatible
			}

			final List<String> pages = Arrays.asList(

					"&c> &7ChatControl Pro (8.x.x)",
					" We offer partial automatic migration from ChatControl 8,",
					" below is what files and folders are migrated for you.",
					" &7&nPLEASE TAKE TIME TO LEARN THIS, IT'S VERY IMPORTANT.",
					" ",
					" - &7localization/ folder: &cnot migrated, &fbecause a lot",
					"   of messages have changed and serve different purpose.",
					" ",
					" - &7logs/ folder: &cnot migrated, &fwe now use a single log.csv",
					"   file or MySQL database to store all logs. You can view new",
					"   logs with /" + Settings.MAIN_COMMAND_ALIASES.get(0) + " log command.",
					" ",
					" - &7rules/ folder: &2migrated automatically&f but",
					"   there are exceptions, see this link for what's new:",
					"   &ohttps://github.com/kangarko/ChatControl-Pro/wiki/Rules",
					" ",
					" - &7variables/ folder: &2migrated automatically&f but",
					"  there are features now working differently, see:",
					"   &ohttps://github.com/kangarko/ChatControl-Pro/wiki/Variables",
					" ",
					" - &7channels.yml file: &2migrated automatically",
					" ",
					" - &7data.db file: &cnot migrated, &fplease do not attempt to",
					"  just place the file over as it will corrupt your data",
					" ",
					" - &7formatting.yml file: &2migrated automatically&f, but",
					"  we recommend learning how it works now at this link:",
					"   &ohttps://github.com/kangarko/ChatControl-Pro/wiki/Formats",
					" ",
					" - &7handlers.yml file: &2migrated automatically &fto our new",
					"  rule group system in rules/groups.rs",
					" ",
					" - &7messages.yml file: &2migrated automatically, &fbut we",
					"  use a totally new system, studying this is recomended:",
					"  &ohttps://github.com/kangarko/ChatControl-Pro/wiki/Messages",
					" ",
					" - &7settings.yml file: &2migrated automatically, &fbut there",
					"   are exceptions and some settings have changed.",
					" ",
					" - &7MySQL database: &cnot migrated, &fformat largely changed,",
					"   you can remove your ChatControl_Data. We now have 3",
					"   tables: ChatControl, ChatControl_Mail and ChatControl_Log.",
					" ",
					" ",
					" ",
					" &c> &7Removed chat formatting, admin and bungee chat",
					" We had 4 systems to format chat which were half-broken and",
					" slow, so we removed it and now only have channels. You can",
					" literally get the exact same setup as you had before if you",
					" remove all channels except 'standard', create admin and",
					" bungee channels and give people this permission:",
					" &o" + Permissions.Channel.AUTO_JOIN + " &fto",
					" automatically join channel on login. If they however, leave it",
					" manually, we remember their choice and won't join them next",
					" time. To stop this, take away their permission to leave such",
					" channel: &o" + Permissions.Channel.LEAVE,
					" ",
					" &c> &7To view what's new in ChatControl Red, please see:",
					" https://github.com/kangarko/ChatControl-Red/wiki/Changelog",
					" ",
					"  &c&lTO MIGRATE, KEEP YOUR OLD CHATCONTROL FOLDER, FINISH",
					"  &c&lTHE TOUR AND TYPE /" + getLabel() + " migrate chc8",
					"  &cTake backups. Downgrading is unsupported at the moment.",
					" ",
					"&c> &7ChatControl Free (5.x.x)",
					" If you're using ChatControl 5 (the Free edition from 2013),",
					" you will need to manually migrate. Unfortunately, due to the",
					" amount of changes, we are not supporting automatic",
					" migration process. Type &c/" + getLabel() + " " + getSublabel() + " start&f to learn",
					" more about the plugin and how to get started quickly.",
					" ",
					" ",
					" Thank you for reading. To get started using ChatControl",
					" type '/" + getLabel() + " " + getSublabel() + " confirm'.");

			new ChatPaginator(SimpleLocalization.Commands.HEADER_SECONDARY_COLOR)
					.setFoundationHeader("Migrating to ChatControl Red")
					.setPages(Common.toArray(pages))
					.send(sender);
		}

		else if ("changelog".equals(param)) {
			tellNoPrefix(
					"&8" + Common.chatLineSmooth(),
					"&c<center>What's New In ChatControl Red?",
					" ",
					" To view what's new in ChatControl Red, please see:",
					" ",
					" &ohttps://github.com/kangarko/ChatControl-Red/wiki/Changelog",
					" ");
		}

		else if ("hooks".equals(param)) {

			class $ {

				SimpleComponent out(boolean has, String name) {
					return SimpleComponent.of(" - &7" + name + ": " + (has ? "&2Found" : "&cNot found") + "&7.");
				}
			}

			final $ $ = new $();

			final List<SimpleComponent> pages = new ArrayList<>();

			pages.add(SimpleComponent.of(" Below you will find all plugins we support, as well as those that we found on your server and hooked into."));
			pages.add(SimpleComponent.empty());

			pages.add($.out(HookManager.isAuthMeLoaded(), "AuthMe"));
			pages.add($.out(HookManager.isBanManagerLoaded(), "BanManager"));
			pages.add($.out(HookManager.isBossLoaded(), "Boss"));
			pages.add($.out(HookManager.isCMILoaded(), "CMI"));
			pages.add($.out(HookManager.isDiscordSRVLoaded(), "DiscordSRV"));
			pages.add($.out(HookManager.isVaultLoaded(), "Vault"));
			pages.add($.out(HookManager.isEssentialsLoaded(), "Essentials"));
			pages.add($.out(HookManager.isFactionsLoaded(), "Factions, FactionsX or FactionsUUID"));
			pages.add($.out(HookManager.isLandsLoaded(), "Lands"));
			pages.add($.out(HookManager.isMcMMOLoaded(), "mcMMO"));
			pages.add($.out(HookManager.isMythicMobsLoaded(), "MythicMobs"));
			pages.add($.out(HookManager.isMythicMobsLoaded(), "Multiverse-Core"));
			pages.add($.out(HookManager.isPlaceholderAPILoaded(), "PlaceholderAPI"));
			pages.add($.out(HookManager.isPlotSquaredLoaded(), "PlotSquared"));
			pages.add($.out(HookManager.isProtocolLibLoaded(), "ProtocolLib"));
			pages.add($.out(HookManager.isTownyLoaded(), "Towny"));
			pages.add($.out(HookManager.isTownyChatLoaded(), "TownyChat"));

			pages.add(SimpleComponent.empty());
			pages.add(SimpleComponent.of(" For more information, please visit:"));
			pages.add(SimpleComponent.of(" &ohttps://github.com/kangarko/ChatControl-Red/wiki/Hooks"));

			new ChatPaginator(SimpleLocalization.Commands.HEADER_SECONDARY_COLOR)
					.setFoundationHeader("Supported Plugins")
					.setPages(pages)
					.send(sender);
		}

		else if ("docs".equals(param)) {
			tellNoPrefix(
					"&8" + Common.chatLineSmooth(),
					"&c<center>Learn About How ChatControl Works",
					" ",
					" For quick help, please see the comments in",
					" almost every file. For extended tutorials and",
					" solving frequent issues/questions, please see:",
					" ",
					" &ohttps://github.com/kangarko/ChatControl-Red/wiki",
					" ");
		}

		else if ("support".equals(param)) {
			tellNoPrefix(
					"&8" + Common.chatLineSmooth(),
					"&c<center>Where To Get Support?",
					" ",
					" We're a tiny team and give support in our spare time, all",
					" we ask you do is read the docs first. Not only you save days",
					" waiting for your reply but also free up our time into",
					" development. See &c/{label} {sublabel} docs &ffor more.",
					" ",
					" Having said that, if you have questions, a bug to report",
					" or anything else needing to be addressed, open an issue at:",
					" &ohttps://github.com/kangarko/ChatControl-Red/wiki/Issues",
					" ",
					" &7Please note for the time being we're not having the capacity",
					" &7to add more features. We're happy to listen, though.");
		}

		else if ("confirm".equals(param)) {

			// Always disable music
			stopMusic();

			checkBoolean(!tourCompleted, "You already agreed to our Terms of Service. Check out our plugin tour with &7/{label} {sublabel} list&c.");

			tellNoPrefix(
					"&8" + Common.chatLineSmooth(),
					"&c<center>Thank you for using ChatControl.",
					" ",
					" &c[!] &fBy continuing, you indicate that you have fully read",
					" and understood our &cTerms of Service &fand have completed",
					" our tour process. Otherwise you will get no support.",
					" ",
					" &chttps://github.com/kangarko/ChatControl-Red/wiki/Terms",
					" ",
					" &7You will find the unlock command by reading our Terms.");

		} else if ("mothman".equals(param)) {

			// Always disable music
			stopMusic();

			checkBoolean(!tourCompleted, "You already agreed to our Terms of Service. Check out our plugin tour with &7/{label} {sublabel} list&c.");

			tellNoPrefix(
					"&8" + Common.chatLineSmooth(),
					"<center>&2Thank you for agreeing to our Terms of Service.",
					" ",
					" You may now use ChatControl. Happy using! Don't forget",
					" to explore the rest of this tour by typing &7/{label} {sublabel} list",
					" Also check out our Wiki on GitHub if you need help.",
					" ",
					" If you're doing a network install, you can run",
					" /{label} {sublabel} mothman from the console for all servers",
					" right away, saving your time.",
					" ",
					" &7If you need support, we're happy to help at this link:",
					" &7https://github.com/kangarko/ChatControl-Red/issues",
					" ");

			if (isPlayer())
				CompSound.SUCCESSFUL_HIT.play(getPlayer());

			ServerCache.getInstance().setTourCompleted(true);
		}

		else
			returnInvalidArgs();
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {

		if (args.length == 1)
			return ServerCache.getInstance().isTourCompleted()
					? completeLastWord("list", "start", "migrate", "changelog", "hooks", "docs", "support")
					: NO_COMPLETE;

		return NO_COMPLETE;
	}
}
