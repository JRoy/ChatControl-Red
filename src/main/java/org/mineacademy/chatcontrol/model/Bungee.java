package org.mineacademy.chatcontrol.model;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.PlayerCache;
import org.mineacademy.chatcontrol.SenderCache;
import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.model.Channel.Mode;
import org.mineacademy.chatcontrol.operator.Tag;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.chatcontrol.settings.Settings.Integration.BungeeCord;
import org.mineacademy.fo.BungeeUtil;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.PlayerUtil;
import org.mineacademy.fo.bungee.BungeeAction;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.model.HookManager;
import org.mineacademy.fo.remain.Remain;

import lombok.Getter;

/**
 * Responsible for handling BungeeCord information
 */
public final class Bungee {

	/**
	 * If BungeeCord is enabled, synchronize mails between servers
	 */
	public static void syncMail(Mail mail) {
		if (BungeeCord.ENABLED)
			Common.runLater(() -> BungeeUtil.tellBungee(BungeePacket.MAIL_SYNC, mail.serialize().toJson()));
	}

	/**
	 * Reschedule the permissions task giving/taking newcomer permissions
	 */
	public static void scheduleTask() {
		Common.runTimer(10, new SyncTask());
	}

	/**
	 * Represents uploading data to BungeeCords
	 */
	private static final class SyncTask implements Runnable {

		/**
		 * @see java.lang.Runnable#run()
		 */
		@Override
		public void run() {
			final SerializedMap allData = new SerializedMap();

			for (final Player online : Remain.getOnlinePlayers()) {
				final SenderCache senderCache = SenderCache.from(online);

				// Ignore senders whose data is being processed
				if (senderCache.isPendingJoin())
					continue;

				final SerializedMap data = new SerializedMap();
				final String nick = Settings.Tag.APPLY_ON.contains(Tag.Type.NICK) ? PlayerCache.from(online).getTag(Tag.Type.NICK) : null;
				final PlayerCache cache = PlayerCache.from(online);

				data.put("Server", Remain.getServerName());
				data.put("UUID", online.getUniqueId());
				data.putIf("Nick", nick);
				data.put("Vanished", PlayerUtil.isVanished(online));
				data.put("Afk", HookManager.isAfk(online));
				data.put("Ignoring_PMs", cache.isIgnoringPart(Toggle.PM));
				data.put("Ignored_Players", cache.getIgnoredPlayers());
				data.put("Channels", Common.convert(cache.getChannels(), new Common.MapToMapConverter<Channel, Channel.Mode, String, Channel.Mode>() {
					@Override
					public String convertKey(Channel key) {
						return key.getName();
					}

					@Override
					public Mode convertValue(Mode value) {
						return value;
					}
				}));

				allData.put(online.getName(), data.toJson());
			}

			if (BungeeCord.ENABLED)
				BungeeUtil.tellBungee(BungeePacket.PLAYERS_SYNC, allData);

			else
				SyncedCache.upload(allData);
		}
	}

	/**
	 * Proprietary implementation of BungeeAction for some of our
	 * premium plugins handled by BungeeControl
	 *
	 * The BungeeCord protocol always begins with
	 *
	 * 1) The UUID of the sender from which we send the packet, or null
	 * 2) The sender server name
	 * 3) The {@link BungeeAction}
	 *
	 * and the rest is the actual data within this enum
	 */
	public enum BungeePacket implements BungeeAction {

		/**
		 * Remove the given message from the players screen if he has received it.
		 */
		REMOVE_MESSAGE_BY_UUID(
				String.class /* remove mode */,
				UUID.class /*message id*/
		),

		/**
		 * Clears the game chat
		 */
		CLEAR_CHAT(
				String.class /*broacast message*/
		),

		/**
		 * Forward commands to BungeeCord or other server
		 */
		FORWARD_COMMAND(
				String.class /*server*/,
				String.class /*command*/
		),

		/**
		 * Update mute status
		 */
		MUTE(
				String.class /*type*/,
				String.class /*object such as channel name*/,
				String.class /*duration*/,
				String.class /*announce message*/
		),

		/**
		 * Send a sound to a player
		 */
		SOUND(
				String.class /*receiver UUID*/,
				String.class /*simple sound raw*/
		),

