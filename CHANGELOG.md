# Harshlands Changelog

## 1.3.1 — Polish Update (WIP)

Player-experience polish pass addressing 130 findings from the 1.3.0 review. Focus on new-player onboarding, translation coverage, balance tuning, and tone consistency. No new gameplay systems.

### Added

- `/hl help` now works for regular players and lists every 1.3.1 command, split into Survival / Self-service / Admin sections.
- 16 new progressive hints for first-time moments: cold exposure, heat exposure, shivering-as-noise, thirst warning, parasite onset/cured, low macro, well-nourished tier-up, overeating, pet eating (deferred wiring), broken limb, fear climb past 50, nightmare spawn, bauble first equip, first comfort buff, first cabin fever restless.
- 12 new `/hl obtain` item guides: campfire, fire_starter, jelled_slime, purified_water_bottle, charcoal_filter, canteen_empty, bandage, splint, medical_kit, thermometer, bauble_bag.
- First Aid bandage, splint, and medical_kit items with recipes (lore currently notes healing ships with the upcoming BodyHealth integration).
- First Aid injury chat messages (`firstaid.damage.*` translations) for head_critical / torso_critical / arm_broken / leg_broken / both_legs_broken / foot_broken — ready to wire when BodyHealth integration lands.
- `CabinFever.RequireMaterialRoof` + `NaturalRoofBlocks` config to exempt forest canopies from cabin-fever counting.
- `WarningLore` key on trap foods (`jelly`, `bat_wing`, `cooked_bat_wing`) — red warning lines now appear in the item tooltip.

### Changed

- `harshlands.command.help`, `command.version`, `command.fear`, `command.debug` defaults flipped from `op` to `true` so regular players can self-query.
- New `harshlands.command.hints.reset.self` (default `true`) — players can replay hints without an admin. `harshlands.admin.hints` continues to gate resetting other players. Admin perm is treated as a superset in the self path.
- NTP `PlankDrops` / `StickDrops` default chance raised from `0.4` to `0.6`, range tightened from `2-4` to `2-3` — less feast-or-famine for early-game wood.
- Ice and Fire `Dragon.SpawnChance` default dropped from `0.8` to `0.3` — gentler first contact for new servers; admins can raise for RLCraft-grade danger.

### Fixed

- `FIRST_SHIVERING` hint originally wired to the cold-breath particle visual; now correctly fires on the SoundEcology shivering-noise event (matches the hint's text and review intent).
- Fear triggers now clamp per-check via `MaxGainPerCheck` (default 3.0) so Darkness + Cave + Night + Enemies can no longer spike fear to 100 in <10s. `AllowPartialDecayUnderTriggers` lets PassiveDecay apply at half-rate when trigger load is light.
- Fear's `EatCookedFood` list now accepts Harshlands custom foods (cooked bacon, soups, bat soup, etc.) — not just vanilla cooked items.
- FakeMobSounds rate dropped from 0.05 → 0.012 per effects tick and `ENTITY_CREEPER_PRIMED` removed from the pool (it was driving players backward into actual traps). Heartbeat now has a 60-tick cooldown and quieter volume.
- Comfort tier thresholds raised: SHELTER 2-5, HOME 6-10, COZY 11-16, LUXURY 17+. New `DiminishingReturns` scoring (Factor 0.5, Cap 2×) makes spamming one category less rewarding. The easy 14-point LUXURY loadout now lands in COZY.
- Cabin fever no longer triggers under forest canopies (leaves/vines/glow-lichen/snow/mangrove-roots are excluded when `RequireMaterialRoof: true`).
- Macro starvation damage gated behind vanilla hunger threshold — players no longer take HP damage with a full hunger bar. Action-bar warning fires instead.
- Only water-type potions (WATER / MUNDANE / AWKWARD / THICK) now restore thirst. The brewing-stand-as-water-fountain loophole is closed.
- Thrown weapons returning to a full inventory are now owner-locked for 10 seconds with extended despawn (180s), so a throwable dropped over lava or in a contested area is recoverable.

### Added (Phase 2)

- Bauble drop-on-death chat notice on respawn (`baubles.death.baubles_dropped`) — tells the victim how many baubles dropped.
- `/hl comfort` now shows the delta to the next tier (`next_tier_hint`) or confirms max tier (`at_max_tier`).
- Tough As Nails death messages rewritten to match the Harshlands tone (dehydration / parasites / hyperthermia / hypothermia).
- Macro decay values now documented inline (units per minute of idle).

### Known backlog (deferred to later releases)

- Book-based new-player guide (in lieu of a first-join chat greeting).
- BodyHealth plugin integration for body-part HP visibility, damage tracking, and bandage/splint heal mechanics. The 1.3.1 bandage / splint / medical_kit items are inventory-only until this lands.
- Pet-origin tracking for `FIRST_PET_EATEN` hint — requires stamping an NBT flag on tamed-origin meat drops.
- Text-mode HUD fallback — not planned; the resource pack is required.
- `#17` IsLethal on legs/arms — needs design playtest.
- `#14` Golden Feast cost/benefit rebalance.
- `#25` Nightmare recovery panic ledge.
- `#49-50` Dynamic Surroundings hosting + enablement decision.
- Translation extraction for Ice and Fire, Spartan and Fire, Dynamic Surroundings, Baubles modules (beyond the minimum in 1.3.1).

