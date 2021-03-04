package org.mineacademy.chatcontrol.operator;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.BlockProjectileSource;
import org.bukkit.projectiles.ProjectileSource;
import org.mineacademy.chatcontrol.ServerCache;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.ItemUtil;
import org.mineacademy.fo.PlayerUtil;
import org.mineacademy.fo.ReflectionUtil;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.debug.Debugger;
import org.mineacademy.fo.exception.EventHandledException;
import org.mineacademy.fo.exception.FoException;
import org.mineacademy.fo.model.HookManager;
import org.mineacademy.fo.model.IsInList;
import org.mineacademy.fo.model.JavaScriptExecutor;
import org.mineacademy.fo.model.Tuple;
import org.mineacademy.fo.remain.CompMaterial;
import org.mineacademy.fo.remain.Remain;
import org.mineacademy.fo.visual.VisualizedRegion;

import lombok.Getter;

/**
 * Represents join, leave, kick or timed message broadcast
 */
@Getter
public final class DeathMessage extends PlayerMessage {

	/**
	 * Permission required for the killer that caused the rule to fire in
	 * order for the rule to apply
	 */
	@Nullable
	private Tuple<String, String> requireKillerPermission;

	/**
	 * JavaScript boolean output required to be true for the rule to apply
	 */
	@Nullable
	private String requireKillerScript;

	/**
	 * World names to require
	 */
	private final Set<String> requireKillerWorlds = new HashSet<>();

	/**
	 * Region names to require
	 */
	private final Set<String> requireKillerRegions = new HashSet<>();

	/**
	 * Killer items to require
	 */
	private final Set<String> requireKillerItems = new HashSet<>();

	/**
	 * Damage causes to require
	 */
	private final Set<DamageCause> requireCauses = new HashSet<>();

	/**
	 * Projectile types to require
	 */
	private final Set<EntityType> requireProjectiles = new HashSet<>();

	/**
	 * Blocks that caused damage to require
	 */
	private final Set<CompMaterial> requireBlocks = new HashSet<>();

	/**
	 * Killer types to require
	 */
	private final Set<EntityType> requireKillers = new HashSet<>();

	/**
	 * Bosses from the Boss plugin to require
	 */
	@Nullable
	private IsInList<String> requireBosses;

	/**
	 * The minimum damage to require
	 */
	private Double requireDamage;

	/**
	 * Permission to bypass the rule
	 */
	@Nullable
	private String ignoreKillerPermission;

	/**
	 * JavaScript boolean output when true for the rule to bypass
	 */
	@Nullable
	private String ignoreKillerScript;

	/**
	 * Gamemodes to ignore
	 */
	private final Set<GameMode> ignoreKillerGamemodes = new HashSet<>();

	/**
	 * World names to ignore
	 */
	private final Set<String> ignoreKillerWorlds = new HashSet<>();

	/**
	 * Region names to ignore
	 */
	private final Set<String> ignoreKillerRegions = new HashSet<>();

	/**
	 * Killer items to ignore
	 */
	private final Set<String> ignoreKillerItems = new HashSet<>();

	/**
	 * List of channels to ignore matching from
	 */
	private final Set<String> ignoreKillerChannels = new HashSet<>();

	public DeathMessage(String group) {
		super(Type.DEATH, group);
	}

