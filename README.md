# OrbitalStrike Minecraft Plugin

**Epic orbital strikes like Wemmbu — Nuke & Stab or Dog Cannon Like Flamefrags!**  
A powerful Plugin for **Minecraft 1.21.1 - 1.21.10** featuring four devastating weapons:
###### Should work on any Spigot fork like Paper or Purpur  if not Message me on Discord or open a issue here

- `/orbital nuke` → **Massive crater with 10 rings + center TNT**
- `/orbital stab` → **Instant full-depth tunnel to bedrock**
- `/orbital dogs` → **50+ wolves ready to Help you**
- `/orbital chunkeater` → **Armorstand ready to destroy the whole Chunk**
---

## Features

| Feature | Description                                                                                     |
|--------|-------------------------------------------------------------------------------------------------|
| **Nuke Strike** | 10 rings + center, all TNT drops simultaneously from above, explodes **2 seconds after impact** |
| **Stab Strike** | Instant tunnel straight down to bedrock                                                         |
| **Dogs Strike** | Summons **50+ tamed wolves** with **Speed II + Strength II**                                    |
| **Chunkeater** | Powerful Armorstand that destroys a whole Chunk                                                                     |
| **One-Time Use** | Rod breaks after single use                                                                     |
| **Fully Configurable** | `config.yml` for rings, yield, height, delay                                                    |
| **Permission System** | `orbital.use` — easy with LuckPerms                                                             |
| **No Cooldown** | Spam allowed (can crash or lag the Server)                                                      |

---

## Installation

1. Download `OrbitalStrike-1.4.0.jar`
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
| `/orbital dogs` | Gives you a **Dog Rod** |
| `/orbital chunkeater` | Gives you a **Chunkeater Armorstand** |

> **Permission:** `orbital.use`  
> → Default: **OPs only**  
> → Should work with any Perms Plugin

---

## Configuration (`config.yml`)

```yaml
messages-enabled: true
permission: "orbital.use"

rod:
  distance: 100
  throw-rod: true

nuke:
  rings: 10
  height: 15
  yield: 6.0
  tnt-per-ring-base: 40
  tnt-per-ring-increase: 2
  center-tnt: true
  fuse-fallback-ticks: 160 # needs 8 Seconds to explode (20 ticks = 1 Second) (20x8=160)
  Animated-rings: true

stab:
  yield: 4.0
  tnt-offset: 0.3

dogs:
  count: 50
  radius: 5.0
  effect-duration: 2400   # 4 Minutes (20 Ticks = 1 Second)
  effects:
    - "SPEED:1"
    - "STRENGTH:2"

messages:
  received: "§aYou received an Orbital Strike Rod - §l{TYPE}§a!"
  incoming: "§6Orbital Strike incoming... §l{TYPE}§6!"
  no-target: "§cNo valid target found!"
```


<p align="center">
 <a href="https://discord.com/users/1092033992288653424" target="_blank"><img src="https://img.shields.io/badge/Discord-Juliaan.py-blue?style=for-the-badge&logo=discord" /></a>
</p>
