# AnvilWatch

**AnvilWatch** is a lightweight Paper plugin that monitors item renaming through anvils. It records successful rename actions, blocks names that match a configurable banned-word list, and gives administrators tools to review rename activity.

<p align="center">
    <a href="https://discord.gg/Evywvfz2FX">
        <img src="https://cdn.modrinth.com/data/cached_images/5ac8884b3c4916cbac2b514220b9bb678db039b7.png" width="300">
    </a>
    <br>
    <i>Please join the Discord if you have questions!</i>
</p>

## Features

- Logs successful item renames performed through anvils.
- Blocks item names that match entries in `BannedWords.txt`.
- Logs blocked rename attempts separately when enabled.
- Sends live rename notifications to online administrators.
- Supports configurable player warnings and administrator notification messages.
- Supports chat, action-bar, or combined administrator notifications.
- Provides `/anvilwatch recent` with combined successful and blocked activity, color-coded entries, and clickable pagination.
- Provides `/anvilwatch check` for testing text against the current banned-word list.
- Preserves administrator notification preferences across server restarts.
- Supports permission-based bypass for trusted users.
- Supports regular expressions, case-insensitive matching, homoglyph normalization, and leetspeak normalization.
- Supports live reloading of configuration and banned words without restarting the server.
- Includes bStats metrics with player and server telemetry.

## How Name Matching Works

Each non-comment line in `BannedWords.txt` is treated as a case-insensitive Java regular expression.

Before checking a name, AnvilWatch normalizes it by:

- Converting it to lowercase.
- Converting supported look-alike characters from other alphabets.
- Converting common leetspeak characters.
- Removing spaces and punctuation.

For example, a pattern of `badword` can catch variations such as:

```text
BADWORD
b.a.d.w.o.r.d
b4dw0rd
```

Because entries are regular expressions, repeated-letter variations can be covered with patterns such as:

```regex
ba+dword
```

This would match both `badword` and `baadword`.

The plugin does not currently perform general fuzzy matching, typo detection, phonetic matching, or automatic repeated-letter collapsing. More complex matching behavior should be expressed explicitly with regular expressions to avoid unnecessary false positives.

## Commands

| Command | Description |
|--------|-------------|
| `/anvilwatch help` | Displays the available commands. |
| `/anvilwatch reload` | Reloads `config.yml` and `BannedWords.txt`. |
| `/anvilwatch add <word>` | Adds a regex pattern to the banned-word list. |
| `/anvilwatch remove <word>` | Removes a matching regex pattern from the banned-word list. |
| `/anvilwatch log <on\|off>` | Enables or disables live administrator notifications for the command user. The preference is saved across restarts. |
| `/anvilwatch check <text>` | Checks text against the current normalized banned-word list and reports a matching pattern when blocked. |
| `/anvilwatch recent [page]` | Shows 10 recent successful and blocked rename entries together. The output includes clickable pagination when multiple pages exist. |

The `recent` command reads from both the successful and blocked log files, sorts entries by timestamp, and color-codes the player, item type, previous name, new name, and blocked status.

**Alias:** `/anw`

**Usage:** `/anvilwatch <help|reload|add|remove|log|check|recent> <args>`

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `anvilwatch.admin` | Receive live rename notifications and use administrator commands. | `op` |
| `anvilwatch.bypass` | Bypass banned-word restrictions when renaming items. | `false` |

## Configuration

The plugin generates a commented `config.yml` in its data folder. Configuration changes can be applied with `/anvilwatch reload`.

### Messages

Messages use MiniMessage formatting. Available placeholders are:

| Placeholder | Meaning |
|-------------|---------|
| `<player>` | The player who used the anvil. |
| `<item>` | The item material type. |
| `<old>` | The previous plain-text name. |
| `<new>` | The new plain-text name being attempted. |

The configurable message options are:

| Option | Description |
|--------|-------------|
| `messages.player-blocked` | Warning sent to a player when a rename is blocked. |
| `messages.admin-rename` | Live notification sent for a successful rename. |
| `messages.admin-blocked` | Live notification sent for a blocked rename attempt. |
| `messages.admin-display` | Notification method: `CHAT`, `ACTION_BAR`, or `BOTH`. |

Administrators can disable their own live notifications with `/anvilwatch log off`. This does not stop file logging.

### Logging

| Option | Default | Description |
|--------|---------|-------------|
| `logging.rename-file` | `logs/anvil_renames.log` | File for successful rename actions. |
| `logging.blocked-file` | `logs/blocked_renames.log` | File for blocked rename attempts. |
| `logging.log-blocked-attempts` | `true` | Whether blocked attempts are written to the blocked log. |

Relative paths are resolved inside the AnvilWatch plugin folder. Absolute paths are also accepted, and missing parent directories are created automatically. Existing log files are preserved and new entries are appended.

## Plugin Files

The plugin data folder contains:

```text
BannedWords.txt
config.yml
data.yml
logs/anvil_renames.log
logs/blocked_renames.log
```

- `BannedWords.txt` contains the banned regex patterns.
- `config.yml` contains messages and logging options.
- `data.yml` stores persistent administrator notification preferences.
- `logs/anvil_renames.log` contains successful rename actions.
- `logs/blocked_renames.log` contains blocked attempts when enabled.

Existing `BannedWords.txt` and `logs/anvil_renames.log` files are retained when upgrading from an older AnvilWatch version. New files are created only when needed.

## Compatibility

- **Minecraft/Paper:** 26.2
- **Paper API:** `26.2.build.112-stable`
- **Java:** 25
- **Dependencies:** Paper API, provided by the server, and bStats, shaded into the plugin JAR.

## Building and Installing

1. Build with JDK 25:

   ```bash
   mvn clean package -DskipTests
   ```

2. Copy the generated JAR from `target/anvilwatch-1.4.jar` into the server's `plugins/` folder.
3. Stop the server before replacing an existing plugin JAR.
4. Keep the existing AnvilWatch data folder so that the banned-word list and historical logs are preserved.
5. Start the server and review the generated `config.yml`.
6. Assign the `anvilwatch.admin` permission to trusted staff.

[![bStats Metrics](https://bstats.org/signatures/bukkit/anvilwatch.svg)](https://bstats.org/plugin/bukkit/AnvilWatch)
