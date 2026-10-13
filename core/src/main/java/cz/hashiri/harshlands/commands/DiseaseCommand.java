/*
    Copyright (C) 2026  Hashiri_

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.hashiri.harshlands.commands;

import cz.hashiri.harshlands.HLPlugin;
import cz.hashiri.harshlands.data.HLModule;
import cz.hashiri.harshlands.data.HLPlayer;
import cz.hashiri.harshlands.data.disease.ActiveInfection;
import cz.hashiri.harshlands.data.disease.DataModule;
import cz.hashiri.harshlands.disease.DiseaseModule;
import cz.hashiri.harshlands.disease.model.Disease;
import cz.hashiri.harshlands.disease.trigger.TaintedItemSource;
import cz.hashiri.harshlands.locale.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * {@code /hl disease …} — the operator's window into the disease engine.
 *
 * <p>The engine is otherwise deliberately opaque: contraction is probabilistic, incubation hides
 * the illness, and several diseases gate on state that is slow to reach by hand (Sybok needs 8
 * prior contractions, Battle Trauma 3 deaths in a rolling window, Malnutrition several sustained
 * checks). Without this command, verifying a disease end-to-end in-game means waiting out
 * conditions that take the better part of an hour, so this exists to make live testing tractable.
 *
 * <p>Read-only subcommands ({@code list}, {@code status}) require
 * {@code harshlands.command.disease}; anything that mutates player state requires
 * {@code harshlands.command.disease.admin}.
 */
final class DiseaseCommand {

    private final HLPlugin plugin;

    DiseaseCommand(HLPlugin plugin) {
        this.plugin = plugin;
    }

    /** Subcommand names, for tab completion and dispatch. */
    static final List<String> SUBCOMMANDS =
        List.of("list", "status", "infect", "stage", "cure", "immune", "taint");

    /** Subcommands whose second argument is a player name. */
    static final List<String> PLAYER_ARG_SUBCOMMANDS =
        List.of("status", "infect", "stage", "cure", "immune");

    /** Subcommands whose third argument is a disease id. */
    static final List<String> DISEASE_ARG_SUBCOMMANDS =
        List.of("infect", "stage", "cure", "immune");

    /** Ids of every currently loaded disease, for tab completion. Empty if the module is off. */
    static List<String> loadedDiseaseIds() {
        DiseaseModule module = module();
        if (module == null) return List.of();
        List<String> ids = new ArrayList<>();
        for (Disease d : module.getRegistry().all()) ids.add(d.id());
        java.util.Collections.sort(ids);
        return ids;
    }

    private static DiseaseModule module() {
        HLModule module = HLModule.getModule(DiseaseModule.NAME);
        return (module instanceof DiseaseModule diseaseModule && module.isGloballyEnabled())
            ? diseaseModule : null;
    }

