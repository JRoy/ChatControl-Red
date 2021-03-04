package org.mineacademy.chatcontrol.command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mineacademy.chatcontrol.PlayerCache;
import org.mineacademy.chatcontrol.command.ChatControlCommands.ChatControlSubCommand;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.WarningPoints;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.model.ChatPaginator;
import org.mineacademy.fo.model.SimpleComponent;

public final class ChatControlPoints extends ChatControlSubCommand {

	public ChatControlPoints() {
		super("points/p");

		setUsage(Lang.of("Commands.Points.Usage"));
		setDescription(Lang.of("Commands.Points.Description"));
		setMinArguments(1);
		setPermission(Permissions.Command.POINTS);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected String[] getMultilineUsageMessage() {
		return Lang.ofArray("Commands.Points.Usages");
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void execute() {
		checkBoolean(Settings.WarningPoints.ENABLED, Lang.of("Commands.Points.Disabled"));

		final WarningPoints points = WarningPoints.getInstance();
		final String param = args[0];

		if ("list".equals(param)) {
			checkUsage(args.length < 3);

			final String setName = args.length == 2 ? args[1] : null;
			checkBoolean(setName == null || points.isSetLoaded(setName), Lang.of("Commands.Invalid_Warning_Set", setName, Common.join(points.getSetNames())));

			pollCaches(caches -> {

				// Fill in the map
				// <Set name:<Player:Points>>
				final Map<String, Map<String, Integer>> listed = new HashMap<>();

				for (final PlayerCache cache : caches)
					for (final Map.Entry<String, Integer> entry : cache.getWarnPoints().entrySet()) {
						final String diskSet = entry.getKey();
						final int diskPoints = entry.getValue();

						if (setName == null || setName.equalsIgnoreCase(diskSet)) {
							final Map<String, Integer> playerPoints = listed.getOrDefault(diskSet, new HashMap<>());
							playerPoints.put(cache.getPlayerName(), diskPoints);

							listed.put(diskSet, playerPoints);
						}
					}

				final List<SimpleComponent> messages = new ArrayList<>();

				for (final Map.Entry<String, Map<String, Integer>> entry : listed.entrySet()) {
					messages.add(SimpleComponent.empty());
					messages.add(Lang.ofComponent("Commands.Points.List_Item_1", entry.getKey()));

					for (final Map.Entry<String, Integer> setEntry : entry.getValue().entrySet())
						messages.add(SimpleComponent
								.of(" &7- ")
								.append("&8[&4X&8]")
								.onHover(Lang.of("Commands.Points.Remove_Tooltip"))
								.onClickRunCmd("/" + getLabel() + " " + getSublabel() + " set " + setEntry.getKey() + " " + entry.getKey() + " 0")
								.append(Lang.of("Commands.Points.List_Item_2", setEntry.getKey(), setEntry.getValue())));
				}

				checkBoolean(!messages.isEmpty(), Lang.of("Commands.Points.No_Points"));

				new ChatPaginator(12)
						.setFoundationHeader(Lang.of("Commands.Points.List_Header"))
						.setPages(messages)
						.send(sender);
			});

			return;
		}

		checkArgs(2, Lang.of("Commands.No_Cached_Player"));
		checkUsage(args.length >= 2);

		final String setName = args.length == 2 ? null : args[2];

		pollCache(args[1], cache -> {

			if ("get".equals(param)) {
				checkUsage(args.length <= 3);

				// Fill in the map
				// <Set name:Points>
				final Map<String, Integer> listed = new HashMap<>();

				for (final Map.Entry<String, Integer> entry : cache.getWarnPoints().entrySet()) {
					final String diskSet = entry.getKey();
					final int diskPoints = entry.getValue();

					if (setName == null || setName.equalsIgnoreCase(diskSet))
						listed.put(diskSet, diskPoints);
				}

				final List<SimpleComponent> messages = new ArrayList<>();

				for (final Map.Entry<String, Integer> entry : listed.entrySet()) {
					messages.add(SimpleComponent
							.of(" &7- ")
							.append("&8[&4X&8]")
							.onHover(Lang.of("Commands.Points.Remove_Tooltip"))
							.onClickRunCmd("/" + getLabel() + " " + getSublabel() + " set " + cache.getPlayerName() + " " + entry.getKey() + " 0")
							.append(Lang.of("Commands.Points.Player_List_Item", entry.getKey(), entry.getValue())));
				}

				checkBoolean(!messages.isEmpty(), Lang.of("Commands.Points.No_Player_Points", cache.getPlayerName()));

				new ChatPaginator(12)
						.setFoundationHeader(Lang.of("Commands.Points.Player_List_Header", cache.getPlayerName()))
						.setPages(messages)
						.send(sender);
			}

			else if ("set".equals(param)) {
				checkArgs(3, Lang.of("Commands.Points.No_Set", Common.join(points.getSetNames())));
				checkUsage(args.length <= 4);

				final int pointsToSet = findNumber(3, Lang.of("Commands.Points.No_Amount"));

				if (pointsToSet == 0 && cache.getWarnPoints(setName) == 0)
					returnTell(Lang.of("Commands.Points.No_Stored", cache.getPlayerName(), setName));

				cache.setWarnPointsNoSave(setName, pointsToSet);
				cache.save();

				tellSuccess(Lang.ofScript("Commands.Points.Set", SerializedMap.of("removed", pointsToSet == 0), pointsToSet, cache.getPlayerName(), setName));

				// Notify BungeeCord so that players connected on another server get their channel updated
				updateBungeeData(cache);
			}

			else
				returnInvalidArgs();
		});
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {

		if (args.length == 1)
			return completeLastWord("get", "set", "list");

		if (args.length == 2) {
			final String param = args[0];

			if ("get".equals(param) || "set".equals(param))
				return completeLastWordPlayerNames();

			else if ("list".equals(param))
				return completeLastWord(WarningPoints.getInstance().getSetNames());
		}

		if (args.length == 3 && ("get".equals(args[0]) || "set".equals(args[0])))
			return completeLastWord(WarningPoints.getInstance().getSetNames());

		return NO_COMPLETE;
	}
}
