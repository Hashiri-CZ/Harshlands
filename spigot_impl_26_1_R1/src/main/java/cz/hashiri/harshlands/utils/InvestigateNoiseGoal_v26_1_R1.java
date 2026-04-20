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
package cz.hashiri.harshlands.utils;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class InvestigateNoiseGoal_v26_1_R1 extends Goal {

    private enum State { TRAVELING, LINGERING, COMPLETED }

    private final Mob mob;
    private final double targetX;
    private final double targetY;
    private final double targetZ;
    private final double speed;
    private final PathNavigation navigation;

    private State state = State.TRAVELING;
    private boolean finished = false;
    private int travelTicks = 0;
    private int lingerTicks = 0;
    private int lingerDuration;
    private int nextLookChangeTick = 0;

    private static final int TRAVEL_TIMEOUT = 200;
    private static final double ARRIVAL_DISTANCE_SQ = 4.0; // 2 blocks squared
    private static final double PLAYER_SCAN_RANGE = 8.0;
    private static final double FAR_FROM_TARGET_SQ = 256.0; // 16 blocks squared
    private static final int PLAYER_SCAN_INTERVAL = 5;

    public InvestigateNoiseGoal_v26_1_R1(Mob mob, double x, double y, double z, double speed) {
        this.mob = mob;
        this.targetX = x;
        this.targetY = y;
        this.targetZ = z;
        this.speed = speed;
        this.navigation = mob.getNavigation();
        this.lingerDuration = 60 + ThreadLocalRandom.current().nextInt(41); // 60-100
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return !finished && mob.getTarget() == null;
    }

    @Override
    public boolean canContinueToUse() {
        return state != State.COMPLETED && mob.isAlive() && mob.getTarget() == null;
    }

    @Override
    public void start() {
        state = State.TRAVELING;
        travelTicks = 0;
        navigation.moveTo(targetX, targetY, targetZ, speed);
    }

    @Override
    public void tick() {
        switch (state) {
            case TRAVELING -> tickTraveling();
            case LINGERING -> tickLingering();
            default -> {}
        }
    }

    private void tickTraveling() {
        travelTicks++;

        double dx = mob.getX() - targetX;
        double dy = mob.getY() - targetY;
        double dz = mob.getZ() - targetZ;
        double distSq = dx * dx + dy * dy + dz * dz;

        if (distSq <= ARRIVAL_DISTANCE_SQ || travelTicks >= TRAVEL_TIMEOUT || navigation.isDone()) {
            // If mob never got close to the noise source, skip lingering
            if (distSq > FAR_FROM_TARGET_SQ) {
                state = State.COMPLETED;
            } else {
                state = State.LINGERING;
                lingerTicks = 0;
            }
            navigation.stop();
        }
    }

    private void tickLingering() {
        lingerTicks++;

        // Random look rotation
        if (lingerTicks >= nextLookChangeTick) {
            float yaw = ThreadLocalRandom.current().nextFloat() * 360.0f;
            mob.getLookControl().setLookAt(
                    mob.getX() + Math.sin(Math.toRadians(yaw)) * 5.0,
                    mob.getEyeY(),
                    mob.getZ() + Math.cos(Math.toRadians(yaw)) * 5.0
            );
            nextLookChangeTick = lingerTicks + 20 + ThreadLocalRandom.current().nextInt(21); // 20-40
        }

        // Scan for players every few ticks (hasLineOfSight is expensive)
        if (lingerTicks % PLAYER_SCAN_INTERVAL == 0) {
            AABB scanBox = mob.getBoundingBox().inflate(PLAYER_SCAN_RANGE);
            List<Player> players = mob.level().getEntitiesOfClass(Player.class, scanBox,
                    p -> !p.isSpectator() && !p.isCreative() && mob.hasLineOfSight(p));
            if (!players.isEmpty()) {
                MobSetTargetCompat_v26_1_R1.setTarget(mob, players.getFirst(),
                        org.bukkit.event.entity.EntityTargetEvent.TargetReason.CLOSEST_PLAYER);
                state = State.COMPLETED;
                return;
            }
        }

        if (lingerTicks >= lingerDuration) {
            state = State.COMPLETED;
        }
    }

    @Override
    public void stop() {
        navigation.stop();
        finished = true;
    }
}
