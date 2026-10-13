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
package cz.hashiri.harshlands.disease;

import cz.hashiri.harshlands.locale.Messages;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Optional player-facing feedback as an infection progresses.
 *
 * <p>Every message is deliberately vague and never names the disease. Naming it is the medical
 * kit's job — if these messages identified the illness, the diagnostic item would be pointless.
 * The trade is intentional: the player learns that something is wrong (and roughly how bad),
 * but still has to craft a kit to learn what it is.
 *
 * <p>All three channels are individually configurable under {@code Notifications} in
 * {@code Settings/disease.yml}; with all of them off this is a no-op and the module behaves
 * exactly as it did before the option existed.
 */
public final class ProgressionNotifier {

    /** Which message a stage transition warrants, or {@code null} for silence. */
    public enum Transition {
        /** Incubation ended and stage 1 began — the first moment symptoms appear. */
        ONSET,
        /** The illness advanced to a later stage. */
        WORSENED,
        /** Mitigation pushed the illness back to an earlier stage. */
        EASED
    }

    private final boolean onContract;
    private final boolean onStageChange;
    private final boolean onNaturalCure;

    public ProgressionNotifier(boolean onContract, boolean onStageChange, boolean onNaturalCure) {
        this.onContract = onContract;
        this.onStageChange = onStageChange;
        this.onNaturalCure = onNaturalCure;
    }

    /** A notifier that says nothing — the default when the config section is absent. */
    public static ProgressionNotifier silent() {
        return new ProgressionNotifier(false, false, false);
    }

    public boolean isOnContractEnabled()    { return onContract; }
    public boolean isOnStageChangeEnabled() { return onStageChange; }
    public boolean isOnNaturalCureEnabled() { return onNaturalCure; }

    public static ProgressionNotifier fromConfig(ConfigurationSection section) {
        if (section == null) return silent();
        return new ProgressionNotifier(
            section.getBoolean("OnContract", false),
            section.getBoolean("OnStageChange", true),
            section.getBoolean("OnNaturalCure", true));
    }

    /**
     * Classifies a stage transition. Pure, so the wording rules are testable without Bukkit.
     *
     * @param fromStage stage before the tick (0 = incubating)
     * @param toStage   stage after the tick
     * @return the transition to announce, or {@code null} when nothing changed
     */
    public static Transition classify(int fromStage, int toStage) {
        if (toStage == fromStage) return null;
        // Leaving incubation is an onset, not a worsening: the player felt nothing until now.
        if (fromStage == 0) return Transition.ONSET;
        return toStage > fromStage ? Transition.WORSENED : Transition.EASED;
    }

    /** A trigger succeeded. Fires during incubation, before any symptom is visible. */
    public void notifyContracted(Player player) {
        if (onContract) Messages.of("disease.progression.contracted").send(player);
    }

    /** The infection moved between stages (including out of incubation). */
    public void notifyStageChange(Player player, int fromStage, int toStage) {
        if (!onStageChange) return;
        Transition transition = classify(fromStage, toStage);
        if (transition == null) return;
        switch (transition) {
            case ONSET -> Messages.of("disease.progression.onset").send(player);
            case WORSENED -> Messages.of("disease.progression.worsened").send(player);
            case EASED -> Messages.of("disease.progression.eased").send(player);
        }
    }

    /** The infection cleared on its own (mitigation regressed it past stage 1). */
    public void notifyNaturalCure(Player player) {
        if (onNaturalCure) Messages.of("disease.progression.recovered").send(player);
    }
}
