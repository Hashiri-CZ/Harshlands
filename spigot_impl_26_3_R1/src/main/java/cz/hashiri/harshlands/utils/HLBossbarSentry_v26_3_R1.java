/*
    Copyright (C) 2025  Hashiri_
    GPL-3.0-or-later — see InternalsProvider.java for full text.
 */
package cz.hashiri.harshlands.utils;

import cz.hashiri.harshlands.HLPlugin;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;

import java.lang.reflect.Field;
import java.util.UUID;

/**
 * Per-version bossbar Sentry. Contains only NMS-touching reflection;
 * dispatch logic lives in the {@link BossbarSentry} core base class.
 *
 * <p>The static {@code locateIdField} / {@code locateOperationField}
 * helpers are byte-identical across the two {@code spigot_impl_*}
 * modules. They cannot share a source file because each module
 * resolves {@code ClientboundBossEventPacket} from its own NMS jar.
 * Treat the duplication as architectural, not accidental — keep the
 * two files in lockstep.</p>
 */
public final class HLBossbarSentry_v26_3_R1 extends BossbarSentry {

    private static final Field ID_FIELD = locateIdField();
    private static final Field OPERATION_FIELD = locateOperationField();

    private static Field locateIdField() {
        try {
            Field f = ClientboundBossEventPacket.class.getDeclaredField("id");
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException primary) {
            // Fall back to type scan if Mojang ever renames the field.
            for (Field f : ClientboundBossEventPacket.class.getDeclaredFields()) {
                if (f.getType() == UUID.class) {
                    f.setAccessible(true);
                    return f;
                }
            }
            throw new IllegalStateException(
                    "Could not locate UUID field on ClientboundBossEventPacket", primary);
        }
    }

    private static Field locateOperationField() {
        try {
            Field f = ClientboundBossEventPacket.class.getDeclaredField("operation");
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException primary) {
            // Fall back: first interface- or enum-typed field that appears AFTER
            // the UUID field in declared order. The ordering guard avoids a false
            // positive on a hypothetical future packet field of object type that
            // lands earlier than `id` in the layout.
            boolean seenUuid = false;
            for (Field f : ClientboundBossEventPacket.class.getDeclaredFields()) {
                Class<?> t = f.getType();
                if (t == UUID.class) {
                    seenUuid = true;
                    continue;
                }
                if (seenUuid && (t.isInterface() || t.isEnum())) {
                    f.setAccessible(true);
                    return f;
                }
            }
            throw new IllegalStateException(
                    "Could not locate operation field on ClientboundBossEventPacket", primary);
        }
    }

    public HLBossbarSentry_v26_3_R1(HLPlugin plugin, UUID playerUuid) {
        super(plugin, playerUuid);
    }

    @Override
    protected UUID parseAddUuid(Object msg) throws IllegalAccessException {
        if (!(msg instanceof ClientboundBossEventPacket pkt)) return null;
        Object op = OPERATION_FIELD.get(pkt);
        if (op == null) return null;
        boolean isAdd = "AddOperation".equals(op.getClass().getSimpleName())
                || (op instanceof Enum<?> e && "ADD".equals(e.name()));
        if (!isAdd) return null;
        return (UUID) ID_FIELD.get(pkt);
    }
}
