package org.mineacademy.chatcontrol.api;

import java.util.Set;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.fo.event.SimpleEvent;

import lombok.Getter;
import lombok.Setter;

/**
 * Event for when players send a message to a chat channel.
 */
@Getter
@Setter
public final class ChatChannelEvent extends SimpleEvent implements Cancellable {

	private static final HandlerList handlers = new HandlerList();

	/**
	 * The chat channel
	 */
	private final Channel channel;

	/**
	 * The player who issued the message
	 */
	private final CommandSender sender;

	/**
	 * A list of players receiving this message
	 */
	private final Set<Player> recipients;

	/**
	 * The message
	 */
	private String message;

	/**
	 * Is the event cancelled?
	 */
	private boolean cancelled;

	public ChatChannelEvent(Channel channel, CommandSender sender, String message, Set<Player> recipients) {
		this.channel = channel;
		this.sender = sender;
		this.message = message;
		this.recipients = recipients;
	}

	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}