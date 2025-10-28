# OrbitalStrike Minecraft Plugin

**Epic orbital strikes like Wemmbu — Nuke & Stab in one plugin!**  
A powerful Plugin for **Minecraft 1.21.1 - 1.21.10** featuring two devastating fishing rod weapons:
###### Should work on any Spigot fork like Paper or Purpur  if not Message me on Discord or open a issue here

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

1. Download `OrbitalStrike-1.1.jar`
2. Place it in your `plugins/` folder
3. Go into **"spigot.yml"** and set **"max-tnt-per-tick"** to **1000** else it might cause problems
4. **Start the server**
5. `plugins/OrbitalStrike/config.yml` is auto-generated

---

## Commands

| Command | Description |
|--------|-------------|
| `/orbital nuke` | Gives you a **Nuke Rod** |
| `/orbital stab` | Gives you a **Stab Rod** |

> **Permission:** `orbital.use`  
> → Default: **OPs only**  
> → Should work with any Perms Plugin

---

## Configuration (`config.yml`)

```yaml
messages-enabled: true

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
```


<p align="center">
 <a href="https://discord.com/users/1092033992288653424" target="_blank"><img src="https://img.shields.io/badge/Discord-Juliaan.py-blue?style=for-the-badge&logo=discord" /></a>
</p>
