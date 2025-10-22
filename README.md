# OrbitalStrike

**Epic orbital strikes like Wemmbu — Nuke & Stab in one plugin!**  
A powerful Spigot plugin for **Minecraft 1.21.1 - 1.21.10** featuring two devastating fishing rod weapons:

- `/orbital nuke` → **Massive crater with 10 rings + center TNT**  
- `/orbital stab` → **Instant full-depth tunnel to bedrock**

---

## Features

| Feature | Description |
|--------|-------------|
| **Nuke Strike** | 10 rings + center, all TNT drops simultaneously from above, explodes **2 seconds after impact** |
| **Stab Strike** | Instant tunnel straight down to bedrock |
| **One-Time Use** | Rod breaks after single use |
| **Fully Configurable** | `config.yml` for rings, yield, height, delay |
| **Permission System** | `orbital.use` — easy with LuckPerms |
| **No Cooldown** | Spam allowed (can crash or lag the Server)|

---

## Installation

1. Download `OrbitalStrike-1.0.jar`
2. Place it in your `plugins/` folder
3. **Start the server**
4. `plugins/OrbitalStrike/config.yml` is auto-generated

---

## Commands

| Command | Description |
|--------|-------------|
| `/orbital nuke` | Gives you a **Nuke Rod** |
| `/orbital stab` | Gives you a **Stab Rod** |

> **Permission:** `orbital.use`  
> → Default: **OPs only**  
> → With LuckPerms: `/lp group default permission set orbital.use false`

---

## Configuration (`config.yml`)

```yaml
nuke:
  rings: 10
  height: 80
  yield: 6.0
  delay-after-impact: 40  # 40 ticks = 2 seconds
  tnt-per-ring-base: 20
  tnt-per-ring-increase: 2
  center-tnt: true

stab:
  yield: 8.0
  tnt-offset: 0.3

permission: "orbital.use"
permission-message: "§cYou don't have permission to use Orbital Strike!"

messages:
  received: "§aReceived Orbital Strike Rod - §l{TYPE}§a!"
  incoming: "§6Orbital Strike incoming... §l{TYPE}§6!"
  no-target: "§cNo target found!"
