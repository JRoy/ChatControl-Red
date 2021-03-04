package org.mineacademy.chatcontrol.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.chatcontrol.settings.Settings.Integration.BungeeCord;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.model.ChatPaginator;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.Variables;
import org.mineacademy.fo.remain.Remain;

/**
 * Class handling listing players on server
 */
public final class ListPlayers {

	/**
	 * Show a formatted list of all players over BungeeCord network from the given
	 * data that was compiled on BungeeCord
	 *
	 * @param sender
	 * @param map
	 */
	public static void listPlayersFromBungee(CommandSender sender, SerializedMap map) {
		showTo(sender, map);
	}

	/**
	 * Show players from this server using the given prefix as a sorting mechanism
	 *
	 * @param sender
	 * @param prefix
	 */
	public static void listPlayers(CommandSender sender, String prefix) {
		showTo(sender, compilePlayers(prefix));
	}

	/**
	 * Return a list of player names and their respective lines in the list command
	 *
	 * @param prefix
	 * @return
	 */
	public static SerializedMap compilePlayers(String prefix) {
		Valid.checkNotNull(prefix, "List players Sort_Prefix is null!");

		final SerializedMap map = new SerializedMap();

		for (final Player online : Remain.getOnlinePlayers())
			map.put(online.getUniqueId().toString(),
					Format
							.parse(Settings.ListPlayers.FORMAT_LINE)
							.build(online, "", SerializedMap.of("sort_prefix", Variables.replace(prefix, online)))
							.serialize().toJson());

		return map;
	}

	/**
	 * Show time!
	 *
	 * @param sender
	 * @param lines playername:line as simplecomponent
	 */
	private static void showTo(CommandSender sender, SerializedMap lines) {
		final List<SimpleComponent> pages = new ArrayList<>();

		for (final Object mapRaw : lines.values()) {
			final SerializedMap data = SerializedMap.fromJson(mapRaw.toString());
			final SimpleComponent line = SimpleComponent.deserialize(data);

			pages.add(line);
		}

		// Sort a-z
		Collections.sort(pages, (f, s) -> Common.stripColors(f.getPlainMessage()).compareTo(Common.stripColors(s.getPlainMessage())));

		new ChatPaginator()
				.setFoundationHeader(Lang.ofScript("Commands.List.Header", SerializedMap.of("bungee", BungeeCord.ENABLED), lines.size()))
				.setPages(pages)
				.send(sender);
	}
}
