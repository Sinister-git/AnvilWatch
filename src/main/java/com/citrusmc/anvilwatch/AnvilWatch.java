package com.citrusmc.anvilwatch;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Stream;

public class AnvilWatch extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private File logFile;
    private File bannedWordsFile;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private List<String> bannedWords;
    private final Set<UUID> logDisabledAdmins = new HashSet<>();

    @Override
    public void onEnable() {
        createLogFile();
        loadBannedWords();
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("anvilwatch")).setExecutor(this);
        Objects.requireNonNull(getCommand("anvilwatch")).setTabCompleter(this);
        getLogger().info("AnvilWatch plugin enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("AnvilWatch plugin disabled.");
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
        bannedWords = new ArrayList<>();
        bannedWordsFile = new File(getDataFolder(), "BannedWords.txt");
        if (!bannedWordsFile.exists()) {
            try {
                if (bannedWordsFile.createNewFile()) {
                    getLogger().info("Created BannedWords.txt: " + bannedWordsFile.getAbsolutePath());
                    try (FileWriter writer = new FileWriter(bannedWordsFile)) {
                        writer.write("# Add one banned word per line (case-insensitive)\n");
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
                    bannedWords.add(line.toLowerCase());
                }
            }
            getLogger().info("Loaded " + bannedWords.size() + " banned words.");
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed to read BannedWords.txt: " + bannedWordsFile.getAbsolutePath(), e);
        }
    }

    private boolean addBannedWord(String word) {
        if (word == null || word.trim().isEmpty()) {
            return false;
        }
        word = word.trim().toLowerCase();
        if (bannedWords.contains(word)) {
            return false;
        }
        try (FileWriter writer = new FileWriter(bannedWordsFile, true)) {
            writer.write(word + "\n");
            bannedWords.add(word);
            getLogger().info("Added banned word: " + word);
            return true;
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed to add banned word to BannedWords.txt: " + word, e);
            return false;
        }
    }

    private boolean removeBannedWord(String word) {
        if (word == null || word.trim().isEmpty()) {
            return false;
        }
        word = word.trim().toLowerCase();
        if (!bannedWords.contains(word)) {
            return false;
        }
        bannedWords.remove(word);
        try {
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(bannedWordsFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().toLowerCase().equals(word)) {
                        lines.add(line);
                    }
                }
            }
            try (FileWriter writer = new FileWriter(bannedWordsFile)) {
                for (String line : lines) {
                    writer.write(line + "\n");
                }
            }
            getLogger().info("Removed banned word: " + word);
            return true;
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed to remove banned word from BannedWords.txt: " + word, e);
            return false;
        }
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
            if (addBannedWord(word)) {
                sender.sendMessage(Component.text("Added banned word: " + word, NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("Failed to add banned word: " + word + " (already exists or invalid)", NamedTextColor.RED));
            }
            return true;
        } else if (subCommand.equals("remove") && args.length == 2) {
            String word = args[1];
            if (removeBannedWord(word)) {
                sender.sendMessage(Component.text("Removed banned word: " + word, NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("Failed to remove banned word: " + word + " (not found or invalid)", NamedTextColor.RED));
            }
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

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Check if the inventory is an anvil
        if (!(event.getInventory() instanceof AnvilInventory anvilInventory)) {
            return;
        }

        // Check if the click is in the result slot (slot 2)
        if (event.getSlotType() != InventoryType.SlotType.RESULT || event.getSlot() != 2) {
            return;
        }

        // Get the player from viewers
        List<HumanEntity> viewers = anvilInventory.getViewers();
        if (viewers.isEmpty()) {
            getLogger().log(Level.WARNING, "No player found for anvil event");
            return;
        }
        HumanEntity player = viewers.getFirst(); // Anvils typically have one viewer
        if (!(player instanceof org.bukkit.entity.Player bukkitPlayer)) {
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

        // Check for banned words unless player has bypass permission
        if (!bukkitPlayer.hasPermission("anvilwatch.bypass")) {
            String newNameLower = newName.toLowerCase();
            for (String bannedWord : bannedWords) {
                if (newNameLower.contains(bannedWord)) {
                    event.setCancelled(true);
                    bukkitPlayer.sendMessage(Component.text("Banned word", NamedTextColor.RED));
                    return;
                }
            }
        }

        // Get the first slot item (original item)
        ItemStack firstSlot = anvilInventory.getFirstItem();
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
            String timestamp = dateFormat.format(new Date());
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
        try (FileWriter writer = new FileWriter(logFile, true)) {
            writer.write(logEntry);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed to write to log file: " + logFile.getAbsolutePath(), e);
        }
    }
}