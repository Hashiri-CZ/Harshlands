/*
    Copyright (C) 2025  Hashiri_

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
package cz.hashiri.harshlands.firstaid;

import cz.hashiri.harshlands.data.HLModule;
import cz.hashiri.harshlands.hints.HintKey;
import cz.hashiri.harshlands.hints.HintsModule;
import cz.hashiri.harshlands.locale.Messages;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class Body {

    private final FaModule module;
    private final Location playerLoc;
    private final FileConfiguration config;

    private double leftLegHealth;
    private double rightLegHealth;
    private double leftArmHealth;
    private double rightArmHealth;
    private double leftFootHealth;
    private double rightFootHealth;
    private double torsoHealth;
    private double headHealth;

    public Body(@Nonnull FaModule module, @Nonnull Location playerLoc) {
        this.module = module;
        this.playerLoc = playerLoc.clone();
        this.config = module.getUserConfig().getConfig();
    }

    public @Nullable BodyPart getHitBodyPart(@Nonnull Location loc) {
        return null;
    }

    public double getHealth(@Nonnull BodyPart organType) {
        return switch (organType) {
            case LEFT_LEG -> leftLegHealth;
            case RIGHT_LEG -> rightLegHealth;
            case LEFT_ARM -> leftArmHealth;
            case RIGHT_ARM -> rightArmHealth;
            case LEFT_FOOT -> leftFootHealth;
            case RIGHT_FOOT -> rightFootHealth;
            case TORSO -> torsoHealth;
            case HEAD -> headHealth;
            default -> -1;
        };
    }

    public void setHealth(@Nonnull BodyPart organType, @Nonnegative double health) {
        health = Math.max(health, 0); // ensure health is non-negative

        switch (organType) {
            case LEFT_LEG   -> leftLegHealth  = health;
            case RIGHT_LEG  -> rightLegHealth = health;
            case LEFT_ARM   -> leftArmHealth  = health;
            case RIGHT_ARM  -> rightArmHealth = health;
            case LEFT_FOOT  -> leftFootHealth  = health;
            case RIGHT_FOOT -> rightFootHealth = health;
            case TORSO      -> torsoHealth = health;
            case HEAD       -> headHealth  = health;
        }
    }

    /**
     * Applies damage to the given body part. When the part's HP transitions from >0 to ≤0
     * (a "break"), sends the appropriate injury chat message to the player and fires the
     * FIRST_BROKEN_LIMB hint. Head/torso fire their critical messages when HP reaches ≤1
     * (since those parts being fully at 0 means death — the warn fires before that).
     *
     * @param organType the body part being damaged
     * @param amount    raw damage amount (positive)
     * @param player    the owning player (for messages and hints)
     * @return true if this damage caused a break/critical transition
     */
    public boolean applyDamage(@Nonnull BodyPart organType, @Nonnegative double amount, @Nonnull Player player) {
        double before = getHealth(organType);
        double after  = Math.max(before - amount, 0);
        setHealth(organType, after);

        boolean transitioned = false;

        switch (organType) {
            case HEAD -> {
                // Critical when HP drops to ≤1 (lethal before 0)
                if (before > 1.0 && after <= 1.0) {
                    player.sendMessage(Messages.get("firstaid.damage.head_critical"));
                    transitioned = true;
                }
            }
            case TORSO -> {
                if (before > 1.0 && after <= 1.0) {
                    player.sendMessage(Messages.get("firstaid.damage.torso_critical"));
                    transitioned = true;
                }
            }
            case LEFT_LEG -> {
                if (before > 0.0 && after <= 0.0) {
                    // Check if right leg is also already broken for combined message
                    if (rightLegHealth <= 0.0) {
                        player.sendMessage(Messages.get("firstaid.damage.both_legs_broken"));
                    } else {
                        player.sendMessage(Messages.get("firstaid.damage.left_leg_broken"));
                    }
                    transitioned = true;
                }
            }
            case RIGHT_LEG -> {
                if (before > 0.0 && after <= 0.0) {
                    if (leftLegHealth <= 0.0) {
                        player.sendMessage(Messages.get("firstaid.damage.both_legs_broken"));
                    } else {
                        player.sendMessage(Messages.get("firstaid.damage.right_leg_broken"));
                    }
                    transitioned = true;
                }
            }
            case LEFT_ARM -> {
                if (before > 0.0 && after <= 0.0) {
                    player.sendMessage(Messages.get("firstaid.damage.left_arm_broken"));
                    transitioned = true;
                }
            }
            case RIGHT_ARM -> {
                if (before > 0.0 && after <= 0.0) {
                    player.sendMessage(Messages.get("firstaid.damage.right_arm_broken"));
                    transitioned = true;
                }
            }
            case LEFT_FOOT -> {
                if (before > 0.0 && after <= 0.0) {
                    player.sendMessage(Messages.get("firstaid.damage.left_foot_broken"));
                    transitioned = true;
                }
            }
            case RIGHT_FOOT -> {
                if (before > 0.0 && after <= 0.0) {
                    player.sendMessage(Messages.get("firstaid.damage.right_foot_broken"));
                    transitioned = true;
                }
            }
        }

        // Fire FIRST_BROKEN_LIMB hint on first limb break (not head/torso criticals)
        if (transitioned && organType != BodyPart.HEAD && organType != BodyPart.TORSO) {
            HintsModule hints = (HintsModule) HLModule.getModule(HintsModule.NAME);
            if (hints != null) hints.sendHint(player, HintKey.FIRST_BROKEN_LIMB);
        }

        return transitioned;
    }


    public double getMaximumHealth(@Nonnull BodyPart organType) {
        return -1;
    }

    public enum BodyPart {
        LEFT_LEG, RIGHT_LEG, LEFT_FOOT, RIGHT_FOOT, LEFT_ARM, RIGHT_ARM, HEAD, TORSO
    }
}

