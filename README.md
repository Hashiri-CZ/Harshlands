<p align="center">
  <img src="https://raw.githubusercontent.com/Hashiri-CZ/Harshlands/refs/heads/master/.github/assets/banner.png" alt="Harshlands Banner" width="800"/>
</p>

<h1 align="center">⚔️ Harshlands</h1>

<p align="center">
  <b>The RLCraft Experience for Spigot & Paper — No Mods Required</b>
</p>

<p align="center">
  <a href="https://github.com/Hashiri-CZ/Harshlands/releases"><img src="https://img.shields.io/github/v/release/Hashiri-CZ/Harshlands?style=for-the-badge&color=c9302c&label=Latest%20Release" alt="Latest Release"/></a>
  <a href="https://github.com/Hashiri-CZ/Harshlands"><img src="https://img.shields.io/github/stars/Hashiri-CZ/Harshlands?style=for-the-badge&color=f0ad4e" alt="GitHub Stars"/></a>
  <a href="https://github.com/Hashiri-CZ/Harshlands/blob/main/LICENSE"><img src="https://img.shields.io/github/license/Hashiri-CZ/Harshlands?style=for-the-badge&color=5cb85c" alt="License"/></a>
  <a href="https://discord.gg/2EkszXWxbE"><img src="https://img.shields.io/badge/Discord-Join%20Us-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Discord"/></a>
</p>

---

## 🌍 What is Harshlands?

Harshlands turns vanilla Minecraft into something that actually tries to kill you — the way RLCraft does, but without asking anyone to install a single mod. It's a server-side plugin for Spigot and Paper. Drop the jar in, restart, and the world stops being polite.

Your players will get cold. They'll get thirsty. They'll punch a tree and hurt their hand. They'll run into a dragon and regret it. They'll eat nothing but steak for three days and wonder why their hearts are vanishing. They'll hear something moving in the dark and realize the dark has opinions now.

Built for server owners who want the RLCraft tone on a plain Spigot/Paper server.

---

## What's in the box

All of this is modular — every system below can be turned off, tuned, or left exactly as it is.

**Survival basics**
- **Tough As Nails** — body temperature, thirst, hypothermia, heatstroke, parasites. Biomes matter. Seasons matter if you run RealisticSeasons. Water isn't infinite anymore.
- **No Tree Punching** — bare hands don't fell oaks. Knap a rock against another rock, make a crude hatchet, work your way up. Early game becomes an actual phase instead of a two-minute formality.
- **Food Expansion** — hunger is replaced with three macros (protein, carbs, fats). Eat a balanced diet and you get bonus hearts; live on one food and you rot. A tiny nutrition preview above the hotbar shows what a food will give before you commit.
- **First Aid** — damage hits specific body parts. A broken leg slows you; a broken arm wrecks your aim. Bandages, splints, and medical kits are craftable — full body-part HP tracking arrives with the BodyHealth integration in a later release.
- **Comfort** — Valheim-style resting. Sleep near a campfire, a bed, some flowers, a crafted chair, and you earn a short buff. Sleep alone in a hole and you don't.

**Combat & creatures**
- **Ice and Fire** — dragons, sea serpents, cyclopses, sirens, hippogryphs, and the loot that drops off them. Boss-tier encounters without a modloader.
- **Spartan Weaponry** — halberds, rapiers, battleaxes, throwing knives, longbows, and the rest of the extended weapon set. Each has its own reach, speed, and feel.
- **Spartan and Fire** — dragonbone and dragonsteel variants of the Spartan weapons. Forged from what you've slain.
- **Baubles** — rings, amulets, belts, and charms. Equippable passives with a proper slot UI, a bauble bag, and enough variety to build around.

**Atmosphere & ambience**
- **Fear** — darkness is a mechanic, not a backdrop. Fear builds when you wander without light, triggers projectile inaccuracy, and eventually invites something unpleasant to visit you. Lit torches persist; unlit ones don't help.
- **Dynamic Surroundings** — ambient sound and particle effects that make biomes feel inhabited.
- **Sound Ecology** — loud activity (mining, fighting, sprinting) attracts hostile attention. Sneaking through a cave is different from bulldozing it.

**Helpful bits**
- **Progressive Hints** — clickable in-chat tips that appear the first time a player hits something the plugin changed: punching a tree, freezing in a biome, drinking from a dirty river, breaking a limb, earning a comfort buff, catching cabin fever, or crossing the fear threshold. `/hl obtain <item>` looks up crafting guides on demand. `/hl hints reset` replays them.
- **Integrations** — PlaceholderAPI, WorldGuard, RealisticSeasons, and AuraSkills are all picked up automatically if they're on the server.

---

## 🚀 Installation

| Requirement | Version |
|-------------|---------|
| Minecraft   | `1.21.11`, `26.1.2` |
| Server      | Spigot, Paper, or Purpur |
| Java        | 21+ |

