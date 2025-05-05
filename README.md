# 🧱 CommunityVault 🧱
##  A Global Shared Vault Plugin for Multiplayer Servers  

CommunityVault is a server-wide item storage system designed for collaborative survival gameplay. With physical deposit/withdrawal chests, organized categories, and powerful searching, it creates a clean and immersive shared storage experience — all while maintaining a survival-friendly vibe.

### 🌟 Features

🔓 Global Vault: A server-wide shared storage system accessible with **/cvault**

📦 Deposit & Withdrawal Chests:

Players can buy deposit/withdrawal chests for 5 diamonds each via **/buydc** and **/buywc**
Chests allow physical item depositing/withdrawing — no instant teleportation of items!

🧠 Automatic Sorting:

Items are sorted based on their material type
For example: enchanted swords, plain swords, or damaged swords all go into the Sword category

🗃️ Custom Categories:

Easily configured via a categories.json file
Define your own groups like "Ores", "Tools", "Wood", etc.

🔍 Vault Search System:

Use **/searchvault <term>** to find specific items
If looking at a withdrawal chest, **/searchvault <term>** can also be used to withdraw items

🧭 Read-Only Vault View:

Players can browse the vault anywhere using **/cvault**
Sorted by category, includes item quantities and pagination

📑 Pagination:

Clean GUI with paginated views of category contents

⚙️ Fully Persistent:

Vault and category data is saved in JSON and survives restarts

🧵 Async File Writes:

Data saving is optimized to avoid lag during large item deposits

❌ No Permissions Needed (yet):

Anyone can use the system — permissions support coming soon

### 🧱 How It Works

Players purchase a Deposit Chest (**/buydc**) or Withdrawal Chest (**/buywc**) for 5 diamonds.
A Deposit Chest accepts items and auto-sorts them into the global vault.
A Withdrawal Chest lets players pull specific items out using **/searchvault <term>**.
Players can view the vault anytime using **/cvault**, organized by category with pagination.
If a chest is broken, it’s gone — players must buy a new one.

### 🛠️ Storage System

Vault and category data is stored in vault.json and categories.json
Uses Material types to classify and group items (enchantments/durability don't affect sorting)
Custom categories are easy to edit and reload

### 🛣️ Roadmap:

🔐 Add permission nodes for advanced access control

🛒 Configurable costs for buying chests

📚 Vault history/logs (who deposited/withdrew what and when)

⛔ Vault capacity limits & per-category quotas

🛠️ Admin override tools

🌍 WorldGuard region protection integration


### 💬 Final Notes

CommunityVault is ideal for survival or semi-RPG servers that want shared storage without breaking immersion. It blends player interaction, GUI-based browsing, and command-driven searching into one cohesive system.

Built by Niels — designed to be fast, friendly, and fun.

Need help or have a suggestion? Drop a comment on Modrinth or open an issue on GitHub!

### Stats
![Bstats](https://bstats.org/signatures/bukkit/CommunityVault.svg)