		// ----------------------------------------------------------------------------------------------------
		// Messages
		// ----------------------------------------------------------------------------------------------------

		/**
		 * Broadcast a message in a channel.
		 */
		CHANNEL(
				String.class /*channel*/,
				String.class /*sender name*/,
				UUID.class /*sender uid*/,
				String.class /*message*/,
				String.class /*simplecomponent json*/,
				String.class /*console format*/,
				Boolean.class /*mute bypass*/,
				Boolean.class /*ignore bypass*/,
				Boolean.class /*log bypass*/
		),

		/**
		 * Broadcast message to spying players
		 */
		SPY(
				String.class /*spy type*/,
				String.class /*channel name*/,
				String.class /*message*/,
				String.class /*simplecomponent json*/,
				String.class /*json string list of UUIDs of players we should ignore*/
		),

		/**
		 * Send a toast message
		 */
		TOAST(
				UUID.class /*receiver UUID*/,
				String.class /*toggle type*/,
				String.class /*message*/,
				String.class /*compmaterial*/
		),

		/**
		 * Send announcement message
		 */
		ANNOUNCEMENT(
				String.class /*type*/,
				String.class /*message*/,
				String.class /*json data*/
		),

		/**
		 * Broadcast the /me command
		 */
		ME(
				UUID.class /*sender uuid*/,
				Boolean.class /*has reach bypass perm*/,
				String.class /*simplecomponent json*/
		),

		/**
		 * Send motd to the given receiver
		 */
		MOTD(
				String.class /*receiver uuid*/
		),

		/**
		 * Rules notify handling
		 */
		NOTIFY(
				String.class /*permission*/,
				String.class /*simplecomponent json*/
		),

		/**
		 * Send a plain message to all fools
		 */
		PLAIN_BROADCAST(
				String.class /*message to broadcast*/
		),

		/**
		 * Send a plain message to the given receiver
		 */
		PLAIN_MESSAGE(
				UUID.class /*receiver*/,
				String.class /*message*/
		),

		/**
		 * Very simple component message to receiver
		 */
		SIMPLECOMPONENT_MESSAGE(
				UUID.class /*receiver*/,
				String.class /*message json*/
		),

		/**
		 * Broadcast this BaseComponent to all online players
		 */
		JSON_BROADCAST(
				String.class /*message json*/
		),

		// ----------------------------------------------------------------------------------------------------
		// List players
		// ----------------------------------------------------------------------------------------------------

		/**
		 * Request player list sorted by the given variable
		 * that is then replaced for each player such as {player_group}
		 */
		LIST_PLAYERS_REQUEST(
				String.class /*requesting player uuid*/,
				String.class /*server to list from*/,
				String.class /*variable to sort by*/
		),

		/**
		 * Return a player-variable map
		 */
		LIST_PLAYERS_RESPONSE(
				String.class /*requesting player uuid*/,
				String.class /*json map*/
		),

		// ----------------------------------------------------------------------------------------------------
		// Data gathering
		// ----------------------------------------------------------------------------------------------------

		/**
		 * Indicates MySQL has changed for player and we need pulling it again
		 */
		DB_UPDATE(
				String.class /*player name*/,
				String.class /*player UUID*/,
				String.class /*player nick*/,
				String.class /*data JSON*/,
				String.class /*message to player*/
		),

		/**
		 * Indicates that the player should have his reply player
		 * updated
		 */
		REPLY_UPDATE(
				UUID.class /* player to update uuid */,
				String.class /* reply player name */,
				UUID.class /* reply player uuid */
		),

		/**
		 * This will sync one mail to BungeeCord.
		 * Sent after MAIL_SYNC_START.
		 */
		MAIL_SYNC(
				String.class /*mail as json*/
		),

		/**
		 * Sync of data between servers using BungeeCord
		 */
		PLAYERS_SYNC(
				String.class /*map*/
		),

		;

		/**
		 * Stores all valid values, the names of them are only used
		 * in the error message when the length of data does not match
		 */
		@Getter
		private final Class<?>[] content;

		/**
		 * Constructs a new bungee action
		 *
		 * @param validValues
		 */
		BungeePacket(final Class<?>... validValues) {
			this.content = validValues;
		}
	}

}
