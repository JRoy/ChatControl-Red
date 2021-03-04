package org.mineacademy.chatcontrol.model;

import org.mineacademy.fo.command.annotation.Permission;
import org.mineacademy.fo.command.annotation.PermissionGroup;
import org.mineacademy.fo.constants.FoPermissions;

/**
 * Holds all permissions we can use in the plugin that
 * are also dynamically loaded in the /chc perms command.
 *
 * IF YOU ARE reading this class and have no coding knowledge, you need
 * to give permissions after the public static final String XX = ""
 * such as chatcontrol.command.book is the right permission etc.
 */
public final class Permissions extends FoPermissions {

	@PermissionGroup("Execute main plugin command.")
	public static final class Command {

		@Permission("Announce important messages to everyone. Replace 'type' with: chat, title, actionbar, bossbar, toast.")
		public static final String ANNOUNCE = "chatcontrol.command.announce.{type}";

		@Permission("Read or save books you can use in rules etc.")
		public static final String BOOK = "chatcontrol.command.book";

		@Permission("Forward commands to BungeeCord.")
		public static final String BUNGEE = "chatcontrol.command.bungee";

		@Permission("Answer or regenerate captcha.")
		public static final String CAPTCHA = "chatcontrol.command.captcha";

		@Permission("Clear the game chat.")
		public static final String CLEAR = "chatcontrol.command.clear";

		@Permission("Clear the console.")
		public static final String CLEAR_CONSOLE = "chatcontrol.command.clear.console";

		@Permission("Change your chat color/decoration.")
		public static final String COLOR = "chatcontrol.command.color";

		@Permission("Change another player's color/decoration.")
		public static final String COLOR_OTHERS = "chatcontrol.command.color.others";

		@Permission("Compress settings for GitHub issues.")
		public static final String DEBUG = "chatcontrol.command.debug";

		@Permission("Send commands to BungeeCord or another server.")
		public static final String FORWARD = "chatcontrol.command.forward";

		@Permission("Toggle seeing messages/PMs from players.")
		public static final String IGNORE = "chatcontrol.command.ignore";

		@Permission("List who is ignoring messages/PMs.")
		public static final String IGNORE_LIST = "chatcontrol.command.ignore.list";

		@Permission("Toggle seeing messages/PMs for others.")
		public static final String IGNORE_OTHERS = "chatcontrol.command.ignore.others";

		@Permission("Print various debug information.")
		public static final String INFO = "chatcontrol.command.info";

		@Permission("Inspect classes, fields and methods in Java.")
		public static final String INSPECT = "chatcontrol.command.inspect";

		@Permission("Browse players on your server or BungeeCord.")
		public static final String LIST = "chatcontrol.command.list";

		@Permission("View last player communication.")
		public static final String LOG = "chatcontrol.command.log";

		@Permission("Manage your game mail.")
		public static final String MAIL = "chatcontrol.command.mail";

		@Permission("Send a formatted message.")
		public static final String ME = "chatcontrol.command.me";

		@Permission("Manage player messages.")
		public static final String MESSAGE = "chatcontrol.command.message";

		@Permission("Migrate data between MySQL and data.db.")
		public static final String MIGRATE = "chatcontrol.command.migrate";

		@Permission("Read the message of the day.")
		public static final String MOTD = "chatcontrol.command.motd";

		@Permission("Send the message of the day to other players.")
		public static final String MOTD_OTHERS = "chatcontrol.command.motd.others";

		@Permission("Mute the game chat.")
		public static final String MUTE = "chatcontrol.command.mute";

		@Permission("List all plugin permissions.")
		public static final String PERMISSIONS = "chatcontrol.command.permissions";

		@Permission("Manage player warning points.")
		public static final String POINTS = "chatcontrol.command.points";

		@Permission("Remove past player's messages.")
		public static final String PURGE = "chatcontrol.command.purge";

		@Permission("Look up player's real name and nick.")
		public static final String REAL_NAME = "chatcontrol.command.realname";

		@Permission("Manage map regions used in rules.")
		public static final String REGION = "chatcontrol.command.region";

		@Permission("Reload plugin configuration.")
		public static final String RELOAD = "chatcontrol.command.reload";

		@Permission("Reply to last player who messaged you.")
		public static final String REPLY = "chatcontrol.command.reply";

		@Permission("Manage the rules system.")
		public static final String RULE = "chatcontrol.command.rule";

		@Permission("Execute JavaScript scripts.")
		public static final String SCRIPT = "chatcontrol.command.script";

		@Permission("Toggle spying player commands and messages.")
		public static final String SPY = "chatcontrol.command.spy";

		@Permission("Set yourself a tag such as prefix, suffix or nick. Type is either prefix, suffix or nick.")
		public static final String TAG = "chatcontrol.command.tag.{type}";

		@Permission("Control tags for players.")
		public static final String TAG_ADMIN = "chatcontrol.command.tag.admin";

		@Permission("Send private messages to players.")
		public static final String TELL = "chatcontrol.command.tell";

		@Permission("Discover what ChatControl is and how it can help your server.")
		public static final String TOUR = "chatcontrol.command.tour";

		@Permission("Toggle seeing parts of the plugin. Replace type with: mail, announcement, me, pm, death, join, kick, quit, list, me, pm, quit ")
		public static final String TOGGLE = "chatcontrol.command.toggle.{type}";

		@Permission("Reload player's tab list name.")
		public static final String UPDATE = "chatcontrol.command.update";
	}

	@PermissionGroup("Use certain colors in chat via & or via command.")
	public static final class Color {

		@Permission("Enable to use & color codes for colors and decoration.")
		public static final String LETTER = "chatcontrol.color.{color}";