	/**
	 * @see org.mineacademy.chatcontrol.operator.PlayerMessage#onParse(java.lang.String, java.lang.String, java.lang.String[])
	 */
	@Override
	protected boolean onParse(String firstThreeParams, String theRestThree, String[] args) {

		firstThreeParams = Common.joinRange(0, 3, args, " ");
		theRestThree = Common.joinRange(3, args);
		final List<String> theRestThreeSplit = splitVertically(theRestThree);

		final String firstTwoParams = Common.joinRange(0, 2, args);
		final String theRestTwo = Common.joinRange(2, args);
		final List<String> theRestTwoSplit = splitVertically(theRestTwo);

		if ("require killer perm".equals(firstThreeParams) || "require killer permission".equals(firstThreeParams)) {
			checkNotSet(this.requireKillerPermission, "require killer perm");
			final String[] split = theRestThree.split(" ");

			this.requireKillerPermission = new Tuple<>(split[0], split.length > 1 ? Common.joinRange(1, split) : null);
		}

		else if ("require killer script".equals(firstThreeParams)) {
			checkNotSet(this.requireKillerScript, "require sender script");

			this.requireKillerScript = theRestThree;
		}

		else if ("require killer world".equals(firstThreeParams) || "require killer worlds".equals(firstThreeParams))
			this.requireKillerWorlds.addAll(theRestThreeSplit);

		else if ("require killer region".equals(firstThreeParams) || "require killer regions".equals(firstThreeParams))
			this.requireKillerRegions.addAll(theRestThreeSplit);

		else if ("require killer item".equals(firstThreeParams) || "require killer items".equals(firstThreeParams))
			for (final String name : theRestThreeSplit) {
				// Support wildcart
				if (name.contains("*"))
					this.requireKillerItems.add(name);

				else {
					final CompMaterial material = CompMaterial.fromStringCompat(name);

					if (material != null)
						this.requireKillerItems.add(material.name());
					else
						throw new FoException("Invalid material in 'require killer item " + theRestThree + "' for death message (remove the rule if your MC version doesn't support this): " + this);
				}
			}

		else if ("require cause".equals(firstTwoParams) || "require causes".equals(firstTwoParams))
			for (final String name : theRestTwoSplit) {
				final DamageCause cause = ReflectionUtil.lookupEnum(DamageCause.class, name);

				this.requireCauses.add(cause);
			}

		else if ("require projectile".equals(firstTwoParams) || "require projectile".equals(firstTwoParams))
			for (final String name : theRestTwoSplit) {
				final EntityType type = ReflectionUtil.lookupEnumCompat(EntityType.class, name);

				if (type != null)
					this.requireProjectiles.add(type);
				else
					throw new FoException("Invalid entity in 'require projectile " + theRestTwo + "' for death message (remove the rule if your MC version doesn't support this): " + this);
			}

		else if ("require block".equals(firstTwoParams) || "require blocks".equals(firstTwoParams))
			for (final String name : theRestTwoSplit) {
				final CompMaterial type = CompMaterial.fromStringCompat(name);

				if (type != null)
					this.requireBlocks.add(type);
				else
					throw new FoException("Invalid block in 'require block " + theRestTwo + "' for death message (remove the rule if your MC version doesn't support this): " + this);
			}

		else if ("require killer".equals(firstTwoParams) || "require killers".equals(firstTwoParams))
			for (final String name : theRestTwoSplit) {
				final EntityType type = ReflectionUtil.lookupEnumCompat(EntityType.class, name);

				if (type != null)
					this.requireKillers.add(type);
				else
					throw new FoException("Invalid entity in 'require killer " + theRestTwo + "' for death message (remove the rule if your MC version doesn't support this): " + this);
			}

		else if ("require boss".equals(firstTwoParams) || "require bosses".equals(firstTwoParams))
			this.requireBosses = new IsInList<>(theRestTwoSplit);

		else if ("require damage".equals(firstTwoParams)) {
			checkNotSet(this.requireDamage, "require damage");
			Valid.checkBoolean(Valid.isDecimal(theRestTwo), "Wrong 'require damage' syntax, type only a whole decimal number such as 2.0 or 5.5");

			this.requireDamage = Double.parseDouble(theRestTwo);
		}

		else if ("ignore killer perm".equals(firstThreeParams) || "ignore killer permission".equals(firstThreeParams)) {
			checkNotSet(this.ignoreKillerPermission, "ignore sender perm");

			this.ignoreKillerPermission = theRestThree;
		}

		else if ("ignore killer script".equals(firstThreeParams)) {
			checkNotSet(this.ignoreKillerScript, "ignore sender script");

			this.ignoreKillerScript = theRestThree;
		}

		else if ("ignore killer gamemode".equals(firstThreeParams) || "ignore killer gamemodes".equals(firstThreeParams))
			for (final String modeName : theRestThreeSplit) {
				final GameMode gameMode = ReflectionUtil.lookupEnum(GameMode.class, modeName);

				this.ignoreKillerGamemodes.add(gameMode);
			}

		else if ("ignore killer world".equals(firstThreeParams) || "ignore killer worlds".equals(firstThreeParams))
			this.ignoreKillerWorlds.addAll(theRestThreeSplit);

		else if ("ignore killer region".equals(firstThreeParams) || "ignore killer regions".equals(firstThreeParams))
			this.ignoreKillerRegions.addAll(theRestThreeSplit);

		else if ("ignore killer item".equals(firstThreeParams) || "ignore killer items".equals(firstThreeParams))
			for (final String name : theRestThreeSplit) {

				// Support wildcart
				if (name.contains("*"))
					this.ignoreKillerItems.add(name);

				else {
					final CompMaterial material = CompMaterial.fromStringCompat(name);

					if (material != null)
						this.ignoreKillerItems.add(material.name());
					else
						throw new FoException("Invalid material in 'ignore killer item " + theRestThree + "' for death message (remove the rule if your MC version doesn't support this): " + this);
				}
			}

		else if ("ignore killer channel".equals(firstThreeParams) || "ignore killer channels".equals(firstThreeParams))
			this.ignoreKillerChannels.addAll(theRestThreeSplit);

		else
			return super.onParse(firstThreeParams, theRestThree, args);

		return true;
	}

