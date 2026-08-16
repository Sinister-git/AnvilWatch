## [1.4]

### Added

- Added a configurable `config.yml` for player warning messages, admin notification messages, notification display modes, log locations, and blocked-attempt logging.
- Added MiniMessage support for configurable messages, including `<player>`, `<item>`, `<old>`, and `<new>` placeholders.
- Added separate logging for blocked rename attempts in `blocked_renames.log`.
- Added the `/anvilwatch check <text>` command for testing text against the current banned word list.
- Added the `/anvilwatch recent [page]` command for viewing the 10 most recent successful and blocked rename entries together.
- Added clickable pagination controls to the `recent` command.
- Added color-coded recent activity entries to make players, items, previous names, new names, and blocked attempts easier to distinguish.
- Added persistent admin notification preferences so `/anvilwatch log on|off` survives server restarts.
- Added tab completion for the new commands and options.

### Changed

- Upgraded the Paper API to `26.2.build.112-stable` and updated the plugin API version to Paper 26.2.
- Updated the Maven Compiler Plugin to `3.15.0` and configured the project to compile for Java 25.
- Updated the configurable successful-rename log to continue using `logs/anvil_renames.log` by default, preserving existing historical logs during upgrades.
- Updated `/anvilwatch reload` to reload both `config.yml` and `BannedWords.txt`.
- Updated admin notifications to use configurable chat, action-bar, or combined display modes.
- Updated log writing to use UTF-8 output and serialized asynchronous file appends.
- Updated the Commons Lang dependency to `3.20.0` with provided scope to remove the vulnerable transitive version associated with CVE-2025-48924.

### Fixed

- Fixed Paper 26.2 compatibility by replacing deprecated `ItemMeta.hasDisplayName()` and `ItemMeta.displayName()` usage with `hasCustomName()` and `customName()`.
- Fixed anvil result handling by validating the active `AnvilView`, the top inventory, and the result slot before processing a rename.
- Fixed detection of removed custom names while preventing the material name from being treated as player-entered text.
- Fixed fragile admin notification formatting that relied on splitting message text by delimiters.
- Fixed banned-word reload behavior by atomically replacing the active pattern list after a successful load.
- Fixed locale-sensitive name normalization by using `Locale.ROOT`.
- Fixed the `recent` command so historical successful entries and new blocked-attempt entries can be read and displayed together.
