package org.mineacademy.chatcontrol.listener;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.PlayerCache;
import org.mineacademy.chatcontrol.SenderCache;
import org.mineacademy.chatcontrol.ServerCache;
import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.model.Announce;
import org.mineacademy.chatcontrol.model.Announce.AnnounceType;
import org.mineacademy.chatcontrol.model.Bungee.BungeePacket;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.chatcontrol.model.ListPlayers;
import org.mineacademy.chatcontrol.model.Mail;
import org.mineacademy.chatcontrol.model.Packets;
import org.mineacademy.chatcontrol.model.Players;
import org.mineacademy.chatcontrol.model.Spy;
import org.mineacademy.chatcontrol.model.Toggle;
import org.mineacademy.chatcontrol.model.UserMap;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.chatcontrol.settings.Settings.MySQL;
import org.mineacademy.fo.BungeeUtil;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.Messenger;
import org.mineacademy.fo.ReflectionUtil;
import org.mineacademy.fo.bungee.message.IncomingMessage;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.constants.FoConstants;
import org.mineacademy.fo.debug.Debugger;
import org.mineacademy.fo.exception.FoException;
import org.mineacademy.fo.model.HookManager;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.SimpleSound;
import org.mineacademy.fo.model.SimpleTime;
import org.mineacademy.fo.remain.CompMaterial;
import org.mineacademy.fo.remain.Remain;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.md_5.bungee.api.chat.BaseComponent;