1. Grab the latest jar from [Releases](https://github.com/Hashiri-CZ/Harshlands/releases).
2. Drop it into `plugins/`.
3. Start the server once so it generates `plugins/Harshlands/`.
4. Open `config.yml` and the files under `Settings/`, tune to taste.
5. `/hl reload`.

> [!NOTE]
> One jar covers both `1.21.11` and `26.1.2`. The plugin picks the right implementation at startup.

---

## ⚙️ Configuration layout

On first boot you get:

```
plugins/Harshlands/
├── config.yml              # Locale, database, performance, module master switches
├── Settings/               # One YAML per module — this is where you tune gameplay
├── Translations/
│   └── en-US/              # English ships by default. Copy the folder to add a language.
├── Items/                  # Item definitions, recipes, mob/block drop tables
├── Presets/                # Shared lore templates and AuraSkills requirement blocks
└── Data/                   # Database (H2) and runtime YAML
```

Want a hard-but-fair server? Leave Fear off and soften the temperature numbers. Want full RLCraft-grade punishment? Turn everything on and lower the nutrition defaults. The knobs are all there.

### Upgrading from the old layout

Servers running a pre-reorganization build auto-migrate on first boot. Old flat YAMLs get split into `Settings/`, `Translations/`, and `Items/`, the database is moved into `Data/`, and the old files are renamed to `*.yml.migrated` — never deleted. If migration can't complete, the plugin refuses to enable and tells you which file was the problem.

### Adding a language

1. Copy `Translations/en-US/` to `Translations/<code>/` (e.g., `cs-CZ`, `de-DE`).
2. Translate the string values in each YAML.
3. Set `Locale: "<code>"` in `config.yml`.
4. `/hl reload`.

---

## 🗺️ Roadmap

- [x] Multi-version support (1.21.11 and 26.1.2)
- [x] Food expansion with macronutrients
- [x] First Aid body-parts damage
- [x] Comfort system
- [ ] More mythical creatures beyond the Ice and Fire roster
- [ ] Skill & leveling tie-in that rewards survival actions
- [ ] Custom structures and dungeons
- [ ] Waystone system

Feature request? [Open an issue](https://github.com/Hashiri-CZ/Harshlands/issues) or drop by the [Discord](https://discord.gg/2EkszXWxbE).

---

## 🤝 Contributing

Bug reports, ideas, code, translations — all welcome.

1. Fork the repo.
2. Branch: `git checkout -b feature/whatever`.
3. Commit and push.
4. Open a PR.

---

## 📊 Project Stats

<p align="center">
  <img src="https://repobeats.axiom.co/api/embed/29bc2959d41437f1c3cdc536820cd9619ebe3fbe.svg" alt="Repobeats analytics" width="700"/>
</p>

---

## 📜 Credits & Acknowledgments

Harshlands stands on a lot of other people's work. Credit where it's due.

### Original plugin lineage

This project is a continuation of the **Realistic Survival** plugin:

- **[ValMobile](https://github.com/ValMobile/RealisticSurvival)** — original author of Realistic Survival.
- **[Nik0-0](https://github.com/Nikos-Stuff/RealisticSurvival-Updated)** — maintained an updated fork that extended ValMobile's work.

### Texture & mod credits

Source textures and design ideas borrowed (with thanks) from:

| Creator(s) | Contribution |
|-------------|-------------|
| **Azanor13** | Baubles |
| **gr8pefish** & **BBoldt** | Bauble Bag texture |
| **mfnalex** | Best Tools code |
| **Cursed1nferno** | Bountiful Baubles |
| **TeamCoFH** & **Drullkus** | Copper tool & armor textures |
| **Lycanite** & **Shivaxi** | Lycanite's Mobs |
| **Raptorfarian** & **Alexthe666** | Ice and Fire |
| **Raptorfarian** & **Janivire** | Spartan and Fire (1.12.2) |
| **Kreloxcz** & **ChoglixVT** | Spartan and Fire (1.16.5) |
| **ObliviousSpartan** | Spartan Shields |
| **ObliviousSpartan** & **xwerffx** | Spartan Weaponry |
| **CreativeMD**, **fonnymunkey** & **ariafreeze** | Tint textures |
| **XzeroAir** & **sonicx8000** | Trinkets and Baubles |
| **Tmtravlr** | Quality Tools |
| **AlcatrazEscapee** | No Tree Punching |
| **TheAdubbz** | Tough As Nails |
| **FN-FAL113** | Throwable weapon code |
| **BlayTheNinth** & **CFGrafanaStats** | Waystones |
| **Shivaxi** | RLCraft modpack |
| **Chaosyr** | Realistic Torches |

---

## ⚠️ Disclaimer

Harshlands is an independent, community-driven project. It is not affiliated with, endorsed by, or connected to Mojang Studios, Microsoft, Minecraft, or Shivaxi (creator of the official RLCraft modpack). All trademarks are the property of their respective owners.

This project originated as a fork of Realistic Survival. While we honor the original authors' contributions, Harshlands is maintained independently. Issues specific to this version belong in [this repository](https://github.com/Hashiri-CZ/Harshlands/issues), not with the original projects.

---

<p align="center">
  <b>Ready to make survival mean something again?</b><br/>
  <a href="https://github.com/Hashiri-CZ/Harshlands/releases">⬇️ Download Harshlands</a>
</p>

<p align="center">
  <sub>Made with ❤️ by <b>Hashiri_</b> and the Harshlands community</sub>
</p>
