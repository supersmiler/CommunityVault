# 🧱 CommunityVault 🧱

  ## A Global Shared Vault Plugin for Multiplayer Servers

  CommunityVault is a server-wide storage system for collaborative survival. Players use physical deposit/
  withdrawal chests, organized categories, and search to keep a clean, immersive shared vault.

  ### 🌟 Features

  - 🔓 Global Vault: Shared storage via /cvault, with read-only browsing anywhere.
  - 📦 Deposit & Withdrawal Chests: Buy with /buydc and /buywc (configurable costs, 0 allowed). Place to
    deposit or withdraw physically—no teleporting items.
  - 🧠 Automatic Sorting & Categories: Items organized by material; categories from categories.yml and a
    category picker UI.
  - 🔍 Vault Search: /searchvault <term> (alias /sv); withdrawing works when looking at a withdrawal chest.
  - 📑 Pagination UI: Clean pages with navigation buttons and tooltips.
  - 🗂️ Manual Compaction: /cvaultcompact (admin/perm) stacks items in the vault; unstackables (e.g.,
    shulkers) are left safe.
  - 🩺 Status Command: /cvaultstatus (admin/perm) shows stack count, total items, categories, last backup,
    and save task status.
  - 💾 Backups: Automatic backups on startup/shutdown with 14-day retention; timestamped files in plugins/
    CommunityVault/backups.
  - ⚙️ Persistence: Vault/category data saved to disk; background saves for vault/chests.
  - ✅ Safety: Withdrawals check inventory space, stack into partial slots, and roll back if full—no more
    item loss.
  - 🧭 Permissions: Admin commands gated by permissions; core browsing/withdrawal still survival-friendly.

  ### 🧱 How It Works

  1. Buy a Deposit Chest (/buydc) or Withdrawal Chest (/buywc).
  2. Deposit chest: drop items in; they auto-sort into the global vault.
  3. Withdrawal chest: open and pull items or search with /sv <term> while looking at it.
  4. View the vault anywhere with /cvault (categories, counts, pagination).

  ### 🛠️ Storage & Data

  - Vault and categories stored under plugins/CommunityVault/ (YAML).
  - Backups saved with timestamps; old backups pruned after 14 days.
  - Unstackables (e.g., shulkers) are never merged during compaction.

  ### 🛣️ Roadmap

  - 🔐 More permission nodes and fine-grained access.
  - 🛒 Economy/Vault plugin pricing options.
  - 📚 Audit/history of deposits/withdrawals.
  - ⛔ Capacity limits & per-category quotas.
  - 🛠️ Admin restore from backup command.

  ### 💬 Final Notes

  CommunityVault keeps shared storage survival-friendly: physical chests, organized GUIs, fast search, and
  admin safety tools. Built by Niels—designed to be fast, friendly, and fun. Need help or have suggestions?
  Comment on Modrinth or open an issue on GitHub.

  ### Stats
![Bstats](https://bstats.org/signatures/bukkit/CommunityVault.svg)
