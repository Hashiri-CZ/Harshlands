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
package cz.hashiri.harshlands.iceandfire;

import cz.hashiri.harshlands.data.HLModule;
import cz.hashiri.harshlands.rsv.HLPlugin;
import cz.hashiri.harshlands.spartanandfire.BurnTask;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;

public interface FireDragon extends Dragon {

    FileConfiguration CONFIG = HLModule.getModule(IceFireModule.NAME).getUserConfig().getConfig();

    @Override
    default void performMeleeAttack(LivingEntity entity) {
        double stageMultiplier = CONFIG.getDouble("Dragon.FireDragon.MeleeAttack.StageMultiplier.Stage" + getStage());
        entity.damage(CONFIG.getDouble("Dragon.FireDragon.MeleeAttack.BaseDamage") * stageMultiplier, getEntity());
    }

    @Override
    default void performSpecialAbility(LivingEntity entity) {
        double stageMultiplier = CONFIG.getDouble("Dragon.FireDragon.InfernoAbility.StageMultiplier.Stage" + getStage());
        if (!BurnTask.hasTask(entity.getUniqueId())) {
            new BurnTask(HLPlugin.getPlugin(), entity, (int) (CONFIG.getInt("Dragon.FireDragon.InfernoAbility.FireTicks") * stageMultiplier), CONFIG.getInt("Dragon.FireDragon.InfernoAbility.TickPeriod")).start();
        }
    }

    @Override
    default void triggerBreathAttack(Location target) {
        new FireBreath(this, target, HLPlugin.getPlugin()).start();
    }

    @Override
    default void triggerExplosionAttack(Location target) {
        new FireExplosionAttack(this, target, HLPlugin.getPlugin());
    }
}