    /** @return always true — the caller returns this straight back to Bukkit. */
    boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("harshlands.command.disease")) {
            Messages.of("commands.no_permission").send(sender);
            return true;
        }

        DiseaseModule module = module();
        if (module == null) {
            Messages.of("commands.disease.module_disabled").send(sender);
            return true;
        }

        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }

        String sub = args[1].toLowerCase();
        boolean mutating = !sub.equals("list") && !sub.equals("status");
        if (mutating && !sender.hasPermission("harshlands.command.disease.admin")) {
            Messages.of("commands.no_permission").send(sender);
            return true;
        }

        switch (sub) {
            case "list" -> list(sender, module);
            case "status" -> status(sender, module, args);
            case "infect" -> infect(sender, module, args);
            case "stage" -> stage(sender, module, args);
            case "cure" -> cure(sender, module, args);
            case "immune" -> immune(sender, module, args);
            case "taint" -> taint(sender);
            default -> sendUsage(sender);
        }
        return true;
    }

    // ── Read-only ────────────────────────────────────────────────────────────

    private void list(CommandSender sender, DiseaseModule module) {
        Collection<Disease> all = module.getRegistry().all();
        Messages.of("commands.disease.list.header").with("count", String.valueOf(all.size())).send(sender);
        List<Disease> sorted = new ArrayList<>(all);
        sorted.sort(java.util.Comparator.comparing(Disease::id));
        String noCure = Messages.of("commands.disease.list.no_cure").build();
        for (Disease d : sorted) {
            String cure = (d.cureItemId() == null || d.cureItemId().isBlank()) ? noCure : d.cureItemId();
            Messages.of("commands.disease.list.entry")
                .with("disease", d.displayName())
                .with("id", d.id())
                .with("stages", String.valueOf(d.maxStage()))
                .with("cure", cure)
                .send(sender);
        }
    }

    private void status(CommandSender sender, DiseaseModule module, String[] args) {
        Player target = resolveTarget(sender, args, 2);
        if (target == null) return;
        DataModule dm = dataModule(sender, target);
        if (dm == null) return;

        long now = System.currentTimeMillis();
        Messages.of("commands.disease.status.header").with("target", target.getName()).send(sender);
        Messages.of("commands.disease.status.exposure")
            .with("count", String.valueOf(dm.getContractionCount())).send(sender);

        Collection<ActiveInfection> infections = dm.getActiveInfections();
        if (infections.isEmpty()) {
            Messages.of("commands.disease.status.healthy").send(sender);
        }
        for (ActiveInfection inf : infections) {
            Disease disease = module.getRegistry().get(inf.diseaseId());
            // A saved infection can outlive its config entry (disease disabled or renamed);
            // show the raw id rather than dropping the row silently.
            String name = disease != null ? disease.displayName() : inf.diseaseId();
            if (inf.isIncubating()) {
                Messages.of("commands.disease.status.incubating")
                    .with("disease", name)
                    .with("ticks", String.valueOf(inf.getIncubationLeft()))
                    .send(sender);
            } else {
                Messages.of("commands.disease.status.active")
                    .with("disease", name)
                    .with("stage", String.valueOf(inf.getStage()))
                    .with("max_stage", String.valueOf(disease != null ? disease.maxStage() : inf.getStage()))
                    .with("ticks", String.valueOf(inf.getTicksInStage()))
                    .send(sender);
            }
        }

        for (Map.Entry<String, Long> entry : dm.getImmunities().entrySet()) {
            if (entry.getValue() <= now) continue; // expired but not yet pruned
            Disease disease = module.getRegistry().get(entry.getKey());
            Messages.of("commands.disease.status.immune")
                .with("disease", disease != null ? disease.displayName() : entry.getKey())
                .with("seconds", String.valueOf((entry.getValue() - now) / 1000L))
                .send(sender);
        }
    }

    // ── Mutating ─────────────────────────────────────────────────────────────

    private void infect(CommandSender sender, DiseaseModule module, String[] args) {
        if (args.length < 4) { sendUsage(sender); return; }
        Player target = resolveTarget(sender, args, 2);
        if (target == null) return;
        Disease disease = resolveDisease(sender, module, args[3]);
        if (disease == null) return;
        DataModule dm = dataModule(sender, target);
        if (dm == null) return;

        // Clear first: re-infecting an already-infected player would otherwise leave the old
        // stage's potion effects and tracker entries running against the new infection.
        if (dm.hasInfection(disease.id())) {
            module.clearAllSymptoms(target, disease);
        }
        // Immunity would silently undo the infection on the very next check.
        dm.clearImmunity(disease.id());
        dm.contract(disease.id(), disease.incubationTicks(), System.currentTimeMillis());
        // Count it exactly as a natural contraction would. Sybok's trigger keys off this
        // lifetime counter, so without this its accumulation path could not be exercised at
        // all — and it also means repeated test infections eventually surface Sybok, which is
        // the real behaviour rather than an artefact of the command.
        dm.incrementContractionCount();

        if (args.length >= 5) {
            Integer requested = parseStage(sender, args[4], disease.maxStage());
            if (requested == null) return;
            applyStage(dm, disease, requested);
        }
        persist(dm);
        Messages.of("commands.disease.infected")
            .with("target", target.getName()).with("disease", disease.displayName()).send(sender);
    }

    private void stage(CommandSender sender, DiseaseModule module, String[] args) {
        if (args.length < 5) { sendUsage(sender); return; }
        Player target = resolveTarget(sender, args, 2);
        if (target == null) return;
        Disease disease = resolveDisease(sender, module, args[3]);
        if (disease == null) return;
        DataModule dm = dataModule(sender, target);
        if (dm == null) return;
        Integer requested = parseStage(sender, args[4], disease.maxStage());
        if (requested == null) return;

        if (!dm.hasInfection(disease.id())) {
            dm.clearImmunity(disease.id());
            dm.contract(disease.id(), disease.incubationTicks(), System.currentTimeMillis());
        } else {
            // Drop the outgoing stage's effects before the new stage's are applied next tick.
            module.clearAllSymptoms(target, disease);
        }
        applyStage(dm, disease, requested);
        persist(dm);
        Messages.of("commands.disease.staged")
            .with("target", target.getName())
            .with("disease", disease.displayName())
            .with("stage", String.valueOf(requested))
            .send(sender);
    }

    private void cure(CommandSender sender, DiseaseModule module, String[] args) {
        if (args.length < 4) { sendUsage(sender); return; }
        Player target = resolveTarget(sender, args, 2);
        if (target == null) return;
        DataModule dm = dataModule(sender, target);
        if (dm == null) return;

        if (args[3].equalsIgnoreCase("all")) {
            int cleared = 0;
            // Copy first: removeInfection mutates the map backing getActiveInfections().
            for (ActiveInfection inf : new ArrayList<>(dm.getActiveInfections())) {
                Disease disease = module.getRegistry().get(inf.diseaseId());
                if (disease != null) module.clearAllSymptoms(target, disease);
                dm.removeInfection(inf.diseaseId());
                cleared++;
            }
            persist(dm);
            Messages.of("commands.disease.cured_all")
                .with("target", target.getName()).with("count", String.valueOf(cleared)).send(sender);
            return;
        }

        Disease disease = resolveDisease(sender, module, args[3]);
        if (disease == null) return;
        if (!dm.hasInfection(disease.id())) {
            Messages.of("commands.disease.not_infected")
                .with("target", target.getName()).with("disease", disease.displayName()).send(sender);
            return;
        }
        module.clearAllSymptoms(target, disease);
        dm.removeInfection(disease.id());
        persist(dm);
        Messages.of("commands.disease.cured")
            .with("target", target.getName()).with("disease", disease.displayName()).send(sender);
    }

    private void immune(CommandSender sender, DiseaseModule module, String[] args) {
        if (args.length < 5) { sendUsage(sender); return; }
        Player target = resolveTarget(sender, args, 2);
        if (target == null) return;
        Disease disease = resolveDisease(sender, module, args[3]);
        if (disease == null) return;
        DataModule dm = dataModule(sender, target);
        if (dm == null) return;

        long ticks;
        try {
            ticks = Long.parseLong(args[4]);
        } catch (NumberFormatException e) {
            Messages.of("commands.disease.invalid_ticks").send(sender);
            return;
        }
        if (ticks < 0) {
            Messages.of("commands.disease.invalid_ticks").send(sender);
            return;
        }

        if (ticks == 0) {
            dm.clearImmunity(disease.id());
            persist(dm);
            Messages.of("commands.disease.immunity_cleared")
                .with("target", target.getName()).with("disease", disease.displayName()).send(sender);
            return;
        }
        dm.grantImmunity(disease.id(), System.currentTimeMillis() + ticks * 50L);
        persist(dm);
        Messages.of("commands.disease.immunity_granted")
            .with("target", target.getName())
            .with("disease", disease.displayName())
            .with("ticks", String.valueOf(ticks))
            .send(sender);
    }

    private void taint(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Messages.of("commands.disease.no_item").send(sender);
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            Messages.of("commands.disease.no_item").send(sender);
            return;
        }
        if (TaintedItemSource.isTainted(held)) {
            Messages.of("commands.disease.tainted_already").send(sender);
            return;
        }
        TaintedItemSource.taint(held);
        Messages.of("commands.disease.tainted").with("item", held.getType().name()).send(sender);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Moves an infection to {@code stage}. Stage 0 rewinds it to a fresh incubation; any other
     * stage skips incubation outright so the symptoms show up on the next check.
     */
    private void applyStage(DataModule dm, Disease disease, int stage) {
        ActiveInfection inf = dm.getInfection(disease.id());
        if (inf == null) return;
        inf.setStage(stage);
        inf.setTicksInStage(0L);
        inf.setIncubationLeft(stage == 0 ? disease.incubationTicks() : 0L);
        dm.markDirty();
    }

    private Integer parseStage(CommandSender sender, String raw, int maxStage) {
        int stage;
        try {
            stage = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            Messages.of("commands.disease.invalid_stage")
                .with("max_stage", String.valueOf(maxStage)).send(sender);
            return null;
        }
        if (stage < 0 || stage > maxStage) {
            Messages.of("commands.disease.invalid_stage")
                .with("max_stage", String.valueOf(maxStage)).send(sender);
            return null;
        }
        return stage;
    }

    /** Resolves the player at {@code index}, falling back to the sender when it is omitted. */
    private Player resolveTarget(CommandSender sender, String[] args, int index) {
        if (args.length > index) {
            Player target = Bukkit.getPlayerExact(args[index]);
            if (target == null) Messages.of("commands.invalid_target").send(sender);
            return target;
        }
        if (sender instanceof Player self) return self;
        Messages.of("commands.invalid_target").send(sender);
        return null;
    }

    private Disease resolveDisease(CommandSender sender, DiseaseModule module, String id) {
        Disease disease = module.getRegistry().get(id.toLowerCase());
        if (disease == null) {
            Messages.of("commands.disease.unknown_disease").with("disease", id).send(sender);
        }
        return disease;
    }

    private DataModule dataModule(CommandSender sender, Player target) {
        HLPlayer hlPlayer = HLPlayer.getPlayers().get(target.getUniqueId());
        DataModule dm = hlPlayer != null ? hlPlayer.getDiseaseDataModule() : null;
        if (dm == null) {
            Messages.of("commands.disease.no_data").with("target", target.getName()).send(sender);
        }
        return dm;
    }

    /** Mirrors the write-through used elsewhere for admin edits, so a crash cannot lose them. */
    private void persist(DataModule dm) {
        plugin.getScheduler().runAsync(dm::saveData);
    }

    private void sendUsage(CommandSender sender) {
        for (String line : Messages.getList("commands.disease.usage")) {
            sender.sendMessage(line);
        }
    }
}