/**
 * Represents the connection to BungeeCord
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BungeeListener extends org.mineacademy.fo.bungee.BungeeListener {

	/**
	 * The singleton of this class
	 */
	@Getter
	private final static BungeeListener instance = new BungeeListener();

	/**
	 * Due to problematic RedisBungee implementation, some of our messages may
	 * get duplicated. We simply store them here and ignore same messages sent to players
	 * right after one another.
	 *
	 * Message : Timestamp
	 */
	private final Map<String, Long> redisDeduplicator = new HashMap<>();

	/*
	 * The presently read packet
	 */
	private BungeePacket packet;

	/*
	 * The server the packet is coming from
	 */
	private String server;

	/*
	 * The sender of the packet
	 */
	private UUID senderUid;

	/**
	 * @see org.mineacademy.fo.bungee.BungeeListener#onMessageReceived(org.bukkit.entity.Player, org.mineacademy.fo.bungee.message.IncomingMessage)
	 */
	@Override
	public void onMessageReceived(Player player, IncomingMessage input) {

		this.packet = input.getAction();
		this.server = input.getServerName();
		this.senderUid = input.getSenderUid();

		if (!Settings.Integration.BungeeCord.ENABLED)
			return;

		if (this.packet != BungeePacket.PLAYERS_SYNC)
			Debugger.debug("bungee", "Received bungee packet " + this.packet + " from server " + server);

		if (this.packet == BungeePacket.CHANNEL) {

			final String channelName = input.readString();
			final String senderName = input.readString();
			final UUID senderUUID = input.readUUID();
			final String message = input.readString();
			final SimpleComponent component = createComponent(input.readString());
			final String consoleLog = input.readString();
			final boolean hasMuteBypass = input.readBoolean();
			final boolean hasIgnoreBypass = input.readBoolean();
			final boolean hasLogBypass = input.readBoolean();

			final Channel channel = Channel.findChannel(channelName);

			if (Settings.Channels.ENABLED && channel != null && channel.isBungee() && canSendMessage(channelName + senderName + consoleLog + hasMuteBypass + hasIgnoreBypass + hasLogBypass + message))
				channel.processBungeeMessage(senderName, senderUUID, message, component, consoleLog, hasMuteBypass, hasIgnoreBypass, hasLogBypass);
		}

		else if (this.packet == BungeePacket.REMOVE_MESSAGE_BY_UUID) {
			final Packets.RemoveMode removeMode = ReflectionUtil.lookupEnum(Packets.RemoveMode.class, input.readString());
			final UUID messageId = input.readUUID();

			if (HookManager.isProtocolLibLoaded())
				Packets.getInstance().removeMessage(removeMode, messageId);
		}

		else if (this.packet == BungeePacket.SPY) {
			final Spy.Type type = input.readEnum(Spy.Type.class);
			final String channelName = input.readString();
			final String message = input.readString();
			final SimpleComponent component = createComponent(input.readString());
			final Set<UUID> ignoredPlayers = Common.convertSet(Remain.fromJsonList(input.readString()), UUID::fromString);

			if (canSendMessage(this.senderUid.toString() + type + channelName + message + ignoredPlayers))
				Spy.broadcastFromBungee(type, channelName, message, component, ignoredPlayers);
		}

		else if (this.packet == BungeePacket.TOAST) {
			final UUID receiverUid = input.readUUID();
			final Toggle toggle = input.readEnum(Toggle.class);
			final String message = input.readString();
			final CompMaterial material = input.readEnum(CompMaterial.class);
			final Player receiver = Remain.getPlayerByUUID(receiverUid);

			if (receiver != null && receiver.isOnline() && canSendMessage(this.senderUid + receiverUid.toString() + toggle.getKey() + material.toString() + message))
				sendToast(player, toggle, message, material);
		}

		else if (this.packet == BungeePacket.PLAYERS_SYNC) {
			if (Settings.Integration.BungeeCord.ENABLED) {
				final SerializedMap mergedData = input.readMap();

				SyncedCache.upload(mergedData);
			}
		}

		else if (this.packet == BungeePacket.DB_UPDATE) {
			final String playerName = input.readString();
			final UUID uniqueId = input.readUUID();
			final String nick = input.readString();
			final SerializedMap data = input.readMap();
			final String message = input.readString();

			final Player online = Remain.getPlayerByUUID(uniqueId);

			if (MySQL.ENABLED) {
				UserMap.getInstance().cacheLocally(new UserMap.Record(playerName, uniqueId, nick.isEmpty() ? null : nick));
				PlayerCache.loadOrUpdateCache(playerName, uniqueId, data);

				if (online != null)
					Players.setTablistName(online);
			}

			if (online != null && !message.isEmpty())
				Messenger.info(online, message);
		}

		else if (this.packet == BungeePacket.REPLY_UPDATE) {
			final UUID targetUid = input.readUUID();
			final String replyPlayer = input.readString();
			final UUID replyUUID = input.readUUID();

			// Update the /reply recipient for the player if he is online
			final Player target = Remain.getPlayerByUUID(targetUid);

			if (target != null)
				SenderCache.from(target).setReplyPlayer(replyPlayer, replyUUID);
		}

		else if (this.packet == BungeePacket.MAIL_SYNC) {
			final String mailJson = input.readString();

			final ServerCache cache = ServerCache.getInstance();
			final Mail mail = Mail.deserialize(SerializedMap.fromJson(mailJson));

			cache.getMails().add(mail);
		}

		else if (this.packet == BungeePacket.ANNOUNCEMENT) {
			final AnnounceType type = input.readEnum(AnnounceType.class);
			final String message = input.readString();
			final SerializedMap params = input.readMap();

			if (params.containsKey("server") && !params.getString("server").equals(Remain.getServerName()))
				return;

			if (canSendMessage(this.senderUid + type.toString() + message + message))
				Announce.sendFromBungee(type, message, params);
		}

		else if (this.packet == BungeePacket.FORWARD_COMMAND) {
			final String server = input.readString();
			final String command = Common.colorize(input.readString().replace("{server_name}", server));

			if (server.equalsIgnoreCase(Remain.getServerName()) && canSendMessage(this.senderUid + server + command))
				Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
		}

		else if (this.packet == BungeePacket.LIST_PLAYERS_REQUEST) {
			final String requesterUUID = input.readString();
			input.readString(); // server to request from, unused here
			final String prefix = input.readString();

			BungeeUtil.tellBungee(BungeePacket.LIST_PLAYERS_RESPONSE, requesterUUID, ListPlayers.compilePlayers(prefix));
		}

		else if (this.packet == BungeePacket.LIST_PLAYERS_RESPONSE) {
			final UUID requester = input.readUUID();
			final SerializedMap mergedData = input.readMap();
			final CommandSender requestingPlayer = requester.equals(FoConstants.NULL_UUID) ? Bukkit.getConsoleSender() : Remain.getPlayerByUUID(requester);

			if (requestingPlayer != null && canSendMessage(this.senderUid + requester.toString() + mergedData.toJson()))
				ListPlayers.listPlayersFromBungee(requestingPlayer, mergedData);
		}

		else if (this.packet == BungeePacket.CLEAR_CHAT) {
			final String announceMessage = input.readString();
			Players.clearChatFromBungee(announceMessage.isEmpty());

			if (!announceMessage.isEmpty() && canSendMessage(this.senderUid + announceMessage))
				Messenger.broadcastAnnounce(announceMessage);
		}

		else if (this.packet == BungeePacket.ME) {
			final UUID senderId = input.readUUID();
			final boolean reachBypass = input.readBoolean();
			final SimpleComponent component = SimpleComponent.deserialize(input.readMap());

			if (canSendMessage(senderId.toString() + reachBypass + Remain.toJson(component.getTextComponent())))
				Players.showMe(senderId, reachBypass, component);
		}

		else if (this.packet == BungeePacket.MOTD) {
			final UUID receiverId = input.readUUID();
			final Player receiver = Remain.getPlayerByUUID(receiverId);

			if (receiver != null)
				Players.showMotd(receiver, false);
		}

		else if (this.packet == BungeePacket.NOTIFY) {
			final String permission = input.readString();
			final SimpleComponent component = SimpleComponent.deserialize(input.readMap());

			if (canSendMessage(this.senderUid + permission + Remain.toJson(component.getTextComponent())))
				Players.broadcastWithPermission(permission, component);
		}

		else if (this.packet == BungeePacket.PLAIN_BROADCAST) {
			final String plainMessage = input.readString();

			if (canSendMessage(this.senderUid + plainMessage))
				for (final Player online : Remain.getOnlinePlayers())
					Common.tellNoPrefix(online, plainMessage);
		}

		else if (this.packet == BungeePacket.PLAIN_MESSAGE) {
			final UUID receiver = input.readUUID();
			final String message = input.readString();
			final Player online = Remain.getPlayerByUUID(receiver);

			if (online != null && canSendMessage(this.senderUid + receiver.toString() + message))
				Common.tellNoPrefix(online, message);
		}

		else if (this.packet == BungeePacket.SIMPLECOMPONENT_MESSAGE) {
			final UUID receiver = input.readUUID();
			String json = input.readString();
			final Player online = Remain.getPlayerByUUID(receiver);

			if (online != null) {
				json = json.replace("{receiver_prefix}", HookManager.getPlayerPrefix(online));

				final SimpleComponent component = SimpleComponent.deserialize(SerializedMap.fromJson(json));

				if (canSendMessage(this.senderUid + receiver.toString() + Remain.toJson(component.getTextComponent())))
					component.send(online);
			}
		}

		else if (this.packet == BungeePacket.JSON_BROADCAST) {
			final BaseComponent[] components = Remain.toComponent(input.readString());

			if (canSendMessage(this.senderUid + Remain.toJson(components)))
				for (final Player online : Remain.getOnlinePlayers())
					Remain.sendComponent(online, components);
		}

		else if (this.packet == BungeePacket.MUTE) {
			final String type = input.readString();
			final String object = input.readString();
			final String durationRaw = input.readString();
			final String announceMessage = input.readString();

			final boolean isOff = "off".equals(durationRaw);
			final SimpleTime duration = isOff ? null : SimpleTime.from(durationRaw);

			if (!canSendMessage(this.senderUid + type + object + durationRaw + announceMessage))
				return;

			if (type.equals("channel")) {
				final Channel channel = Channel.findChannel(object);

				if (channel != null) {
					channel.setMuted(duration);

					for (final Player online : channel.getOnlinePlayers().keySet())
						Common.tellNoPrefix(online, announceMessage);
				}
			}

			else if (type.equals("server")) {
				ServerCache.getInstance().setMuted(duration);

				for (final Player online : Remain.getOnlinePlayers())
					Common.tellNoPrefix(online, announceMessage);

			} else
				throw new FoException("Unhandled mute packet type " + type);
		}

		else if (this.packet == BungeePacket.SOUND) {
			final UUID receiverUUID = input.readUUID();
			final SimpleSound sound = new SimpleSound(input.readString());
			final Player receiver = Remain.getPlayerByUUID(receiverUUID);

			if (receiver != null && receiver.isOnline())
				sound.play(receiver);
		}

		else
			throw new FoException("Unhandled packet from BungeeControl: " + this.packet);
	}

	/*
	 * Return true if message can be sent, same messages can only
	 * be sent each 100ms
	 */
	private boolean canSendMessage(String message) {
		final long timestamp = this.redisDeduplicator.getOrDefault(message, -1L);

		if (timestamp == -1 || (System.currentTimeMillis() - timestamp) > 100) {
			this.redisDeduplicator.put(message, System.currentTimeMillis());

			return true;
		}

		return false;
	}

	/*
	 * Sends a toast message to the player given he is not ignoring it nor the sender
	 */
	private void sendToast(Player player, Toggle toggle, String message, CompMaterial material) {
		final PlayerCache cache = PlayerCache.from(player);

		if (!cache.isIgnoringPart(toggle) && !cache.isIgnoringPlayer(this.senderUid))
			Remain.sendToast(player, message, material);
	}

	/*
	 * Add bungee prefix
	 */
	private SimpleComponent createComponent(String json) {

		// Read the component
		final SimpleComponent component = SimpleComponent.deserialize(SerializedMap.fromJson(json));

		// Replace prefix variables
		final String prefix = Settings.Integration.BungeeCord.PREFIX.replace("{bungee_server_name}", this.server);

		return component.appendFirst(SimpleComponent.of(prefix));
	}
}