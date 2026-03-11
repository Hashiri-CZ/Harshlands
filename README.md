<p align="center">
  <!-- Replace with your actual logo/banner image -->
  <img src="https://raw.githubusercontent.com/Hashiri-CZ/RealisticSurvival-Reloaded/main/.github/assets/banner.png" alt="Harshlands Banner" width="800"/>
</p>

<h1 align="center">⚔️ Harshlands</h1>

<p align="center">
  <b>The RLCraft Experience for Spigot & Paper — No Mods Required</b>
</p>

<p align="center">
  <a href="https://github.com/Hashiri-CZ/Harshlands/releases"><img src="https://img.shields.io/github/v/release/Hashiri-CZ/Harshlands?style=for-the-badge&color=c9302c&label=Latest%20Release" alt="Latest Release"/></a>
  <a href="https://github.com/Hashiri-CZ/Harshlands"><img src="https://img.shields.io/github/stars/Hashiri-CZ/Harshlands?style=for-the-badge&color=f0ad4e" alt="GitHub Stars"/></a>
  <a href="https://github.com/Hashiri-CZ/Harshlands/blob/main/LICENSE"><img src="https://img.shields.io/github/license/Hashiri-CZ/Harshlands?style=for-the-badge&color=5cb85c" alt="License"/></a>
  <a href="https://discord.gg/E863pjrSXr"><img src="https://img.shields.io/badge/Discord-Join%20Us-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Discord"/></a>
</p>


---

## 🌍 What is Harshlands?

**Harshlands** transforms your vanilla Minecraft server into a brutal, RLCraft-inspired survival experience — entirely server-side. No client mods. No modloader. Just drop it on your Spigot or Paper server and let your players discover what survival *actually* means.

Thirst will drain them. Temperature will punish them. Trees won't break with bare fists. Dragons will hunt them. And if they think the dark is safe — they haven't met what lives in it.

> **🎯 Built for server owners** who want RLCraft-level difficulty without forcing players to install anything.

---

## ✨ Features

| Feature | Description |
|---------|-------------|
| 🌡️ **Temperature & Thirst** | Players must manage body temperature and hydration to survive. Deserts burn, tundras freeze, and water is no longer infinite. |
| 🪓 **No Tree Punching** | Forget punching trees — players need to craft basic tools from rocks and sticks before they can even gather wood. |
| 🐉 **Ice & Fire** | Dragons, sea serpents, and mythical creatures roam the world. Engage in epic boss-level encounters without a single client mod. |
| ⚔️ **Spartan Weaponry** | Dozens of new weapon types — halberds, rapiers, throwing knives, battleaxes — each with unique combat mechanics. |
| 🔥 **Spartan & Fire** | Dragonbone and Dragonsteel weapon variants forged from the creatures you slay. |
| 💎 **Baubles & Trinkets** | Equippable rings, amulets, and charms that grant passive buffs and abilities. |
| 👻 **Fear** | Darkness is deadly. Unseen terrors stalk players who dare to wander without light. |
| 🔧 **Fully Modular** | Every feature can be toggled, tweaked, or disabled independently. Your server, your rules. |

---

## 📦 Modules

Harshlands is built on a modular architecture. Enable only what you need:

```
harshlands/
├── 🌡️ Tough As Nails      → Temperature & thirst survival mechanics
├── 🪓 No Tree Punching     → Realistic early-game progression
├── 🐉 Ice and Fire         → Dragons, mythical creatures & loot
├── ⚔️ Spartan Weaponry     → Expanded weapon arsenal
├── 🔥 Spartan and Fire     → Dragon-forged weapon variants
├── 💎 Baubles              → Wearable trinkets & accessories
└── 👻 Fear                 → Darkness-based horror mechanics
```

Each module has its own configuration file. Mix and match to create the exact experience your community wants.

---

## 🚀 Installation

### Requirements

| Requirement | Version                  |
|-------------|--------------------------|
| Minecraft | `1.21.11`                |
| Server | Spigot, Paper, or Purpur |
| Java | 21+                      |

### Quick Start

