package org.mineacademy.chatcontrol.command;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.mineacademy.chatcontrol.command.ChatControlCommands.ChatControlSubCommand;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.FileUtil;
import org.mineacademy.fo.ReflectionUtil;
import org.mineacademy.fo.ReflectionUtil.ReflectionException;
import org.mineacademy.fo.model.ChatPaginator;
import org.mineacademy.fo.model.SimpleComponent;

import lombok.NonNull;

public final class ChatControlInspect extends ChatControlSubCommand {

	public ChatControlInspect() {
		super("inspect/i");

		setUsage(Lang.of("Commands.Inspect.Usage"));
		setDescription(Lang.of("Commands.Inspect.Description"));
		setMinArguments(2);
		setPermission(Permissions.Command.INSPECT);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected String[] getMultilineUsageMessage() {
		return Lang.ofArray("Commands.Inspect.Usages");
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void execute() {
		checkUsage(args.length < 3);

		final String param = args[0];
		final String value = Common.joinRange(1, args);

		// The pages that are filled below
		final List<SimpleComponent> components = new ArrayList<>();

		// Header for convenience
		String header = null;

		// List file content or folder
		if ("file".equals(param)) {
			final File file = new File(value);
			final String name = file.getName();

			checkBoolean(!name.endsWith("mca") && !name.endsWith("jar") && !name.endsWith("dat"), Lang.of("Commands.Inspect.Illegal_File", file.getPath()));
			checkBoolean(file.exists(), Lang.of("Commands.Inspect.Invalid_File", file.getPath()));

			if (file.isDirectory()) {
				header = Lang.of("Commands.Inspect.Listing_Content", file.getName());

				listFiles(file, components, 0);
			}

			else {
				header = Lang.of("Commands.Inspect.Listing_File", file.getName());

				final List<String> lines = FileUtil.readLines(file);
				int lineCount = 1;

				for (String line : lines) {
					line = line.replace("\t", "  ");
					line = "&7" + lineCount++ + ": &f" + line.replace("{player}", sender.getName());

					final SimpleComponent component = SimpleComponent.empty();

					if (lineCount > 2000) {
						components.add(Lang.ofComponent("Commands.Inspect.Cut", lines.size() - lineCount));

						break;
					}

					appendAndTrim(line, component);
					components.add(component);
				}
			}
		}

		// List plugin classes
		else if ("plugin".equals(param)) {
			final Plugin plugin = Bukkit.getPluginManager().getPlugin(value);
			checkNotNull(plugin, Lang.of("Commands.Inspect.Plugin_Not_Loaded", value));

			header = Lang.of("Commands.Inspect.Listing_Classes", plugin.getDescription().getFullName());

			for (final Class<?> clazz : ReflectionUtil.getClasses(plugin)) {
				final String path = clazz.getName();
				final SimpleComponent component = SimpleComponent.empty();

				appendAndTrim("&7- " + path, component);

				component
						.onHover(Lang.ofArray("Commands.Inspect.Plugin_Tooltip"))
						.onClickRunCmd("/" + getLabel() + " " + getSublabel() + " class " + path);

				components.add(component);
			}
		}

		// List class fields/methods
		else if ("class".equals(param)) {
			Class<?> clazz = null;

			try {
				clazz = ReflectionUtil.lookupClass(value);

			} catch (final ReflectionException ex) {
			}

			checkNotNull(clazz, Lang.of("Commands.Inspect.No_Class", value));

			header = Lang.of("Commands.Inspect.Inspecting_Class", clazz.getSimpleName());

			// Collect fields
			for (final Field field : ReflectionUtil.getAllFields(clazz)) {
				final SimpleComponent component = SimpleComponent.of("");
				final int mod = field.getModifiers();

				addModifiers(component, mod);

				final Class<?> type = field.getType();
				component.append("&7" + type.getSimpleName() + " ");

				if (!type.isPrimitive())
					component
							.onHover(Lang.ofArray("Commands.Inspect.Inspect_Tooltip", type.getName()))
							.onClickRunCmd("/" + getLabel() + " " + getSublabel() + " class " + type.getName());

				appendAndTrim("&f" + field.getName(), component);

				if (!field.isEnumConstant() && Modifier.isStatic(mod))
					try {
						field.setAccessible(true);

						final Object fieldValue = field.get(null);
						appendAndTrim(" &7= &f" + fieldValue, component);

						if (fieldValue != null)
							component
									.onHover(Lang.ofArray("Commands.Inspect.Inspect_Tooltip", fieldValue.getClass().toString()))
									.onClickRunCmd("/" + getLabel() + " " + getSublabel() + " class " + fieldValue.getClass().getName());

					} catch (final ReflectiveOperationException e) {
						// hide
					}

				component.append("&7;");
				components.add(component);
			}

			// Collect methods
			for (final Method method : clazz.getDeclaredMethods()) {
				final SimpleComponent component = SimpleComponent.of("");
				final int mod = method.getModifiers();

				addModifiers(component, mod);

				final Class<?> type = method.getReturnType();
				component.append("&7" + type.getSimpleName() + " ");

				if (!type.isPrimitive() && type != Void.TYPE)
					component
							.onHover(Lang.ofArray("Commands.Inspect.Inspect_Tooltip", type.getName()))
							.onClickRunCmd("/" + getLabel() + " " + getSublabel() + " class " + type.getName());

				appendAndTrim("&f" + method.getName() + "(" + Common.join(method.getParameters(), ", ", parameter -> parameter.getType().getSimpleName()) + ")", component);

				if (Modifier.isStatic(mod) && method.getParameterCount() == 0 && type != Void.TYPE)
					try {
						appendAndTrim("&7 -> &f" + method.invoke(null), component);
					} catch (final ReflectiveOperationException ex) {
						// Silence
					}

				components.add(component);
			}
		}

		// Check for invalid arguments
		if (header == null)
			returnInvalidArgs();

		// Compile pages and send
		final ChatPaginator pages = new ChatPaginator(15);

		pages.setFoundationHeader(header);
		pages.setPages(components);
		pages.send(sender);
	}

	/*
	 * Paint field/method with its modifiers
	 */
	private void addModifiers(SimpleComponent name, int modifiers) {
		if (Modifier.isPrivate(modifiers))
			name.append("&6private ");

		if (Modifier.isProtected(modifiers))
			name.append("&6protected ");

		if (Modifier.isPublic(modifiers))
			name.append("&6protected ");

		if (Modifier.isAbstract(modifiers))
			name.append("&3abstract ");

		if (Modifier.isStatic(modifiers))
			name.append("&4static ");

		if (Modifier.isFinal(modifiers))
			name.append("&5final ");

		if (Modifier.isInterface(modifiers))
			name.append("&5interface ");

		if (Modifier.isNative(modifiers))
			name.append("&dnative ");

		if (Modifier.isSynchronized(modifiers))
			name.append("&dsynchronized ");

		if (Modifier.isVolatile(modifiers))
			name.append("&dvolatile ");
	}

	/*
	 * Append text to the component respecting the maximum line width, 58 letters
	 */
	private void appendAndTrim(String line, @NonNull SimpleComponent currentComponent) {
		final int limit = 58 - Common.stripColors(currentComponent.getPlainMessage()).length();
		final boolean overlimit = line.length() > limit;

		final SimpleComponent component = SimpleComponent.of(overlimit ? line.substring(0, limit) + ".." : line);

		if (overlimit)
			component.onHover(line.substring(limit, line.length() > 250 ? 250 : line.length()));

		currentComponent.append(component);
	}

	/*
	 * List files recursively
	 */
	private List<SimpleComponent> listFiles(File path, List<SimpleComponent> list, int indent) {
		final File parent = path.getParentFile();

		// Add parent
		if (!path.getPath().equals(".")) {

			final SimpleComponent component = Lang.ofComponent("Commands.Inspect.Parent");
			paintOpen(parent == null ? new File(".") : parent, component);

			list.add(component);
		}

		final List<File> files = Arrays.asList(path.listFiles());
		Collections.sort(files, (f, s) -> f.isDirectory() && !s.isDirectory() ? -1 : f.getName().compareTo(s.getName()));

		for (final File file : files) {
			final boolean dir = file.isDirectory();
			final SimpleComponent component = SimpleComponent.of("&7" + Common.duplicate(" &7 ", indent) + (dir ? "&2" : "") + file.getName() + (dir ? "/" : ""));
			final String name = file.getName();

			if (!name.endsWith(".jar") && !name.endsWith(".mca") && !name.endsWith(".dat"))
				paintOpen(file, component);

			list.add(component);
		}

		return list;
	}

	/*
	 * Add the hover and click even to the given file that will open it
	 */
	private void paintOpen(File file, SimpleComponent component) {
		final String openPath = file.getPath().replace("\\", "/");

		component
				.onHover(Lang.ofArray("Commands.Inspect.Open", openPath))
				.onClickRunCmd("/" + getLabel() + " inspect file " + openPath);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {
		if (args.length == 1)
			return completeLastWord("file", "plugin", "class");

		if (args.length == 2) {
			if (args[0].equals("plugin"))
				return completeLastWord(Common.convert(Bukkit.getPluginManager().getPlugins(), Plugin::getName));

			else if (args[0].equals("file"))
				try {
					return completeLastWord(Common.convert(new File(args[1].isEmpty() ? "." : args[1]).listFiles(), File::getPath));
				} catch (final NullPointerException ex) {
					// pass through
				}
		}

		return NO_COMPLETE;
	}
}
