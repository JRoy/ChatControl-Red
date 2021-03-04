package org.mineacademy.chatcontrol.listener;

import java.util.Iterator;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.server.TabCompleteEvent;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.Players;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.PlayerUtil;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Listens for tab completions
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TabListener implements Listener {

	@Getter
	private static final TabListener instance = new TabListener();

	/**
	 * Filter tab completions for all commands, this is sent ONCE when player joins
	 *
	 * @param event
	 */
	@EventHandler
	public void onComplete(PlayerCommandSendEvent event) {
		if (PlayerUtil.hasPerm(event.getPlayer(), Permissions.Bypass.TAB_COMPLETE))
			return;

		for (final Iterator<String> it = event.getCommands().iterator(); it.hasNext();) {
			final String command = "/" + it.next();

			if (!Settings.TabComplete.WHITELIST.isInListRegex(command))
				it.remove();
		}
	}

	/**
	 * Filter tab completions for command arguments, set for each letter player types in command
	 *
	 * @param event
	 */
	@EventHandler
	public void onComplete(TabCompleteEvent event) {

		// Filter vanished
		if (!PlayerUtil.hasPerm(event.getSender(), Permissions.Bypass.REACH))
			Players.removeVanished(event.getCompletions());

		// Filter tab complete
		if (!PlayerUtil.hasPerm(event.getSender(), Permissions.Bypass.TAB_COMPLETE)) {
			final String buffer = event.getBuffer();
			final String[] split = buffer.split(" ");

			final String label = split.length > 1 ? split[0] : "";
			final String lastWord = split.length > 1 ? split[split.length - 1] : "";

			if ((buffer.endsWith(" ") && Settings.TabComplete.PREVENT_IF_BELOW_LENGTH > 0) || lastWord.length() < Settings.TabComplete.PREVENT_IF_BELOW_LENGTH)
				event.setCancelled(true);

			else if (!"".equals(label) && !Settings.TabComplete.WHITELIST.isInListRegex(label))
				event.setCancelled(true);
		}
	}
}