1. Download the latest release from [Releases](https://github.com/Hashiri-CZ/Harshlands/releases)
2. Place the `.jar` file in your server's `plugins/` folder
3. Restart your server
4. Configure modules in `plugins/Harshlands/config.yml`
5. Watch your players *suffer* — in the best way possible

> [!NOTE]
> **Harshlands currently supports Minecraft `1.21.11` only.** Support for additional versions is planned.

---

## ⚙️ Configuration

Every module is independently configurable. After first launch, you'll find configuration files in:

```
plugins/Harshlands/
├── config.yml              # Master config — enable/disable modules
├── tough-as-nails.yml      # Temperature & thirst settings
├── no-tree-punching.yml    # Early-game progression rules
├── ice-and-fire.yml        # Creature spawning & loot tables
├── spartan-weaponry.yml    # Weapon stats & recipes
├── baubles.yml             # Trinket effects & drop rates
└── fear.yml                # Darkness mechanics & intensity
```

Want a server that's hard but not *RLCraft* hard? Turn off Fear and reduce temperature damage. Want pure chaos? Crank everything to max. The choice is yours.

---

## 🗺️ Roadmap

Harshlands is under active development. Here's what's coming:

- [ ] **Expanded creatures** — More mythical beasts beyond dragons
- [ ] **Skill & leveling system** — RPG progression tied to survival actions
- [ ] **Custom structures & dungeons** — Exploration rewards for the brave
- [ ] **Waystone system** — Fast travel for those who've earned it
- [ ] **Food expansion** — Stop eating that steak!
- [ ] **Multi-version support** — Expanding beyond 1.21.11

Have a feature request? [Open an issue](https://github.com/Hashiri-CZ/Harshlands/issues) or join our [Discord](https://discord.gg/E863pjrSXr).

---

## 🤝 Contributing

Contributions are welcome! Whether it's bug reports, feature ideas, code, or translations — every bit helps.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 📊 Project Stats

<p align="center">
  <img src="https://repobeats.axiom.co/api/embed/29bc2959d41437f1c3cdc536820cd9619ebe3fbe.svg" alt="Repobeats analytics" width="700"/>
</p>

---

## 📜 Credits & Acknowledgments

Harshlands builds upon the foundation laid by talented developers and creators in the Minecraft modding community. We gratefully acknowledge their work:

### Original Plugin Lineage

This project is a continuation of the **Realistic Survival** plugin:

- **[ValMobile](https://github.com/ValMobile/RealisticSurvival)** — Original creator of Realistic Survival
- **[Nik0-0](https://github.com/Nikos-Stuff/RealisticSurvival-Updated)** — Maintained an updated fork that extended ValMobile's work

### Texture & Mod Credits

Harshlands uses source textures and ideas from the following mods and their creators:

| Creator(s) | Contribution |
|-------------|-------------|
| **Azanor13** | Baubles |
| **gr8pefish** & **BBoldt** | Bauble Bag Texture |
| **mfnalex** | Best Tools Code |
| **Cursed1nferno** | Bountiful Baubles |
| **TeamCoFH** & **Drullkus** | Copper Tool & Armor Textures |
| **Lycanite** & **Shivaxi** | Lycanite's Mobs |
| **Raptorfarian** & **Alexthe666** | Ice and Fire |
| **Raptorfarian** & **Janivire** | Spartan and Fire [1.12.2] |
| **Kreloxcz** & **ChoglixVT** | Spartan and Fire [1.16.5] |
| **ObliviousSpartan** | Spartan Shields |
| **ObliviousSpartan** & **xwerffx** | Spartan Weaponry |
| **CreativeMD**, **fonnymunkey** & **ariafreeze** | Tint Textures |
| **XzeroAir** & **sonicx8000** | Trinkets and Baubles |
| **Tmtravlr** | Quality Tools |
| **AlcatrazEscapee** | No Tree Punching |
| **TheAdubbz** | Tough As Nails |
| **FN-FAL113** | Throwable Weapon Code |
| **BlayTheNinth** & **CFGrafanaStats** | Waystones |
| **Shivaxi** | RLCraft Modpack |
| **Chaosyr** | Realistic Torches |

---

## ⚠️ Disclaimer

Harshlands is an **independent, community-driven project**. It is not affiliated with, endorsed by, or connected to Mojang Studios, Microsoft, Minecraft, or Shivaxi (the creator of the official RLCraft modpack). All trademarks and registered trademarks are the property of their respective owners.

This project originated as a fork of the Realistic Survival plugin. While we honor the original authors' contributions, Harshlands is maintained independently. Any issues specific to this version should be directed to [this repository](https://github.com/Hashiri-CZ/Harshlands/issues), not to the original projects.

---

<p align="center">
  <b>Ready to make survival mean something again?</b><br/>
  <a href="https://github.com/Hashiri-CZ/Harshlands/releases">⬇️ Download Harshlands</a>
</p>

<p align="center">
  <sub>Made with ❤️ by <b>Hashiri_</b> and the Harshlands community</sub>
</p>