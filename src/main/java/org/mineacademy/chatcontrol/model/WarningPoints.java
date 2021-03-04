package org.mineacademy.chatcontrol.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.PlayerCache;
import org.mineacademy.chatcontrol.model.Bungee.BungeePacket;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.BungeeUtil;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.MathUtil;
import org.mineacademy.fo.MathUtil.CalculatorException;
import org.mineacademy.fo.PlayerUtil;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.collection.StrictSet;
import org.mineacademy.fo.debug.Debugger;
import org.mineacademy.fo.debug.LagCatcher;
import org.mineacademy.fo.exception.EventHandledException;
import org.mineacademy.fo.model.Replacer;
import org.mineacademy.fo.model.Variables;
import org.mineacademy.fo.remain.Remain;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Implementation of the warning points system.
 *
 * @since 29.09.2020
 */
public final class WarningPoints {

	/**
	 * The instance of this class for public use.
	 */
	@Getter
	private static final WarningPoints instance = new WarningPoints();

	/**
	 * Warning sets.
	 */
	private final StrictSet<WarnSet> sets = new StrictSet<>();

	// ---------------------------------------------------------------------------------------------------------------------------
	// Sub-classes
	// ---------------------------------------------------------------------------------------------------------------------------

	/**
	 * Represents a warn trigger
	 */
	@RequiredArgsConstructor
	public static final class WarnTrigger {

		/**
		 * The set name
		 */
		private final String set;

		/**
		 * The formula with variables
		 */
		private final String formula;

		/**
		 * Execute this for player, if false or no warning message show we
		 * show him the fallback one
		 *
		 * @param sender
		 * @param fallbackWarningMessage
		 * @param variables
		 */
		public void execute(CommandSender sender, String fallbackWarningMessage, SerializedMap variables) {
			if (Settings.WarningPoints.ENABLED && sender instanceof Player) {
				final String expression = Replacer.replaceVariables(this.formula, variables);
				Valid.checkBoolean(!expression.contains("{") && !expression.contains("}"),
						"Unparsed variables found when evaluating warning points for " + sender.getName() + ". Supported variables: " + variables.keySet() + " Warning message: " + fallbackWarningMessage + ". Please correct your expression: " + expression);

				double calculatedPoints;

				try {
					calculatedPoints = MathUtil.calculate(expression);

				} catch (final CalculatorException ex) {
					Common.error(ex,
							"Failed to calculate warning points for " + sender.getName(),
							"Expression: '" + expression + "'",
							"Variables supported: " + variables,
							"Error: %error");

					return;
				}

				final boolean warned = WarningPoints.getInstance().givePoints((Player) sender, this.set, calculatedPoints);

				if (warned)
					throw new EventHandledException(true);
			}

			throw new EventHandledException(true, fallbackWarningMessage);
		}
	}

	/**
	 * Represents a warning set with actions.
	 */
	@Getter(AccessLevel.PRIVATE)
	@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
	public static final class WarnSet {

		private final String name;
		private final List<WarnAction> actions;

		@Override
		public boolean equals(Object obj) {
			return obj instanceof WarnSet ? ((WarnSet) obj).name.equals(this.name) : false;
		}
	}

	/**
	 * Represents an action in a warning set.
	 */
	@Getter(AccessLevel.PRIVATE)
	@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
	public final static class WarnAction {

		private final int trigger;
		private final List<String> commands;

		@Override
		public boolean equals(Object obj) {
			return obj instanceof WarnAction ? ((WarnAction) obj).trigger == this.trigger : false;
		}

		/**
		 * Run this action and return whether or not the player was warned.
		 *
		 * @param player
		 * @return
		 */
		public boolean execute(Player player) {
			boolean warned = true;

			for (String commandLine : commands) {
				commandLine = Variables.replace(commandLine, player);

				if (commandLine.startsWith("warn ")) {
					Common.tellLater(2, player, commandLine.replaceFirst("warn ", ""));
					warned = true;

				} else if (commandLine.startsWith("bungeeconsole ")) {
					if (Settings.Integration.BungeeCord.ENABLED) {
						final String[] split = commandLine.replace("bungeeconsole ", "").split(" ");
						final String server = split.length > 1 ? split[0] : "bungee";
						final String command = split.length > 1 ? Common.joinRange(1, split) : split[0];

						BungeeUtil.tellBungee(BungeePacket.FORWARD_COMMAND, server, command);
					}

				} else
					Common.dispatchCommand(player, commandLine);
			}

			return warned;
		}

		@Override
		public String toString() {
			return "Action[" + trigger + "]";
		}
	}

	// ---------------------------------------------------------------------------------------------------------------------------
	// Sets manipulation
	// ---------------------------------------------------------------------------------------------------------------------------

	/**
	 * Add a new warning set.
	 *
	 * @param name
	 * @param rawActions
	 */
	public void addSet(String name, SerializedMap rawActions) {
		Valid.checkBoolean(!isSetLoaded(name), "Warning set '" + name + "' already exists! (Use /chc reload, not plugin managers!)");

		// Sort actions by points
		final TreeSet<WarnAction> loadedActions = new TreeSet<>((first, second) -> {
			final int x = second.getTrigger();
			final int y = first.getTrigger();

			return x > y ? -1 : x == y ? 0 : 1;
		});

		// Load them
		rawActions.forEach((triggerRaw, actions) -> {
			final int trigger = Integer.parseInt(triggerRaw);

			loadedActions.add(new WarnAction(trigger, (List<String>) actions));
		});

		sets.add(new WarnSet(name, new ArrayList<>(loadedActions)));
	}