		@Permission("Enable to use hex colors or decoration, also via /{label} color. Replace color with the code without #.")
		public static final String HEX = "chatcontrol.hexcolor.{color}";

		@Permission("Enable to use certain colors or decoration via /{label} color.")
		public static final String GUI = "chatcontrol.guicolor.{color}";

		@Permission(value = "Allow players use & and hex colors. Replace apply_on with Colors.Apply_On sections from settings.yml (by default players can use colors everywhere)", def = true)
		public static final String USE = "chatcontrol.use.color.{apply_on}";
	}

	@PermissionGroup("Prevent applying certain parts of the plugin.")
	public static final class Bypass {

		@Permission("Bypass the anticaps filter.")
		public static final String CAPS = "chatcontrol.bypass.caps";

		@Permission("Prevents your screen from getting wiped when chat is cleared.")
		public static final String CLEAR = "chatcontrol.bypass.clear";

		@Permission("Bypass time limit for messages and commands.")
		public static final String DELAY = "chatcontrol.bypass.delay";

		@Permission("Do not apply capitalize first/insert dot grammar adjustments.")
		public static final String GRAMMAR = "chatcontrol.bypass.grammar";

		@Permission("Prevent your messages and commands from being logged.")
		public static final String LOG = "chatcontrol.bypass.log";

		@Permission("Prevent antibot login delay from applying.")
		public static final String LOGIN_DELAY = "chatcontrol.bypass.logindelay";

		@Permission("Allow player joining if he has a disallowed nickname.")
		public static final String LOGIN_USERNAMES = "chatcontrol.bypass.loginusernames";

		@Permission("Prevent antibot chat/command until move check.")
		public static final String MOVE = "chatcontrol.bypass.move";

		@Permission("Except player from different rules if he is newcomer.")
		public static final String NEWCOMER = "chatcontrol.bypass.newcomer";

		@Permission("Bypass the vanilla antispam kick when typing rapidly.")
		public static final String SPAM_KICK = "chatcontrol.bypass.spamkick";

		@Permission("Prevent player actions from being spied upon.")
		public static final String SPY = "chatcontrol.bypass.spy";

		@Permission("Prevent antibot sign duplication check.")
		public static final String SIGN_DUPLICATION = "chatcontrol.bypass.signduplication";

		@Permission("Bypass chat or channel mute.")
		public static final String MUTE = "chatcontrol.bypass.mute";

		@Permission("Bypass period antispam check.")
		public static final String PERIOD = "chatcontrol.bypass.period";

		@Permission("Bypass channel range and reach everyone on all worlds. False even to OPs by default.")
		public static final String RANGE = "chatcontrol.bypass.range";

		@Permission("Bypass channel range and reach everyone on the same world only. False even to OPs by default.")
		public static final String RANGE_WORLD = "chatcontrol.bypass.range.world";

		@Permission("Send messages and private messages to players who ignore you, or have PMs disabled.")
		public static final String REACH = "chatcontrol.bypass.reach";

		@Permission("Bypass similarity antispam check.")
		public static final String SIMILARITY = "chatcontrol.bypass.similarity";

		@Permission("Bypass tab-complete filtering.")
		public static final String TAB_COMPLETE = "chatcontrol.bypass.tabcomplete";

		@Permission("Do not receive warning points and bypass their actions.")
		public static final String WARNING_POINTS = "chatcontrol.bypass.warnpoints";
	}

	@PermissionGroup("Permissions for chat channels.")
	public static final class Channel {

		@Permission(value = "Automatically join the given channel to the given mode on join")
		public static final String AUTO_JOIN = "chatcontrol.channel.autojoin.{channel}.{mode}";

		@Permission(value = "Join channel in mode with '/{label_channel} join'")
		public static final String JOIN = "chatcontrol.channel.join.{channel}.{mode}";

		@Permission(value = "Join others to channels with '/{label_channel} join'")
		public static final String JOIN_OTHERS = "chatcontrol.channel.join.others";

		@Permission(value = "Leave channel with '/{label_channel} leave'")
		public static final String LEAVE = "chatcontrol.channel.leave.{channel}";

		@Permission(value = "Leave others from channels with '/{label_channel} leave'")
		public static final String LEAVE_OTHERS = "chatcontrol.channel.leave.others";

		@Permission(value = "Mute or kick players from channels in '/{label_channel} list'")
		public static final String LIST_OPTIONS = "chatcontrol.channel.list.options";

		@Permission(value = "Send messages to channel with '/{label_channel} send'")
		public static final String SEND = "chatcontrol.channel.send.{channel}";
	}

	@PermissionGroup("Control messages the player can receive.")
	public static final class Receive {

		@Permission(value = "See messages from /{label} announce.", def = true)
		public static final String ANNOUNCER = "chatcontrol.receive.announcer";
	}

	@PermissionGroup("Spying related permissions.")
	public static final class Spy {

		@Permission("Automatically start spying everything on join.")
		public static final String AUTO_ENABLE = "chatcontrol.spy.autoenable";
	}

	@PermissionGroup("Permissions related to game chat.")
	public static final class Chat {

		@Permission(value = "See game chat messages.", def = true)
		public static final String READ = "chatcontrol.chat.read";

		@Permission(value = "Write messages to game chat.", def = true)
		public static final String WRITE = "chatcontrol.chat.write";
	}

	@Permission("Automatically assign a certain group to player.")
	public static final String GROUP = "chatcontrol.group.{group}";

	@Permission(value = "Use the sound notify feature. True by default.", def = true)
	public static final String SOUND_NOTIFY = "chatcontrol.soundnotify";

}
