# 🧱 CommunityVault 🧱

## A Global Shared Vault for Multiplayer Servers

CommunityVault is a server-wide storage system for collaborative survival. Players use physical deposit
and withdrawal chests, organized categories, and search to keep a clean, immersive shared vault.

### 🌟 Features

- 🔓 Global Vault: Shared storage via `/cvault` (read-only anywhere).
- 📦 Deposit & Withdrawal Chests: Buy with `/buydc` and `/buywc` (configurable costs, 0 allowed).
- 🧠 Automatic Sorting & Categories: Items organized by material; categories editable in-game.
- 🔍 Vault Search: `/searchvault <term>` (alias `/sv`); withdrawing works when looking at a withdrawal chest.
- 📑 Pagination UI: Clean pages with navigation buttons and tooltips.
- 🗂️ Manual Compaction: `/cvaultcompact` stacks items; unstackables (e.g., shulkers) are left safe.
- 🩺 Status Command: `/cvaultstatus` shows stack/item counts, backups, saves, and capacity status.
- 🔁 Reload Command: `/cvaultreload` reloads config and categories without a restart.
- 🔄 Automation: hoppers/droppers/crafters can deposit into deposit chests (capacity-respecting).
- 🪣 Hopper Withdrawal Output: set a single output per withdrawal chest and let hoppers pull it.
- ⛔ Capacity Limits: optional max-cap with clear messaging when full.
- 💾 Backups: automatic backups on startup/shutdown with 14-day retention.
- ✅ Safety: withdrawals check inventory space and roll back if full.

### 🧱 How It Works

1. Buy a Deposit Chest (`/buydc`) or Withdrawal Chest (`/buywc`).
2. Deposit chest: drop items in; they auto-sort into the global vault.
3. Withdrawal chest: open and pull items or search with `/sv <term>` while looking at it.
4. Optional: set a hopper output from the withdrawal chest UI (single output per chest).
4. View the vault anywhere with `/cvault` (categories, counts, pagination).

### 🧩 Chest Rules

- Deposit chests only pair with deposit chests.
- Withdrawal chests only pair with withdrawal chests.
- A normal chest cannot attach to a special chest (deposit/withdrawal).
- Withdrawal hoppers pull the selected output item (buffered in the center slot).

### 🧩 Category Management

- Open `Manage Categories` from the vault (book icon).
- Players can rename categories, change icons, and edit items by default.
- Creating/deleting categories requires permissions.
- Items can belong to multiple categories.

### ⚙️ Config Highlights

- `diamondCostDepositChest` / `diamondCostWithdrawalChest`
- `maxVaultCapacityEnabled`
- `maxVaultCapacity`
- `allowHopperWithdrawal`
- `allowHopperDeposit`
- `allowDropperDeposit`
- `allowCrafterDeposit`

### 🔐 Permissions

- `communityvault.compact` (or op): `/cvaultcompact`
- `communityvault.status` (or op): `/cvaultstatus`
- `communityvault.reload` (or op): `/cvaultreload`
- `communityvault.categories.view`
- `communityvault.categories.create`
- `communityvault.categories.rename`
- `communityvault.categories.icon`
- `communityvault.categories.additem`
- `communityvault.categories.removeitem`
- `communityvault.categories.delete`

Defaults:
- `view`, `rename`, `icon`, `additem`, `removeitem` are **default true**.
- `create` and `delete` are **default op**.

Core usage (view, deposit, withdrawal via withdrawal chest) is permission-free by default. Server admins
can change all permissions with their permission plugin (LuckPerms, etc.).

### 🛠️ Storage & Data

- Vault and categories stored under `plugins/CommunityVault/` (YAML).
- Backups saved with timestamps; old backups pruned after 14 days.
- Unstackables (e.g., shulkers) are never merged during compaction.

### 🛣️ Roadmap

- 🔐 More permission nodes and fine-grained access.
- 🛒 Economy/Vault plugin pricing options.
- 📚 Audit/history of deposits/withdrawals.
- 🛠️ Admin restore from backup command.
- 🗄️ Optional database backend for multi-server/shared vaults.
- 📚 Paginated category list in the main vault menu (for large category counts).

### 💬 Final Notes

CommunityVault keeps shared storage survival-friendly: physical chests, organized GUIs, fast search, and
admin safety tools. Built by Niels—designed to be fast, friendly, and fun. Need help or have suggestions?
Comment on Modrinth or open an issue on GitHub.

### Stats
![Bstats](https://bstats.org/signatures/bukkit/CommunityVault.svg)
