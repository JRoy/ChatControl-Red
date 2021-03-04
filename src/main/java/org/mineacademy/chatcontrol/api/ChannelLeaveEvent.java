package org.mineacademy.chatcontrol.api;

import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.mineacademy.chatcontrol.PlayerCache;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.fo.event.SimpleEvent;

import lombok.Getter;
import lombok.Setter;

/**
 * An event that is executed when a player leaves a channel.
 */
@Getter
public final class ChannelLeaveEvent extends SimpleEvent implements Cancellable {

	private static final HandlerList handlers = new HandlerList();

	/**
	 * The cache of the Dude himself
	 */
	private final PlayerCache cache;

	/**
	 * The channel leaving
	 */
	private final Channel channel;

	/**
	 * The mode player had
	 */
	private final Channel.Mode mode;

	/**
	 * Prevent dude leaving?
	 */
	@Setter
	private boolean cancelled;

	public ChannelLeaveEvent(PlayerCache cache, Channel channel, Channel.Mode mode) {
		this.cache = cache;
		this.channel = channel;
		this.mode = mode;
	}

	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}