	/**
	 * Returns true if a set by the given name exists.
	 *
	 * @param name
	 * @return
	 */
	public boolean isSetLoaded(String name) {
		return getSet(name) != null;
	}

	/**
	 * Get a set by its name
	 *
	 * @param name
	 * @return
	 */
	public WarnSet getSet(String name) {
		for (final WarnSet set : sets)
			if (set.getName().equals(name))
				return set;

		return null;
	}

	/**
	 * Clears sets, called on reload.
	 */
	public void clearSets() {
		sets.clear();
	}

	/**
	 * Return all set names
	 */
	public List<String> getSetNames() {
		return Common.convert(this.sets, WarnSet::getName);
	}

	// ---------------------------------------------------------------------------------------------------------------------------
	// Giving points
	// ---------------------------------------------------------------------------------------------------------------------------

	/**
	 * Gives points to player and runs proper actions. Returns if the player was warned so you can
	 * prevent sending duplicate messages.
	 * <p>
	 * Please note that we round the amount to a whole number.
	 *
	 * @param reason
	 * @param player
	 * @param setName
	 * @param exactAmount, the warn points amount. If the player is a newcomer it will be multiplied
	 *                     by the setting option this number is later rounded to an integer
	 * @return
	 */
	public boolean givePoints(Player player, String setName, double exactAmount) {
		if (!Settings.WarningPoints.ENABLED || PlayerUtil.hasPerm(player, Permissions.Bypass.WARNING_POINTS) || exactAmount < 1)
			return false;

		final WarnSet set = getSet(setName);
		Valid.checkNotNull(set, "Cannot give warn points to a non-existing warn set '" + setName + "'. Available: " + Common.join(sets, ", ", WarnSet::getName));

		// Multiply if the player is a newcomer
		final int amount = (int) Math.round(exactAmount * (Newcomer.isNewcomer(player) ? Settings.Newcomer.WARNING_POINTS_MULTIPLIER : 1));

		final int points = assignPoints(player, setName, amount);
		final WarnAction action = findHighestAction(set, points, player);

		return action != null && action.execute(player);
	}

	//
	// Give the warning points to the player's data.db section.
	// Returns new points
	//
	private int assignPoints(Player player, String set, int amount) {
		final PlayerCache data = PlayerCache.from(player);

		final int oldPoints = data.getWarnPoints(set);
		final int newPoints = oldPoints + amount;

		Common.log("Set " + player.getName() + "'s warning set '" + set + "' points from " + oldPoints + " -> " + newPoints);

		data.setWarnPointsNoSave(set, newPoints);

		Common.runLater(() -> data.save());

		return newPoints;
	}

	//
	// Find the highest action we can run for a player.
	//
	private WarnAction findHighestAction(WarnSet set, int points, Player player) {
		WarnAction highestAction = null;

		Debugger.debug("points", "Before we found highest action, " + player.getName() + " had " + points + " points.");

		for (int i = 0; i < set.getActions().size(); i++) {
			final WarnAction action = set.getActions().get(i);

			// If player has more or equals points for this action, assign it.
			if (points >= action.getTrigger()) {

				if (highestAction != null && highestAction.getTrigger() > action.getTrigger()) {
					Debugger.debug("points", "-> Is elligible BUT has already a better action. Tried: " + action + ", has: " + highestAction);

					continue;
				}

				highestAction = action;
				Debugger.debug("points", "-> Is elligible for " + action);

			} else
				Debugger.debug("points", "-> Has not enough points for " + action);
		}

		Debugger.debug("points", "RESULT: Executing action " + highestAction);
		return highestAction;
	}

	// ---------------------------------------------------------------------------------------------------------------------------
	// Static
	// ---------------------------------------------------------------------------------------------------------------------------

	/**
	 * Start the task removing old warning points if the warning points are enabled and the tasks
	 * repeat period is over 0
	 */
	public static void scheduleTask() {
		if (!Settings.WarningPoints.ENABLED || Settings.WarningPoints.RESET_TASK_PERIOD.getRaw().equals("0"))
			return;

		Common.runTimer(Settings.WarningPoints.RESET_TASK_PERIOD.getTimeTicks(), () -> {
			LagCatcher.start("Reset warning points");

			for (final Player player : Remain.getOnlinePlayers()) {
				final PlayerCache data = PlayerCache.from(player);

				for (final Map.Entry<String, Integer> entry : new HashMap<>(data.getWarnPoints()).entrySet()) {
					final String set = entry.getKey();
					final int amount = entry.getValue();

					final Integer setRemoveThreshold = Settings.WarningPoints.RESET_MAP.get(set);

					// if the remove task has this set
					if (setRemoveThreshold != null) {
						final int remaining = Math.max(0, amount - setRemoveThreshold);

						Log.logTip("TIP Note: Reset " + player.getName() + "'s warning points in " + set + " from " + amount + " to " + remaining);
						data.setWarnPointsNoSave(set, remaining);
						data.save();
					}
				}
			}

			LagCatcher.end("Reset warning points", Debugger.isDebugged("points"));
		});
	}
}
