package org.mineacademy.chatcontrol.command;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.mineacademy.chatcontrol.SenderCache;
import org.mineacademy.chatcontrol.ServerCache;
import org.mineacademy.chatcontrol.command.ChatControlCommands.ChatControlSubCommand;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.RegionTool;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.fo.ChatUtil;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.region.Region;
import org.mineacademy.fo.remain.CompMaterial;
import org.mineacademy.fo.remain.CompSound;
import org.mineacademy.fo.visual.VisualizedRegion;

/**
 * Represents creation of region that can be used in rules etc.
 */
public final class ChatControlRegion extends ChatControlSubCommand {

	public ChatControlRegion() {
		super("region/rg");

		setUsage(Lang.of("Commands.Region.Usage"));
		setDescription(Lang.of("Commands.Region.Description"));
		setMinArguments(1);
		setPermission(Permissions.Command.REGION);
	}

	@Override
	protected String[] getMultilineUsageMessage() {
		return Lang.ofArray("Commands.Region.Usages");
	}

	@Override
	protected void execute() {
		checkConsole();

		final ServerCache serverCache = ServerCache.getInstance();
		final SenderCache cache = SenderCache.from(sender);

		final String param = args[0];
		final String name = args.length > 1 ? args[1] : null;

		if ("list".equals(param)) {
			tellNoPrefix(Lang
					.of("Commands.Region.List", serverCache.getRegions().size())
					.replace("{chat_line_smooth}", Common.chatLineSmooth()));

			for (final Region region : serverCache.getRegions()) {
				final String regionName = region.getName();
				final String longestText = Lang.of("Commands.Region.Secondary", Common.shortLocation(region.getSecondary()));

				SimpleComponent
						.of(" ")
						.append("&8[&4X&8]")
						.onHover(Lang.of("Commands.Region.Tooltip_Remove"))
						.onClickRunCmd("/" + getLabel() + " " + getSublabel() + " remove " + regionName + " -list")

						.append(" ")
						.append("&8[&2?&8]")
						.onHover(Lang.of("Commands.Region.Tooltip_Visualize"))
						.onClickRunCmd("/" + getLabel() + " " + getSublabel() + " view " + regionName + " -list")

						.append(" ")
						.append("&8[&3>&8]")
						.onHover(Lang.of("Commands.Region.Tooltip_Teleport"))
						.onClickRunCmd("/" + getLabel() + " " + getSublabel() + " tp " + regionName + " -list")

						.append(" ")
						.append("&7" + regionName)
						.onHover(
								ChatUtil.center(Lang.of("Commands.Region.Region_Info"), longestText.length() * 2 + longestText.length() / 3),
								Lang.of("Commands.Region.Primary", Common.shortLocation(region.getPrimary())),
								Lang.of("Commands.Region.Secondary", longestText),
								Lang.of("Commands.Region.Size", region.getBlocks().size()))

						.send(sender);
			}

			return;
		}

		else if ("tool".equals(param)) {
			RegionTool.getInstance().give(getPlayer());
			CompSound.AMBIENCE_THUNDER.play(getPlayer());

			tellSuccess(Lang.of("Commands.Region.Tool"));
			return;
		}

		checkNotNull(name, Lang.of("Commands.Region.No_Name"));

		if ("add".equals(param)) {
			final Region oldRegion = serverCache.findRegion(name);
			final VisualizedRegion createdRegion = cache.getCreatedRegion();

			checkBoolean(createdRegion != null && createdRegion.isWhole(), Lang.of("Commands.Region.No_Selection"));
			checkBoolean(oldRegion == null, Lang.of("Commands.Region.Already_Exists"));

			createdRegion.setName(name);
			serverCache.addRegion(createdRegion);

			cache.setCreatedRegion(Region.EMPTY);
			tellSuccess(Lang.of("Commands.Region.Saved", name));
			return;
		}

		final VisualizedRegion region = serverCache.findRegion(name);
		checkNotNull(region, Lang.of("Commands.Invalid_Region"));

		if ("remove".equals(param)) {
			serverCache.removeRegion(name);

			tellSuccess(Lang.of("Commands.Region.Removed", name));
		}

		else if ("view".equals(param)) {
			if (!region.canSeeParticles(getPlayer()))
				region.showParticles(getPlayer(), 5 * 20);

			tellSuccess(Lang.of("Commands.Region.Visualized", name));
		}

		else if ("tp".equals(param)) {
			getPlayer().teleport(getSuitableTeleportLocation(region));

			tellSuccess(Lang.of("Commands.Region.Teleported", name));
		}

		else
			returnInvalidArgs();
	}

	/*
	 * Return a suitable region center location that will not suffocate the player
	 */
	private Location getSuitableTeleportLocation(final Region region) {
		final Location regionCenter = region.getCenter().clone();

		while (regionCenter.getY() < regionCenter.getWorld().getMaxHeight() && !CompMaterial.isAir(regionCenter.getBlock()) && !CompMaterial.isAir(regionCenter.getBlock().getRelative(BlockFace.UP)))
			regionCenter.add(0, 1, 0);

		return regionCenter;
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	public List<String> tabComplete() {

		if (args.length == 1)
			return completeLastWord("add", "remove", "view", "tp", "tool", "list");

		if (args.length == 2)
			return completeLastWord(ServerCache.getInstance().getRegions(), VisualizedRegion::getName);

		return NO_COMPLETE;
	}
}