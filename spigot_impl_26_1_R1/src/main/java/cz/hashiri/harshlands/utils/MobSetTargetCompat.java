package cz.hashiri.harshlands.utils;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.bukkit.event.entity.EntityTargetEvent;

import java.lang.reflect.Method;

// Paper 1.21.11+ removed the `boolean fireEvent` parameter from Mob#setTarget.
// Spigot still exposes the 3-arg overload; compiling against Spigot and running
// on Paper/Purpur throws NoSuchMethodError. Bind whichever overload exists at
// class-load time and route calls through it.
public final class MobSetTargetCompat {

    private static final Method METHOD;
    private static final boolean HAS_FIRE_EVENT_PARAM;

    static {
        Method found = null;
        boolean hasFireEvent = false;
        try {
            found = Mob.class.getMethod("setTarget", LivingEntity.class,
                    EntityTargetEvent.TargetReason.class, boolean.class);
            hasFireEvent = true;
        } catch (NoSuchMethodException ignored) {
            try {
                found = Mob.class.getMethod("setTarget", LivingEntity.class,
                        EntityTargetEvent.TargetReason.class);
            } catch (NoSuchMethodException ignored2) {
                // leave null; setTarget(Mob, LivingEntity, TargetReason) will fall back
            }
        }
        METHOD = found;
        HAS_FIRE_EVENT_PARAM = hasFireEvent;
    }

    private MobSetTargetCompat() {
    }

    public static void setTarget(Mob mob, LivingEntity target, EntityTargetEvent.TargetReason reason) {
        try {
            if (METHOD == null) {
                mob.setTarget(target);
                return;
            }
            if (HAS_FIRE_EVENT_PARAM) {
                METHOD.invoke(mob, target, reason, true);
            } else {
                METHOD.invoke(mob, target, reason);
            }
        } catch (ReflectiveOperationException e) {
            mob.setTarget(target);
        }
    }
}
