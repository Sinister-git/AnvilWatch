package com.citrusmc.anvilwatch;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

import org.bukkit.Bukkit;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.MultiLineChart;

public class AnvilWatch extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private File logFile;
    private File bannedWordsFile;
    private final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final List<Pattern> bannedWords = new ArrayList<>();
    private final Set<UUID> logDisabledAdmins = new HashSet<>();
    private final Map<Character, Character> homoglyphs = new HashMap<>();
    private final Map<Character, String> leetSpeakMap = new HashMap<>();

    @Override
    public void onEnable() {
        createLogFile();
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
        for (char c : name.toLowerCase().toCharArray()) {
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

    private void createLogFile() {
        File logsDir = new File(getDataFolder(), "logs");
        if (!logsDir.exists()) {
            try {
                if (!logsDir.mkdirs()) {
                    getLogger().log(Level.SEVERE, "Failed to create logs directory: " + logsDir.getAbsolutePath());
                    return;
                }
            } catch (SecurityException e) {
                getLogger().log(Level.SEVERE, "Security exception while creating logs directory: " + logsDir.getAbsolutePath(), e);
                return;
            }
        }

        logFile = new File(logsDir, "anvil_renames.log");
        if (!logFile.exists()) {
            try {
                if (logFile.createNewFile()) {
                    getLogger().info("Created log file: " + logFile.getAbsolutePath());
                } else {
                    getLogger().log(Level.WARNING, "Log file was not created (may already exist or creation failed): " + logFile.getAbsolutePath());
                }
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Failed to create log file: " + logFile.getAbsolutePath(), e);
                return;
            } catch (SecurityException e) {
                getLogger().log(Level.SEVERE, "Security exception while creating log file: " + logFile.getAbsolutePath(), e);
                return;
            }
        }

        if (!logFile.exists() || !logFile.canWrite()) {
            getLogger().log(Level.SEVERE, "Log file is not usable (does not exist or is not writable): " + logFile.getAbsolutePath());
            logFile = null;
        }
    }

    private void loadBannedWords() {
        bannedWords.clear();
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
                        bannedWords.add(Pattern.compile(line, Pattern.CASE_INSENSITIVE));
                    } catch (PatternSyntaxException e) {
                        getLogger().log(Level.SEVERE, "Invalid regex pattern in BannedWords.txt: '" + line + "'", e);
                    }
                }
            }
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
            sender.sendMessage(Component.text("Usage: /anvilwatch <help|reload|add|remove|log> <args>", NamedTextColor.YELLOW));
            return true;
        }

        String subCommand = args[0].toLowerCase();
        if (subCommand.equals("help")) {
            sender.sendMessage(Component.text("AnvilWatch Commands:", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/anvilwatch help - Displays this help message", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("/anvilwatch reload - Reloads banned words from BannedWords.txt", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("/anvilwatch add <word> - Adds a word to the banned list", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("/anvilwatch remove <word> - Removes a word from the banned list", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("/anvilwatch log <on|off> - Toggles in-game rename log messages for you", NamedTextColor.GRAY));
            return true;
        } else if (subCommand.equals("reload")) {
            loadBannedWords();
            sender.sendMessage(Component.text("AnvilWatch banned words reloaded.", NamedTextColor.GREEN));
            return true;
        } else if (subCommand.equals("add") && args.length == 2) {
            String word = args[1];
            addBannedWordAsync(sender, word);
            return true;
        } else if (subCommand.equals("remove") && args.length == 2) {
            String word = args[1];
            removeBannedWordAsync(sender, word);
            return true;
        } else if (subCommand.equals("log") && args.length == 2 && sender instanceof Player player) {
            boolean enable = args[1].equalsIgnoreCase("on");
            if (enable) {
                logDisabledAdmins.remove(player.getUniqueId());
                sender.sendMessage(Component.text("In-game rename log messages enabled.", NamedTextColor.GREEN));
            } else if (args[1].equalsIgnoreCase("off")) {
                logDisabledAdmins.add(player.getUniqueId());
                sender.sendMessage(Component.text("In-game rename log messages disabled.", NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("Usage: /anvilwatch log <on|off>", NamedTextColor.YELLOW));
            }
            return true;
        }

        sender.sendMessage(Component.text("Usage: /anvilwatch <help|reload|add|remove|log> <args>", NamedTextColor.YELLOW));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        if (!sender.hasPermission("anvilwatch.admin")) {
            return null;
        }

        if (args.length == 1) {
            return Stream.of("help", "reload", "add", "remove", "log")
                    .filter(cmd -> cmd.startsWith(args[0].toLowerCase()))
                    .toList();
        } else if (args.length == 2 && args[0].equalsIgnoreCase("log")) {
            return Stream.of("on", "off")
                    .filter(opt -> opt.startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        // Check if the inventory view is an anvil view
        if (!(event.getView() instanceof org.bukkit.inventory.view.AnvilView anvilView)) {
            return;
        }

        // Check if the click is in the result slot (slot 2)
        if (event.getSlotType() != InventoryType.SlotType.RESULT || event.getSlot() != 2) {
            return;
        }

        // Get the player
        if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player bukkitPlayer)) {
            getLogger().log(Level.WARNING, "Viewer is not a player for anvil event");
            return;
        }

        // Get the result item (the renamed item)
        ItemStack result = event.getCurrentItem();
        if (result == null || !result.hasItemMeta()) {
            return;
        }

        ItemMeta resultMeta = result.getItemMeta();
        if (resultMeta == null || !resultMeta.hasDisplayName()) {
            return;
        }

        // Convert Component to plain string
        Component displayNameComponent = resultMeta.displayName();
        String newName = displayNameComponent != null
                ? PlainTextComponentSerializer.plainText().serialize(displayNameComponent)
                : "";

        // Normalize the new name for checking against banned words
        String normalizedNewName = normalizeName(newName);

        // Check for banned words unless player has bypass permission
        if (!bukkitPlayer.hasPermission("anvilwatch.bypass")) {
            for (Pattern pattern : bannedWords) {
                if (pattern.matcher(normalizedNewName).find()) {
                    event.setCancelled(true);
                    bukkitPlayer.sendMessage(Component.text("You cannot use that word in item names.", NamedTextColor.RED));
                    return;
                }
            }
        }

        // Get the first slot item (original item)
        ItemStack firstSlot = anvilView.getTopInventory().getItem(0);
        String itemType = result.getType().toString();

        // Prepare log entry
        String logEntry = null;
        String adminMessage = null;
        String oldName;
        if (firstSlot != null && firstSlot.hasItemMeta()) {
            ItemMeta firstMeta = firstSlot.getItemMeta();
            if (firstMeta != null && firstMeta.hasDisplayName()) {
                Component oldNameComponent = firstMeta.displayName();
                oldName = oldNameComponent != null
                        ? PlainTextComponentSerializer.plainText().serialize(oldNameComponent)
                        : itemType; // Fallback to material name if display name is null
            } else {
                oldName = itemType; // Use material name if no custom display name
            }
        } else {
            oldName = itemType; // Use material name if no item or no meta
        }

        // Only log if the name has changed
        if (!newName.equals(oldName)) {
            String playerName = bukkitPlayer.getName();
            String playerUUID = bukkitPlayer.getUniqueId().toString();
            String timestamp = LocalDateTime.now().format(dateFormat);
            logEntry = String.format("[%s] Player: %s (UUID: %s) renamed item (%s) from '%s' to '%s'%n",
                    timestamp, playerName, playerUUID, itemType, oldName, newName);
            adminMessage = String.format("[%s] %s renamed %s from '%s' to '%s'",
                    timestamp, playerName, itemType, oldName, newName);
        }

        // Write to log and send to admins
        if (logEntry != null) {
            writeToLog(logEntry);
            Component logMessage = Component.text()
                    .append(Component.text(adminMessage.split(" ")[2], NamedTextColor.YELLOW))
                    .append(Component.text(" renamed ", NamedTextColor.GRAY))
                    .append(Component.text(adminMessage.split(" ")[4], NamedTextColor.AQUA))
                    .append(Component.text(" from '", NamedTextColor.GRAY))
                    .append(Component.text(oldName, NamedTextColor.RED))
                    .append(Component.text("' to '", NamedTextColor.GRAY))
                    .append(Component.text(newName, NamedTextColor.GREEN))
                    .append(Component.text("'", NamedTextColor.GRAY))
                    .build();
            getServer().getOnlinePlayers().stream()
                    .filter(p -> p.hasPermission("anvilwatch.admin") && !logDisabledAdmins.contains(p.getUniqueId()))
                    .forEach(p -> p.sendMessage(logMessage));
        }
    }

    private void writeToLog(String logEntry) {
        if (logFile == null || !logFile.exists() || !logFile.canWrite()) {
            getLogger().log(Level.SEVERE, "Cannot write to log file: Log file is not initialized or not writable");
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try (FileWriter writer = new FileWriter(logFile, true)) {
                writer.write(logEntry);
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Failed to write to log file: " + logFile.getAbsolutePath(), e);
            }
        });
    }
}