package com.citrusmc.anvilwatch;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

import org.bukkit.Bukkit;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.MultiLineChart;

public class AnvilWatch extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private static final int RECENT_ENTRIES_PER_PAGE = 10;
    private static final String DEFAULT_PLAYER_BLOCKED_MESSAGE = "<red>You cannot use that word in item names.</red>";
    private static final String DEFAULT_ADMIN_RENAME_MESSAGE = "<yellow><player></yellow> renamed <aqua><item></aqua> from '<red><old></red>' to '<green><new></green>'";
    private static final String DEFAULT_ADMIN_BLOCKED_MESSAGE = "<yellow><player></yellow> attempted to use a blocked name on <aqua><item></aqua>: '<red><new></red>'";
    private static final Pattern RECENT_LOG_PATTERN = Pattern.compile(
            "^\\[([^\\]]+)] Player: (.*?) \\(UUID: ([^)]+)\\) (.*?) \\(([^)]+)\\) from '(.*)' to '(.*)'$");

    private File logFile;
    private File blockedLogFile;
    private File bannedWordsFile;
    private File dataFile;
    private YamlConfiguration dataConfiguration;
    private final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private volatile List<Pattern> bannedWords = List.of();
    private final Set<UUID> logDisabledAdmins = new HashSet<>();
    private final Queue<RecentLogEntry> pendingLogEntries = new ConcurrentLinkedQueue<>();
    private final AtomicLong logSequence = new AtomicLong();
    private final Object logWriteLock = new Object();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<Character, Character> homoglyphs = new HashMap<>();
    private final Map<Character, String> leetSpeakMap = new HashMap<>();

    private record RecentLogEntry(String line, LocalDateTime timestamp, long sequence, boolean blocked) {
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadAdminPreferences();
        initializeLogFiles();
        loadBannedWords();
        loadHomoglyphs();
        loadLeetSpeakMap();
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("anvilwatch")).setExecutor(this);
        Objects.requireNonNull(getCommand("anvilwatch")).setTabCompleter(this);

        int pluginId = 31133;
        Metrics metrics = new Metrics(this, pluginId);
        metrics.addCustomChart(new MultiLineChart("players_and_servers", () -> {
            Map<String, Integer> valueMap = new HashMap<>();
            valueMap.put("servers", 1);
            valueMap.put("players", Bukkit.getOnlinePlayers().size());
            return valueMap;
        }));

        getLogger().info("AnvilWatch plugin enabled.");
    }

    @Override
    public void onDisable() {
        saveAdminPreferences();
        getLogger().info("AnvilWatch plugin disabled.");
    }

    private void loadHomoglyphs() {
        // Cyrillic
        homoglyphs.put('а', 'a');
        homoglyphs.put('в', 'b');
        homoglyphs.put('с', 'c');
        homoglyphs.put('е', 'e');
        homoglyphs.put('н', 'h');
        homoglyphs.put('і', 'i');
        homoglyphs.put('ј', 'j');
        homoglyphs.put('к', 'k');
        homoglyphs.put('м', 'm');
        homoglyphs.put('о', 'o');
        homoglyphs.put('р', 'p');
        homoglyphs.put('ѕ', 's');
        homoglyphs.put('т', 't');
        homoglyphs.put('х', 'x');
        homoglyphs.put('у', 'y');
        // Latin
        homoglyphs.put('µ', 'u');
        // Greek
        homoglyphs.put('α', 'a');
        homoglyphs.put('β', 'b');
        homoglyphs.put('ε', 'e');
        homoglyphs.put('ι', 'i');
        homoglyphs.put('κ', 'k');
        homoglyphs.put('ο', 'o');
        homoglyphs.put('ρ', 'p');
        homoglyphs.put('τ', 't');
        homoglyphs.put('υ', 'u');
        homoglyphs.put('χ', 'x');
        // Other
        homoglyphs.put('１', '1');
        homoglyphs.put('２', '2');
        homoglyphs.put('３', '3');
        homoglyphs.put('４', '4');
        homoglyphs.put('５', '5');
        homoglyphs.put('６', '6');
        homoglyphs.put('７', '7');
        homoglyphs.put('８', '8');
        homoglyphs.put('９', '9');
        homoglyphs.put('０', '0');
    }

    private void loadLeetSpeakMap() {
        leetSpeakMap.put('@', "a");
        leetSpeakMap.put('4', "a");
        leetSpeakMap.put('8', "b");
        leetSpeakMap.put('(', "c");
        leetSpeakMap.put('[', "c");
        leetSpeakMap.put('<', "c");
        leetSpeakMap.put('3', "e");
        leetSpeakMap.put('9', "g");
        leetSpeakMap.put('6', "g");
        leetSpeakMap.put('!', "i");
        leetSpeakMap.put('1', "i");
        leetSpeakMap.put('|', "l");
        leetSpeakMap.put('0', "o");
        leetSpeakMap.put('$', "s");
        leetSpeakMap.put('5', "s");
        leetSpeakMap.put('7', "t");
        leetSpeakMap.put('+', "t");
        leetSpeakMap.put('2', "z");
    }

    private String normalizeName(String name) {
        StringBuilder normalized = new StringBuilder();
        for (char c : name.toLowerCase(Locale.ROOT).toCharArray()) {
            if (homoglyphs.containsKey(c)) {
                normalized.append(homoglyphs.get(c));
            } else if (leetSpeakMap.containsKey(c)) {
                normalized.append(leetSpeakMap.get(c));
            } else {
                normalized.append(c);
            }
        }
        return normalized.toString().replaceAll("[^a-zA-Z0-9]", "");
    }

    private String getPlainCustomName(ItemMeta itemMeta) {
        if (itemMeta == null || !itemMeta.hasCustomName()) {
            return null;
        }

        Component customName = itemMeta.customName();
        return customName == null
                ? null
                : PlainTextComponentSerializer.plainText().serialize(customName);
    }

    private void initializeLogFiles() {
        logFile = createConfiguredLogFile("logging.rename-file", "logs/anvil_renames.log");
        blockedLogFile = createConfiguredLogFile("logging.blocked-file", "logs/blocked_renames.log");
    }

    private File createConfiguredLogFile(String configPath, String defaultPath) {
        String configuredPath = getConfig().getString(configPath, defaultPath);
        if (configuredPath == null || configuredPath.isBlank()) {
            configuredPath = defaultPath;
        }

        File file = new File(configuredPath.trim());
        if (!file.isAbsolute()) {
            file = new File(getDataFolder(), configuredPath.trim());
        }

        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
                getLogger().log(Level.SEVERE, "Failed to create log directory: " + parent.getAbsolutePath());
                return null;
            }
            if (!file.exists() && !file.createNewFile()) {
                getLogger().log(Level.WARNING, "Log file was not created: " + file.getAbsolutePath());
            }
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed to create log file: " + file.getAbsolutePath(), e);
            return null;
        } catch (SecurityException e) {
            getLogger().log(Level.SEVERE, "Security exception while creating log file: " + file.getAbsolutePath(), e);
            return null;
        }

        if (!file.isFile() || !file.canWrite()) {
            getLogger().log(Level.SEVERE, "Log file is not usable (does not exist or is not writable): " + file.getAbsolutePath());
            return null;
        }

        getLogger().info("Using log file: " + file.getAbsolutePath());
        return file;
    }

    private void loadAdminPreferences() {
        dataFile = new File(getDataFolder(), "data.yml");
        dataConfiguration = YamlConfiguration.loadConfiguration(dataFile);
        logDisabledAdmins.clear();

        for (String value : dataConfiguration.getStringList("log-disabled-admins")) {
            try {
                logDisabledAdmins.add(UUID.fromString(value));
            } catch (IllegalArgumentException e) {
                getLogger().warning("Ignoring invalid UUID in data.yml: " + value);
            }
        }

        if (!dataFile.exists()) {
            saveAdminPreferences();
        }
    }

    private boolean saveAdminPreferences() {
        if (dataConfiguration == null || dataFile == null) {
            return false;
        }

        dataConfiguration.set("log-disabled-admins", logDisabledAdmins.stream()
                .map(UUID::toString)
                .sorted()
                .toList());
        try {
            dataConfiguration.save(dataFile);
            return true;
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed to save admin notification preferences: " + dataFile.getAbsolutePath(), e);
            return false;
        }
    }

    private void loadBannedWords() {
        List<Pattern> loadedBannedWords = new ArrayList<>();
        bannedWordsFile = new File(getDataFolder(), "BannedWords.txt");
        if (!bannedWordsFile.exists()) {
            try {
                if (bannedWordsFile.createNewFile()) {
                    getLogger().info("Created BannedWords.txt: " + bannedWordsFile.getAbsolutePath());
                    try (FileWriter writer = new FileWriter(bannedWordsFile)) {
                        writer.write("# Add one banned word (regex pattern) per line (case-insensitive)\n");
                        writer.write("# Example for 'badword': badword\n");
                        writer.write("# Example for variations of 'badword': b[a@]dw[o0]rd\n");
                        writer.write("examplebadword\n");
                    }
                } else {
                    getLogger().log(Level.WARNING, "BannedWords.txt was not created: " + bannedWordsFile.getAbsolutePath());
                }
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Failed to create BannedWords.txt: " + bannedWordsFile.getAbsolutePath(), e);
                return;
            } catch (SecurityException e) {
                getLogger().log(Level.SEVERE, "Security exception while creating BannedWords.txt: " + bannedWordsFile.getAbsolutePath(), e);
                return;
            }
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(bannedWordsFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    try {
                        loadedBannedWords.add(Pattern.compile(line, Pattern.CASE_INSENSITIVE));
                    } catch (PatternSyntaxException e) {
                        getLogger().log(Level.SEVERE, "Invalid regex pattern in BannedWords.txt: '" + line + "'", e);
                    }
                }
            }
            bannedWords = List.copyOf(loadedBannedWords);
            getLogger().info("Loaded " + bannedWords.size() + " banned word patterns.");
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed to read BannedWords.txt: " + bannedWordsFile.getAbsolutePath(), e);
        }
    }

    private void addBannedWordAsync(CommandSender sender, String word) {
        if (word == null || word.trim().isEmpty()) {
            sender.sendMessage(Component.text("Invalid word.", NamedTextColor.RED));
            return;
        }
        String patternString = word.trim();
        try {
            Pattern.compile(patternString, Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException e) {
            sender.sendMessage(Component.text("Invalid regex pattern: '" + patternString + "'", NamedTextColor.RED));
            return;
        }

        if (bannedWords.stream().anyMatch(p -> p.pattern().equalsIgnoreCase(patternString))) {
            sender.sendMessage(Component.text("Failed to add banned word: " + word + " (already exists)", NamedTextColor.RED));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try (FileWriter writer = new FileWriter(bannedWordsFile, true)) {
                writer.write("\n" + patternString);
                Bukkit.getScheduler().runTask(this, () -> {
                    loadBannedWords(); // Reload patterns
                    getLogger().info("Added banned word pattern: " + patternString);
                    sender.sendMessage(Component.text("Added banned word: " + word, NamedTextColor.GREEN));
                });
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Failed to add banned word to BannedWords.txt: " + patternString, e);
                Bukkit.getScheduler().runTask(this, () -> sender.sendMessage(Component.text("Failed to add banned word due to an error.", NamedTextColor.RED)));
            }
        });
    }

    private void removeBannedWordAsync(CommandSender sender, String word) {
        if (word == null || word.trim().isEmpty()) {
            sender.sendMessage(Component.text("Invalid word.", NamedTextColor.RED));
            return;
        }
        String patternString = word.trim();
        if (bannedWords.stream().noneMatch(p -> p.pattern().equalsIgnoreCase(patternString))) {
            sender.sendMessage(Component.text("Failed to remove banned word: " + word + " (not found)", NamedTextColor.RED));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                List<String> lines = new ArrayList<>();
                try (BufferedReader reader = new BufferedReader(new FileReader(bannedWordsFile))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.trim().equalsIgnoreCase(patternString)) {
                            lines.add(line);
                        }
                    }
                }
                try (FileWriter writer = new FileWriter(bannedWordsFile, false)) {
                    for (String line : lines) {
                        writer.write(line + "\n");
                    }
                }
                Bukkit.getScheduler().runTask(this, () -> {
                    loadBannedWords(); // Reload patterns
                    getLogger().info("Removed banned word pattern: " + patternString);
                    sender.sendMessage(Component.text("Removed banned word: " + word, NamedTextColor.GREEN));
                });
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Failed to remove banned word from BannedWords.txt: " + patternString, e);
                Bukkit.getScheduler().runTask(this, () -> sender.sendMessage(Component.text("Failed to remove banned word due to an error.", NamedTextColor.RED)));
            }
        });
    }

    @Override
    public boolean onCommand(CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!sender.hasPermission("anvilwatch.admin")) {
            sender.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        switch (subCommand) {
            case "help" -> sendHelp(sender);
            case "reload" -> {
                reloadConfig();
                initializeLogFiles();
                loadBannedWords();
                sender.sendMessage(Component.text("AnvilWatch configuration and banned words reloaded.", NamedTextColor.GREEN));
            }
            case "add" -> {
                if (args.length != 2) {
                    sender.sendMessage(Component.text("Usage: /anvilwatch add <word>", NamedTextColor.YELLOW));
                } else {
                    addBannedWordAsync(sender, args[1]);
                }
            }
            case "remove" -> {
                if (args.length != 2) {
                    sender.sendMessage(Component.text("Usage: /anvilwatch remove <word>", NamedTextColor.YELLOW));
                } else {
                    removeBannedWordAsync(sender, args[1]);
                }
            }
            case "log" -> handleLogCommand(sender, args);
            case "check" -> handleCheckCommand(sender, args);
            case "recent" -> handleRecentCommand(sender, args);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("Usage: /anvilwatch <help|reload|add|remove|log|check|recent> <args>", NamedTextColor.YELLOW));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("AnvilWatch Commands:", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/anvilwatch help - Displays this help message", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/anvilwatch reload - Reloads configuration and banned words", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/anvilwatch add <word> - Adds a word to the banned list", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/anvilwatch remove <word> - Removes a word from the banned list", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/anvilwatch log <on|off> - Toggles in-game rename log messages for you", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/anvilwatch check <text> - Checks text against the current banned list", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/anvilwatch recent [page] - Shows recent successful and blocked rename activity", NamedTextColor.GRAY));
    }

    private void handleLogCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player) || args.length != 2) {
            sender.sendMessage(Component.text("Usage: /anvilwatch log <on|off>", NamedTextColor.YELLOW));
            return;
        }

        if (args[1].equalsIgnoreCase("on")) {
            logDisabledAdmins.remove(player.getUniqueId());
            if (saveAdminPreferences()) {
                sender.sendMessage(Component.text("In-game rename log messages enabled and saved.", NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("In-game rename log messages enabled, but the setting could not be saved.", NamedTextColor.YELLOW));
            }
        } else if (args[1].equalsIgnoreCase("off")) {
            logDisabledAdmins.add(player.getUniqueId());
            if (saveAdminPreferences()) {
                sender.sendMessage(Component.text("In-game rename log messages disabled and saved.", NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("In-game rename log messages disabled, but the setting could not be saved.", NamedTextColor.YELLOW));
            }
        } else {
            sender.sendMessage(Component.text("Usage: /anvilwatch log <on|off>", NamedTextColor.YELLOW));
        }
    }

    private void handleCheckCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /anvilwatch check <text>", NamedTextColor.YELLOW));
            return;
        }

        String text = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        if (text.isEmpty()) {
            sender.sendMessage(Component.text("The text to check cannot be empty.", NamedTextColor.RED));
            return;
        }

        Pattern matchingPattern = findMatchingPattern(text);
        if (matchingPattern == null) {
            sender.sendMessage(Component.text("This word is allowed by the current banned word list.", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("BLOCKED", NamedTextColor.RED));
            sender.sendMessage( Component.text("Matching pattern: ", NamedTextColor.GRAY) .append(Component.text(matchingPattern.pattern(), NamedTextColor.RED)) );
        }
    }

    private void handleRecentCommand(CommandSender sender, String[] args) {
        if (args.length > 2) {
            sender.sendMessage(Component.text("Usage: /anvilwatch recent [page]", NamedTextColor.YELLOW));
            return;
        }

        int page = 1;
        if (args.length == 2) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("The page must be a positive whole number.", NamedTextColor.RED));
                return;
            }
            if (page < 1) {
                sender.sendMessage(Component.text("The page must be a positive whole number.", NamedTextColor.RED));
                return;
            }
        }

        showRecentActivity(sender, page);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        if (!sender.hasPermission("anvilwatch.admin")) {
            return null;
        }

        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            return Stream.of("help", "reload", "add", "remove", "log", "check", "recent")
                    .filter(cmd -> cmd.startsWith(partial))
                    .toList();
        } else if (args.length == 2 && args[0].equalsIgnoreCase("log")) {
            String partial = args[1].toLowerCase(Locale.ROOT);
            return Stream.of("on", "off")
                    .filter(opt -> opt.startsWith(partial))
                    .toList();
        }
        return null;
    }

    private Pattern findMatchingPattern(String name) {
        if (name == null) {
            return null;
        }

        String normalizedName = normalizeName(name);
        for (Pattern pattern : bannedWords) {
            if (pattern.matcher(normalizedName).find()) {
                return pattern;
            }
        }
        return null;
    }

    private Component renderConfiguredMessage(String configPath, String fallback,
                                              String player, String item, String oldName, String newName) {
        String template = getConfig().getString(configPath, fallback);
        if (template == null || template.isBlank()) {
            return null;
        }

        try {
            return miniMessage.deserialize(template,
                    Placeholder.unparsed("player", valueOrEmpty(player)),
                    Placeholder.unparsed("item", valueOrEmpty(item)),
                    Placeholder.unparsed("old", valueOrEmpty(oldName)),
                    Placeholder.unparsed("new", valueOrEmpty(newName)));
        } catch (RuntimeException exception) {
            getLogger().log(Level.WARNING, "Invalid MiniMessage in config at '" + configPath
                    + "'. Using the default message.", exception);
            try {
                return miniMessage.deserialize(fallback,
                        Placeholder.unparsed("player", valueOrEmpty(player)),
                        Placeholder.unparsed("item", valueOrEmpty(item)),
                        Placeholder.unparsed("old", valueOrEmpty(oldName)),
                        Placeholder.unparsed("new", valueOrEmpty(newName)));
            } catch (RuntimeException fallbackException) {
                getLogger().log(Level.WARNING, "Unable to parse the default message for '" + configPath + "'.", fallbackException);
                return Component.text(fallback);
            }
        }
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private void notifyAdmins(String messagePath, String fallback,
                              String player, String item, String oldName, String newName) {
        Component message = renderConfiguredMessage(messagePath, fallback, player, item, oldName, newName);
        if (message == null) {
            return;
        }

        String displayMode = getConfig().getString("messages.admin-display", "CHAT");
        if (displayMode == null) {
            displayMode = "CHAT";
        }
        displayMode = displayMode.trim().toUpperCase(Locale.ROOT);

        for (Player admin : getServer().getOnlinePlayers()) {
            if (!admin.hasPermission("anvilwatch.admin") || logDisabledAdmins.contains(admin.getUniqueId())) {
                continue;
            }

            switch (displayMode) {
                case "ACTION_BAR", "ACTIONBAR" -> admin.sendActionBar(message);
                case "BOTH" -> {
                    admin.sendMessage(message);
                    admin.sendActionBar(message);
                }
                default -> admin.sendMessage(message);
            }
        }
    }

    private String createLogEntry(String action, String playerName, String playerUUID,
                                  String itemType, String oldName, String newName) {
        String timestamp = LocalDateTime.now().format(dateFormat);
        return String.format(Locale.ROOT, "[%s] Player: %s (UUID: %s) %s (%s) from '%s' to '%s'",
                timestamp, playerName, playerUUID, action, itemType, oldName, newName);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        // Check if the click is on the result slot of an anvil view.
        if (!(event.getView() instanceof AnvilView anvilView)
                || event.getSlotType() != InventoryType.SlotType.RESULT
                || event.getClickedInventory() != anvilView.getTopInventory()) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player bukkitPlayer)) {
            getLogger().log(Level.WARNING, "Viewer is not a player for anvil event");
            return;
        }

        ItemStack result = event.getCurrentItem();
        if (result == null || !result.hasItemMeta()) {
            return;
        }

        ItemMeta resultMeta = result.getItemMeta();
        if (resultMeta == null) {
            return;
        }

        String itemType = result.getType().name();
        String customNewName = getPlainCustomName(resultMeta);
        // A result without a custom name is still relevant when a player removes
        // an existing custom name. It must not be checked as a banned player name.
        String newName = customNewName != null ? customNewName : itemType;

        ItemStack firstSlot = anvilView.getTopInventory().getItem(0);
        String oldName = firstSlot == null ? null : getPlainCustomName(firstSlot.getItemMeta());
        if (oldName == null) {
            oldName = itemType;
        }

        // Check player-entered names against banned words unless the player has
        // bypass permission. The material name is not player input.
        if (customNewName != null && !bukkitPlayer.hasPermission("anvilwatch.bypass")) {
            Pattern matchingPattern = findMatchingPattern(customNewName);
            if (matchingPattern != null) {
                event.setCancelled(true);

                if (getConfig().getBoolean("logging.log-blocked-attempts", true)) {
                    String blockedLogEntry = createLogEntry("attempted blocked rename of item",
                            bukkitPlayer.getName(), bukkitPlayer.getUniqueId().toString(),
                            itemType, oldName, newName);
                    writeToLog(blockedLogFile, blockedLogEntry, true);
                }

                Component playerMessage = renderConfiguredMessage("messages.player-blocked",
                        DEFAULT_PLAYER_BLOCKED_MESSAGE,
                        bukkitPlayer.getName(), itemType, oldName, newName);
                if (playerMessage != null) {
                    bukkitPlayer.sendMessage(playerMessage);
                }
                notifyAdmins("messages.admin-blocked", DEFAULT_ADMIN_BLOCKED_MESSAGE,
                        bukkitPlayer.getName(), itemType, oldName, newName);
                return;
            }
        }

        // Only log successful actions if the name has changed.
        if (newName.equals(oldName)) {
            return;
        }

        String logEntry = createLogEntry("renamed item",
                bukkitPlayer.getName(), bukkitPlayer.getUniqueId().toString(),
                itemType, oldName, newName);
        writeToLog(logFile, logEntry, false);
        notifyAdmins("messages.admin-rename", DEFAULT_ADMIN_RENAME_MESSAGE,
                bukkitPlayer.getName(), itemType, oldName, newName);
    }

    private void writeToLog(File target, String logEntry, boolean blocked) {
        if (target == null || !target.exists() || !target.canWrite()) {
            getLogger().log(Level.SEVERE, "Cannot write to log file: Log file is not initialized or not writable");
            return;
        }

        String line = logEntry.stripTrailing();
        RecentLogEntry pendingEntry = new RecentLogEntry(line, parseLogTimestamp(line),
                logSequence.incrementAndGet(), blocked);
        pendingLogEntries.add(pendingEntry);

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                synchronized (logWriteLock) {
                    try (BufferedWriter writer = Files.newBufferedWriter(target.toPath(),
                            StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                            StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
                        writer.write(line);
                        writer.newLine();
                    }
                }
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Failed to write to log file: " + target.getAbsolutePath(), e);
            } finally {
                pendingLogEntries.remove(pendingEntry);
            }
        });
    }

    private void showRecentActivity(CommandSender sender, int requestedPage) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            List<RecentLogEntry> entries = loadRecentEntries();
            int totalPages = Math.max(1, (entries.size() + RECENT_ENTRIES_PER_PAGE - 1) / RECENT_ENTRIES_PER_PAGE);

            if (requestedPage > totalPages) {
                Bukkit.getScheduler().runTask(this, () -> sender.sendMessage(Component.text(
                        "There are only " + totalPages + " page(s) of recent activity.", NamedTextColor.RED)));
                return;
            }

            int start = (requestedPage - 1) * RECENT_ENTRIES_PER_PAGE;
            int end = Math.min(start + RECENT_ENTRIES_PER_PAGE, entries.size());
            List<Component> messages = new ArrayList<>();
            messages.add(Component.text("AnvilWatch recent activity (page " + requestedPage
                    + " of " + totalPages + ")", NamedTextColor.YELLOW));

            if (entries.isEmpty()) {
                messages.add(Component.text("No rename activity has been logged yet.", NamedTextColor.GRAY));
            } else {
                for (RecentLogEntry entry : entries.subList(start, end)) {
                    messages.add(renderRecentEntry(entry));
                }
            }

            if (totalPages > 1) {
                Component navigation = Component.empty();
                if (requestedPage > 1) {
                    navigation = navigation.append(pageButton("[Previous]", requestedPage - 1));
                } else {
                    navigation = navigation.append(Component.text("[Previous]", NamedTextColor.DARK_GRAY));
                }
                navigation = navigation.append(Component.text("  ", NamedTextColor.GRAY));
                navigation = navigation.append(Component.text("Page " + requestedPage + "/" + totalPages, NamedTextColor.GRAY));
                navigation = navigation.append(Component.text("  ", NamedTextColor.GRAY));
                if (requestedPage < totalPages) {
                    navigation = navigation.append(pageButton("[Next]", requestedPage + 1));
                } else {
                    navigation = navigation.append(Component.text("[Next]", NamedTextColor.DARK_GRAY));
                }
                messages.add(navigation);
            }

            Bukkit.getScheduler().runTask(this, () -> messages.forEach(sender::sendMessage));
        });
    }

    private Component renderRecentEntry(RecentLogEntry entry) {
        Matcher matcher = RECENT_LOG_PATTERN.matcher(entry.line());
        if (!matcher.matches()) {
            NamedTextColor fallbackColor = entry.blocked() ? NamedTextColor.RED : NamedTextColor.GREEN;
            String prefix = entry.blocked() ? "[BLOCKED] " : "[RENAME] ";
            return Component.text(prefix + entry.line(), fallbackColor);
        }

        NamedTextColor statusColor = entry.blocked() ? NamedTextColor.RED : NamedTextColor.GREEN;
        String status = entry.blocked() ? "[BLOCKED]" : "[RENAME]";
        return Component.text()
                .append(Component.text(status, statusColor))
                .append(Component.text(" [" + matcher.group(1) + "] ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Player: ", NamedTextColor.GRAY))
                .append(Component.text(matcher.group(2), NamedTextColor.YELLOW))
                .append(Component.text(" (UUID: " + matcher.group(3) + ") ", NamedTextColor.DARK_GRAY))
                .append(Component.text(matcher.group(4) + " (", NamedTextColor.GRAY))
                .append(Component.text(matcher.group(5), NamedTextColor.AQUA))
                .append(Component.text(") from '", NamedTextColor.GRAY))
                .append(Component.text(matcher.group(6), NamedTextColor.RED))
                .append(Component.text("' to '", NamedTextColor.GRAY))
                .append(Component.text(matcher.group(7), NamedTextColor.GREEN))
                .append(Component.text("'", NamedTextColor.GRAY))
                .build();
    }

    private Component pageButton(String label, int page) {
        return Component.text(label, NamedTextColor.AQUA)
                .clickEvent(ClickEvent.runCommand("/anvilwatch recent " + page));
    }

    private List<RecentLogEntry> loadRecentEntries() {
        List<RecentLogEntry> entries = new ArrayList<>();
        readLogFile(logFile, false, entries);
        readLogFile(blockedLogFile, true, entries);

        Set<String> persistedLines = new HashSet<>();
        for (RecentLogEntry entry : entries) {
            persistedLines.add(entry.line());
        }
        for (RecentLogEntry pendingEntry : pendingLogEntries) {
            if (!persistedLines.contains(pendingEntry.line())) {
                entries.add(pendingEntry);
            }
        }

        entries.sort((left, right) -> {
            int timestampComparison = right.timestamp().compareTo(left.timestamp());
            return timestampComparison != 0
                    ? timestampComparison
                    : Long.compare(right.sequence(), left.sequence());
        });
        return entries;
    }

    private void readLogFile(File file, boolean blocked, List<RecentLogEntry> entries) {
        if (file == null || !file.isFile() || !file.canRead()) {
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    entries.add(new RecentLogEntry(line, parseLogTimestamp(line),
                            logSequence.incrementAndGet(), blocked));
                }
            }
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Failed to read recent activity from: " + file.getAbsolutePath(), e);
        }
    }

    private LocalDateTime parseLogTimestamp(String line) {
        if (line.length() < 21 || line.charAt(0) != '[' || line.charAt(20) != ']') {
            return LocalDateTime.MIN;
        }

        try {
            return LocalDateTime.parse(line.substring(1, 20), dateFormat);
        } catch (DateTimeParseException e) {
            return LocalDateTime.MIN;
        }
    }
}