	/**
	 * Collect all options we have to debug
	 *
	 * @return
	 */
	@Override
	protected SerializedMap collectOptions() {
		return super.collectOptions().putArray(
				"Require Killer Permission", this.requireKillerPermission,
				"Require Killer Script", this.requireKillerScript,
				"Require Killer Worlds", this.requireKillerWorlds,
				"Require Killer Regions", this.requireKillerRegions,
				"Require Killer Items", this.requireKillerItems,
				"Require Causes", this.requireCauses,
				"Require Projectiles", this.requireProjectiles,
				"Require Blocks", this.requireBlocks,
				"Require Killers", this.requireKillers,
				"Require Bosses", this.requireBosses,
				"Require Damage", this.requireDamage,
				"Ignore Killer Permission", this.ignoreKillerPermission,
				"Ignore Killer Script", this.ignoreKillerScript,
				"Ignore Killer Gamemodes", this.ignoreKillerGamemodes,
				"Ignore Killer Worlds", this.ignoreKillerWorlds,
				"Ignore Killer Regions", this.ignoreKillerRegions,
				"Ignore Killer Items", this.ignoreKillerItems,
				"Ignore Killer Channels", this.ignoreKillerChannels

		);
	}

	/* ------------------------------------------------------------------------------- */
	/* Classes */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Represents a check that is implemented by this class
	 */
	public static final class DeathMessageCheck extends PlayerMessageCheck<DeathMessage> {

		/**
		 * The killer entity
		 */
		@Nullable
		private final Entity killer;

		/**
		 * If the killer is man?
		 */
		private final boolean killerIsDude;

		/**
		 * The killer type or null
		 */
		@Nullable
		private EntityType killerType;

		/**
		 * The killer item in his hands or null
		 */
		@Nullable
		private CompMaterial killerItem;

		/**
		 * Get the damage cause
		 */
		private DamageCause damageCause;

		/**
		 * The projectile or null
		 */
		@Nullable
		private EntityType projectile;

		/**
		 * The block type or null
		 */
		@Nullable
		private CompMaterial blockType;

		/**
		 * If player died by a Boss this is his name
		 */
		@Nullable
		private String bossName = "";

		/**
		 * Create a new death message
		 *
		 * @param player
		 * @param originalMessage
		 */
		protected DeathMessageCheck(Player player, String originalMessage) {
			super(Type.DEATH, player, originalMessage);

			this.killer = player.getKiller() != null ? player.getKiller() : player.getLastDamageCause() instanceof EntityDamageByEntityEvent ? ((EntityDamageByEntityEvent) player.getLastDamageCause()).getDamager() : null;
			this.killerIsDude = this.killer instanceof Player;
		}

