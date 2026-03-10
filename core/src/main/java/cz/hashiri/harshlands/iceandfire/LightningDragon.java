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
import cz.hashiri.harshlands.spartanandfire.ElectrocuteTask;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;

public interface LightningDragon extends Dragon {

    FileConfiguration CONFIG = HLModule.getModule(IceFireModule.NAME).getUserConfig().getConfig();

    default void performMeleeAttack(LivingEntity entity) {
        double stageMultiplier = CONFIG.getDouble("Dragon.LightningDragon.MeleeAttack.StageMultiplier.Stage" + getStage());
        entity.damage(CONFIG.getDouble("Dragon.LightningDragon.MeleeAttack.BaseDamage") * stageMultiplier, getEntity());
    }

    default void performSpecialAbility(LivingEntity entity) {
        Location loc = entity.getLocation();
        if (CONFIG.getBoolean("Dragon.LightningDragon.ElectrocuteAbility.SummonCosmeticLightning")) {
            loc.getWorld().strikeLightningEffect(loc);
        }
        else {
            loc.getWorld().strikeLightning(loc);
        }
        if (!ElectrocuteTask.hasTask(entity.getUniqueId())) {
            new ElectrocuteTask(HLPlugin.getPlugin(), getStage(), entity).start();
        }
    }

    default void triggerBreathAttack(Location target) {
        new LightningBreath(this, target, HLPlugin.getPlugin()).start();
    }

    default void triggerExplosionAttack(Location target) {
        new LightningExplosionAttack(this, target, HLPlugin.getPlugin()).start();
    }
}

