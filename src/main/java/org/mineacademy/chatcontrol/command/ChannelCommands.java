package org.mineacademy.chatcontrol.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.PlayerCache;
import org.mineacademy.chatcontrol.ServerCache;
import org.mineacademy.chatcontrol.command.ChatControlCommands.GenericSubCommand;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.chatcontrol.model.Channel.Mode;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.chatcontrol.settings.Settings.Channels;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.PlayerUtil;
import org.mineacademy.fo.command.SimpleCommandGroup;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.settings.SimpleLocalization;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Stores all /channel commands
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ChannelCommands extends SimpleCommandGroup {

	/**
	 * The singleton of this class
	 */
	@Getter
	private final static SimpleCommandGroup instance = new ChannelCommands();

	/**
	 * @see org.mineacademy.fo.command.SimpleCommandGroup#getHelpHeader()
	 */
	@Override
	protected String[] getHelpHeader() {
		return new String[] {
				"&8",
				"&8" + Common.chatLineSmooth(),
				getHeaderPrefix() + " Channel Commands",
				" ",
				"&2  [] &f= " + SimpleLocalization.Commands.LABEL_OPTIONAL_ARGS,
				"&6  <> &f= " + SimpleLocalization.Commands.LABEL_REQUIRED_ARGS,
				" "
		};
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommandGroup#getHeaderPrefix()
	 */
	@Override
	protected String getHeaderPrefix() {
		return "" + ChatColor.DARK_RED + ChatColor.BOLD;
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommandGroup#getNoParamsHeader(org.bukkit.command.CommandSender)
	 */
	@Override
	protected List<SimpleComponent> getNoParamsHeader(CommandSender sender) {
		final List<SimpleComponent> messages = new ArrayList<>();

		final boolean isPlayer = sender instanceof Player;
		final PlayerCache cache = isPlayer ? PlayerCache.from((Player) sender) : null;

		if (Lang.getOption("Channels.Use_Alternative_Header")) {
			final String channels = !isPlayer ? Lang.of("None")
					: Common.join(Common.convert(cache.getChannels().entrySet(),
							entry -> Lang.of("Channels.Header_Alternative_Channel")
									.replace("{channel}", entry.getKey().getName())
									.replace("{mode}", entry.getValue().getKey())));

			messages.addAll(Lang.ofComponentList("Channels.Header_Alternative", channels));

		} else {

			messages.add(SimpleComponent.of("&8" + Common.chatLineSmooth()));
			messages.add(SimpleComponent.of((isPlayer ? "<center>" : " ") + getHeaderPrefix() + Lang.of("Channels.Header")));
			messages.add(SimpleComponent.of(" "));

			if (!Settings.Channels.ENABLED) {
				messages.add(Lang.ofComponent("Channels.Disabled"));

				return messages;
			}

			if (isPlayer && Channels.IGNORE_WORLDS.contains(((Player) sender).getWorld().getName())) {
				messages.add(Lang.ofComponent("Channels.Disabled_World", ((Player) sender).getWorld()));

				return messages;
			}

			if (Channel.getChannels().isEmpty()) {
				messages.add(Lang.ofComponent("Channels.No_Channels"));

				return messages;
			}

			{ // Fill in available channels
				final SimpleComponent available = Lang.ofComponent("Channels.Available");

				final List<String> channelNames = Channel.getChannelNames();
				boolean atLeastOneAvailable = false;

				for (int i = 0; i < channelNames.size(); i++) {
					final String channel = channelNames.get(i);

					if (!isPlayer || PlayerUtil.hasPerm(sender, Permissions.Channel.JOIN.replace("{channel}", channel).replace("{mode}", Channel.Mode.WRITE.getKey()))) {
						available.append(channel);

						final Channel currentWriteChannel = cache != null ? cache.getWriteChannel() : null;
						final List<String> hover = new ArrayList<>();

						if (currentWriteChannel != null && currentWriteChannel.getName().equals(channel))
							hover.add(Lang.of("Channels.Tooltip_Joined"));

						else {
							hover.add(Lang.of("Channels.Tooltip_Join_Write"));

							if (currentWriteChannel != null)
								hover.addAll(Lang.ofList("Channels.Tooltip_Leave_Write", currentWriteChannel.getName()));

							available.onClickRunCmd("/" + Settings.Channels.COMMAND_ALIASES.get(0) + " join " + channel);
						}

						if (!hover.isEmpty())
							available.onHover(hover);

						if (i + 1 < channelNames.size())
							available.append(", ");

						atLeastOneAvailable = true;
					}
				}

				if (atLeastOneAvailable) {
					messages.add(available);

					if (isPlayer)
						messages.add(SimpleComponent.of(" "));
				}
			}

			// Fill in the channels player is in
			if (isPlayer) {
				messages.add(Lang.ofComponent("Channels.Tooltip_Channels"));

				boolean atLeastOneJoined = false;
				final List<Entry<Channel, Mode>> entries = new ArrayList<>(cache.getChannels().entrySet());

				Collections.sort(entries, (f, s) -> f.getKey().getName().compareTo(s.getKey().getName()));

				for (final Entry<Channel, Mode> entry : entries) {
					final String channel = entry.getKey().getName();
					final String mode = entry.getValue().getKey();

					final SimpleComponent channelEnum = SimpleComponent.of("&7 - &f" + channel + " &7(" + mode + ")");

					if (PlayerUtil.hasPerm(sender, Permissions.Channel.LEAVE.replace("{channel}", channel)))
						channelEnum.onHover(Lang.of("Channels.Tooltip_Leave")).onClickRunCmd("/" + Settings.Channels.COMMAND_ALIASES.get(0) + " leave " + channel);

					messages.add(channelEnum);
					atLeastOneJoined = true;
				}

				if (!atLeastOneJoined)
					messages.add(SimpleComponent.of(" &7- &o" + Lang.of("None")));
			}

			messages.add(SimpleComponent.of("&8" + Common.chatLineSmooth()));
		}

		return messages;
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommandGroup#registerSubcommands()
	 */
	@Override
	protected void registerSubcommands() {
		registerSubcommand(new ChannelJoin());
		registerSubcommand(new ChannelLeave());
		registerSubcommand(new ChannelList());
		registerSubcommand(new ChannelSend());
		registerSubcommand(new ChannelSendAs());
	}

	/**
	 * A helper class used by all channel commands
	 */
	public static abstract class ChannelSubCommand extends GenericSubCommand {

		protected ChannelSubCommand(String sublabel) {
			super(ChannelCommands.getInstance(), sublabel);

			setPermission("chatcontrol.channel.{sublabel}");
		}

		/**
		 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
		 */
		@Override
		protected final void execute() {

			if (!ServerCache.getInstance().isTourCompleted()) {
				checkBoolean(sender.isOp(), Lang.of("Commands.Tour.Requires_Op"));

				returnTell(Lang.of("Commands.Tour.Not_Completed", Settings.MAIN_COMMAND_ALIASES.get(0)));
			}

			if (!Channels.ENABLED)
				returnTell(Lang.of("Channels.Disabled"));

			if (isPlayer() && Channels.IGNORE_WORLDS.contains(getPlayer().getWorld().getName()))
				returnTell(Lang.of("Channels.Disabled_World"));

			this.onChannelCommand();
		}

		/**
		 * Same as onCommand but we already checked if channels are enabled
		 */
		protected abstract void onChannelCommand();
	}
}