		/**
		 * @see org.mineacademy.chatcontrol.operator.PlayerMessage.PlayerMessageCheck#canFilter(org.mineacademy.chatcontrol.operator.PlayerMessage)
		 */
		@Override
		protected boolean canFilter(DeathMessage operator) {

			// ----------------------------------------------------------------
			// Prepare variables we use later
			// ----------------------------------------------------------------

			final Player killer = player.getKiller();
			final EntityDamageEvent lastDamageCause = player.getLastDamageCause();

			if (lastDamageCause == null) {
				Common.log("Warning: Unexpected null last damage cause for " + player.getName() + ", messages not broadcasted.");

				return false;
			}

			damageCause = lastDamageCause.getCause();

			if (lastDamageCause instanceof EntityDamageByEntityEvent) {
				final EntityDamageByEntityEvent event = (EntityDamageByEntityEvent) lastDamageCause;
				final Entity damager = event.getDamager();

				this.killerType = damager.getType();

				if (damager instanceof LivingEntity) {
					final ItemStack hand = ((LivingEntity) damager).getEquipment().getItemInHand();

					this.killerItem = CompMaterial.fromItem(hand);

					if (this.killerItem == null)
						this.killerItem = CompMaterial.fromStringCompat(hand.getType().name());
				}

				if (HookManager.isBossLoaded())
					this.bossName = HookManager.getBossName(damager);

				if ((this.bossName == null || this.bossName.isEmpty()) && HookManager.isMythicMobsLoaded())
					this.bossName = HookManager.getMythicMobName(damager);

				if (damager instanceof Projectile) {
					final ProjectileSource shooter = ((Projectile) damager).getShooter();

					this.projectile = damager.getType();

					if (shooter instanceof BlockProjectileSource) {
						final Block block = ((BlockProjectileSource) shooter).getBlock();

						this.blockType = CompMaterial.fromBlock(block);

					} else if (shooter instanceof LivingEntity) {
						final LivingEntity entity = (LivingEntity) shooter;

						this.killerType = entity.getType();
					}
				}
			}

			else if (lastDamageCause instanceof EntityDamageByBlockEvent) {
				final EntityDamageByBlockEvent event = (EntityDamageByBlockEvent) lastDamageCause;
				final Block block = event.getDamager();

				if (block != null)
					blockType = CompMaterial.fromBlock(block);
			}

			Debugger.debug("operator", "Cause: " + lastDamageCause.getCause() + ", Killer: " + killerType + ", Projectile: " + projectile + ", Block: " + blockType + ", Boss: " + bossName);

			// ----------------------------------------------------------------
			// Check for specific death require conditions
			// ----------------------------------------------------------------

			if (!operator.getRequireCauses().isEmpty() && !operator.getRequireCauses().contains(lastDamageCause.getCause()))
				return false;

			if (!operator.getRequireProjectiles().isEmpty() && (this.projectile == null || !operator.getRequireProjectiles().contains(this.projectile)))
				return false;

			if (!operator.getRequireBlocks().isEmpty() && (this.blockType == null || !operator.getRequireBlocks().contains(this.blockType)))
				return false;

			if (!operator.getRequireKillers().isEmpty() && (this.killerType == null || !operator.getRequireKillers().contains(this.killerType)))
				return false;

			if (operator.getRequireBosses() != null && !HookManager.isBossLoaded() && !HookManager.isMythicMobsLoaded())
				return false;

			if (operator.getRequireBosses() != null && (this.bossName == null || "".equals(this.bossName)))
				return false;

			if (operator.getRequireBosses() != null && !operator.getRequireBosses().contains(this.bossName))
				return false;

			if (operator.getRequireDamage() != null && Remain.getFinalDamage(lastDamageCause) < operator.getRequireDamage())
				return false;

			if (killer != null) {

				// ----------------------------------------------------------------
				// Require
				// ----------------------------------------------------------------

				if (this.killerIsDude && operator.getRequireKillerPermission() != null) {
					final String permission = operator.getRequireKillerPermission().getKey();
					final String noPermissionMessage = operator.getRequireKillerPermission().getValue();

					if (!PlayerUtil.hasPerm(killer, replaceVariables(permission, operator))) {
						if (noPermissionMessage != null) {
							Common.tell(this.receiver, replaceVariables(noPermissionMessage, operator));

							throw new EventHandledException(true);
						}

						Debugger.debug("operator", "\tno required killer permission");
						return false;
					}
				}

				if (operator.getRequireKillerScript() != null) {
					final Object result = JavaScriptExecutor.run(replaceVariables(operator.getRequireKillerScript(), operator), SerializedMap.ofArray("player", this.sender, "killer", killer).asMap());

					if (result != null) {
						Valid.checkBoolean(result instanceof Boolean, "require killer condition must return boolean not " + (result == null ? "null" : result.getClass()) + " for rule " + operator);

						if ((boolean) result == false) {
							Debugger.debug("operator", "\tno required killer script");

							return false;
						}
					}
				}

				if (!operator.getRequireKillerWorlds().isEmpty() && !Valid.isInList(killer.getWorld().getName(), operator.getRequireKillerWorlds())) {
					Debugger.debug("operator", "\tno required killer worlds");

					return false;
				}

				if (!operator.getRequireKillerRegions().isEmpty()) {
					final List<String> regions = Common.convert(ServerCache.getInstance().findRegions(killer.getLocation()), VisualizedRegion::getName);
					boolean found = false;

					for (final String requireRegionName : operator.getRequireKillerRegions())
						if (regions.contains(requireRegionName)) {
							found = true;

							break;
						}

					if (!found) {
						Debugger.debug("operator", "\tno required killer regions");

						return false;
					}
				}

				if (!operator.getRequireKillerItems().isEmpty()) {
					boolean found = false;

					if (this.killerItem == null)
						return false;

					final String killerItemName = this.killerItem.name().toLowerCase();

					for (final String requiredItem : operator.getRequireKillerItems()) {
						final String starless = requiredItem.replace("*", "").toLowerCase();

						final boolean matches = requiredItem.startsWith("*") ? killerItemName.endsWith(starless)
								: requiredItem.endsWith("*") ? killerItemName.startsWith(starless)
										: this.killerItem == CompMaterial.valueOf(requiredItem);

						if (matches) {
							found = true;

							break;
						}
					}

					if (!found)
						return false;
				}

				// ----------------------------------------------------------------
				// Ignore
				// ----------------------------------------------------------------

				if (killerIsDude && operator.getIgnoreKillerPermission() != null && PlayerUtil.hasPerm(killer, replaceVariables(operator.getIgnoreKillerPermission(), operator))) {
					Debugger.debug("operator", "\tignore killer permission found");

					return false;
				}

				if (operator.getIgnoreKillerScript() != null) {
					final Object result = JavaScriptExecutor.run(replaceVariables(operator.getIgnoreKillerScript(), operator), SerializedMap.ofArray("player", this.sender, "killer", killer).asMap());

					if (result != null) {
						Valid.checkBoolean(result instanceof Boolean, "ignore killer script condition must return boolean not " + (result == null ? "null" : result.getClass()) + " for rule " + operator);

						if ((boolean) result == true) {
							Debugger.debug("operator", "\tignore killer script found");

							return false;
						}
					}
				}

				if (killerIsDude && operator.getIgnoreKillerGamemodes().contains(killer.getGameMode())) {
					Debugger.debug("operator", "\tignore killer gamemodes found");

					return false;
				}

				if (operator.getIgnoreKillerWorlds().contains(killer.getWorld().getName())) {
					Debugger.debug("operator", "\tignore killer worlds found");

					return false;
				}

				for (final String playersRegion : Common.convert(ServerCache.getInstance().findRegions(killer.getLocation()), VisualizedRegion::getName))
					if (operator.getIgnoreKillerRegions().contains(playersRegion)) {
						Debugger.debug("operator", "\tignore killer regions found");

						return false;
					}

				if (this.killerItem != null) {
					final String killerItemName = this.killerItem.name().toLowerCase();

					for (final String ignoredItem : operator.getIgnoreKillerItems()) {
						final String starless = ignoredItem.replace("*", "").toLowerCase();

						final boolean matches = ignoredItem.startsWith("*") ? killerItemName.endsWith(starless)
								: ignoredItem.endsWith("*") ? killerItemName.startsWith(starless) : this.killerItem == CompMaterial.valueOf(ignoredItem);

						if (matches)
							return false;
					}
				}

			} // end killer != null

			return super.canFilter(operator);
		}

		/**
		 * @see org.mineacademy.chatcontrol.operator.PlayerMessage.PlayerMessageCheck#prepareVariables(org.mineacademy.chatcontrol.operator.PlayerMessage)
		 */
		@Override
		protected SerializedMap prepareVariables(DeathMessage operator) {
			return super.prepareVariables(operator).putArray(
					"cause", damageCause.toString().toLowerCase().replace("_", " "),
					"killer", killer == null ? "" : this.getKillerName(),
					"projectile", projectile == null ? "" : projectile.name(),
					"block_type", blockType == null ? "" : ItemUtil.bountifyCapitalized(blockType),
					"killer_type", killerType == null ? "" : ItemUtil.bountifyCapitalized(killerType),
					"boss_name", Common.getOrDefault(bossName, ""));
		}

		/*
		 * Get the properly formatted killer name for the death message
		 */
		private String getKillerName() {
			if (this.killer instanceof Player)
				return ((Player) this.killer).getName();

			final String fallback = ItemUtil.bountifyCapitalized(this.killerType);

			try {
				final String name = this.killer.isCustomNameVisible() ? this.killer.getCustomName() : this.killer.getName();

				// Return the custom name or fallback in case the name contains known health plugin letters
				return name.contains("♡") || name.contains("♥") || name.contains("❤") || name.contains("■") ? fallback : name;

			} catch (final Error err) {
				// MC version incompatible call for Entity#getName
				return fallback;
			}
		}

		/**
		 * @see org.mineacademy.chatcontrol.operator.PlayerMessage.PlayerMessageCheck#getMessagePlayerForVariables()
		 */
		@Override
		protected CommandSender getMessagePlayerForVariables() {
			return this.killer instanceof Player ? (Player) killer : this.player;
		}

		/**
		 * @see org.mineacademy.chatcontrol.operator.Operator.OperatorCheck#getOperators()
		 */
		@Override
		public List<DeathMessage> getOperators() {
			return PlayerMessages.getInstance().getMessages(Type.DEATH);
		}
	}
}
