/*
    Copyright (C) 2025  Val_Mobile

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

import cz.hashiri.harshlands.HLPlugin;
import cz.hashiri.harshlands.baubles.EndermanAlly;
import cz.hashiri.harshlands.iceandfire.*;
import cz.hashiri.harshlands.utils.recipe.RecipeDisplayRegistry;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import io.netty.channel.ChannelPipeline;
import net.minecraft.network.Connection;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.world.TimeSkipEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.inventory.SmithingRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;

public class v26_3_R1 extends InternalsProvider {

    private RecipeDisplayPatcher_v26_3_R1 recipeDisplayPatcher;

    @Override
    public EndermanAlly spawnEndermanAlly(Player owner, Location loc) {
        return new EndermanAlly_v26_3_R1(owner, loc);
    }

    @Override
    public FireDragon spawnFireDragon(Location loc) {
        return new FireDragon_v26_3_R1(loc);
    }

    @Override
    public FireDragon spawnFireDragon(Location loc, int stage) {
        return new FireDragon_v26_3_R1(loc, stage);
    }

    @Override
    public FireDragon spawnFireDragon(Location loc, DragonVariant variant) {
        return new FireDragon_v26_3_R1(loc, variant);
    }

    @Override
    public FireDragon spawnFireDragon(Location loc, DragonVariant variant, int stage) {
        return new FireDragon_v26_3_R1(loc, variant, stage);
    }

    @Override
    public IceDragon spawnIceDragon(Location loc) {
        return new IceDragon_v26_3_R1(loc);
    }

    @Override
    public IceDragon spawnIceDragon(Location loc, int stage) {
        return new IceDragon_v26_3_R1(loc, stage);
    }

    @Override
    public IceDragon spawnIceDragon(Location loc, DragonVariant variant) {
        return new IceDragon_v26_3_R1(loc, variant);
    }

    @Override
    public IceDragon spawnIceDragon(Location loc, DragonVariant variant, int stage) {
        return new IceDragon_v26_3_R1(loc, variant, stage);
    }

    @Override
    public LightningDragon spawnLightningDragon(Location loc) {
        return new LightningDragon_v26_3_R1(loc);
    }

    @Override
    public LightningDragon spawnLightningDragon(Location loc, int stage) {
        return new LightningDragon_v26_3_R1(loc, stage);
    }

    @Override
    public LightningDragon spawnLightningDragon(Location loc, DragonVariant variant) {
        return new LightningDragon_v26_3_R1(loc, variant);
    }

    @Override
    public LightningDragon spawnLightningDragon(Location loc, DragonVariant variant, int stage) {
        return new LightningDragon_v26_3_R1(loc, variant, stage);
    }

    @Override
    public SeaSerpent spawnSeaSerpent(Location loc) {
        return new SeaSerpent_v26_3_R1(loc);
    }

    @Override
    public SeaSerpent spawnSeaSerpent(Location loc, SeaSerpentVariant variant) {
        return new SeaSerpent_v26_3_R1(loc, variant);
    }

    @Override
    public Siren spawnSiren(Location loc) {
        return new Siren_v26_3_R1(loc);
    }

    @Override
    public Tag<Material> getTag(String name) {
        return TagUtils.getTag(Tag.class, name);
    }

    @Override
    public boolean isUndead(Entity entity) {
        return entity instanceof LivingEntity living && Tag.ENTITY_TYPES_UNDEAD.isTagged(living.getType());
    }
    @Override
    public boolean isNetheriteRecipe(SmithingInventory inv) {
        Recipe recipe = inv.getRecipe();

        if (recipe instanceof SmithingRecipe) {
            NamespacedKey key = ((SmithingRecipe) recipe).getKey();

            if (key.getNamespace().equals(NamespacedKey.MINECRAFT)) {
                switch (key.getKey()) {
                    case "netherite_axe_smithing", "netherite_pickaxe_smithing", "netherite_shovel_smithing", "netherite_sword_smithing", "netherite_hoe_smithing", "netherite_helmet_smithing", "netherite_chestplate_smithing", "netherite_leggings_smithing", "netherite_boots_smithing" -> {
                        return true;
                    }
                    default -> {}
                }
            }
        }
        return false;
    }

    @Override
    public void registerEntities() {
        CustomEntities_v26_3_R1.registerEntities();
    }

    @Override
    public void setFreezingView(Player player, int ticks) {
        player.setFreezeTicks(ticks);
    }

    @Override
    public void attack(LivingEntity attacker, Entity defender) {
        if (attacker instanceof Player) {
            ((CraftPlayer) attacker).getHandle().attack(((CraftEntity) defender).getHandle());
        }
        else {
            ((CraftLivingEntity) attacker).getHandle().doHurtTarget(((CraftWorld) defender.getWorld()).getHandle(), ((CraftEntity) defender).getHandle());
        }
    }

    @Override
    public boolean hasItemModel(ItemMeta meta) {
        return meta.hasItemModel();
    }

    @Override
    public NamespacedKey getItemModel(ItemMeta meta) {
        return meta.getItemModel();
    }

    @Override
    public void setItemModel(ItemMeta meta, NamespacedKey key) {
        meta.setItemModel(key);
    }

    @Override
    public boolean hasEquippableComponentModel(ItemMeta meta) {
        return meta.hasEquippable();
    }

    @Override
    public NamespacedKey getEquippableComponentModel(ItemMeta meta) {
        return meta.getEquippable().getModel();
    }

    @Override
    public void setEquippableComponentModel(ItemMeta meta, NamespacedKey key, EquipmentSlot slot) {
        EquippableComponent component = meta.getEquippable();
        component.setSlot(slot);
        component.setModel(key);
        meta.setEquippable(component);
    }

    @Override
    public void applyItemModel(
            @javax.annotation.Nonnull ItemMeta meta,
            int customModelData,
            @javax.annotation.Nullable NamespacedKey itemModelKey,
            @javax.annotation.Nullable NamespacedKey equippableModel,
            @javax.annotation.Nullable EquipmentSlot equippableSlot) {
        if (customModelData > 0) {
            meta.setCustomModelData(customModelData);
            org.bukkit.inventory.meta.components.CustomModelDataComponent component =
                    meta.getCustomModelDataComponent();
            component.setFloats(java.util.List.of((float) customModelData));
            meta.setCustomModelDataComponent(component);
        }
        if (itemModelKey != null) {
            setItemModel(meta, itemModelKey);
        }
        if (equippableModel != null && equippableSlot != null) {
            setEquippableComponentModel(meta, equippableModel, equippableSlot);
        }
    }

    @Override
    public boolean assignInvestigateNoiseGoal(org.bukkit.entity.Mob mob, org.bukkit.Location target) {
        try {
            net.minecraft.world.entity.Mob nmsMob = ((org.bukkit.craftbukkit.entity.CraftMob) mob).getHandle();

            // Remove any existing InvestigateNoiseGoal, stopping running ones to release AI flags
            nmsMob.goalSelector.getAvailableGoals().removeIf(wrappedGoal -> {
                if (wrappedGoal.getGoal() instanceof InvestigateNoiseGoal_v26_3_R1) {
                    if (wrappedGoal.isRunning()) {
                        wrappedGoal.stop();
                    }
                    return true;
                }
                return false;
            });

            nmsMob.goalSelector.addGoal(3,
                    new InvestigateNoiseGoal_v26_3_R1(nmsMob, target.getX(), target.getY(), target.getZ(), 1.0));
            return true;
        } catch (ClassCastException e) {
            return false;
        }
    }

    public static boolean isLookingAtMe(Enderman enderman, net.minecraft.world.entity.player.Player entityhuman) {
        ItemStack itemstack = entityhuman.getInventory().getArmorContents().get(3);
        if (itemstack.is(Blocks.CARVED_PUMPKIN.asItem())) {
            return false;
        }
        else {
            Vec3 vec3d = entityhuman.getViewVector(1.0F).normalize();
            Vec3 vec3d1 = new Vec3(enderman.getX() - entityhuman.getX(), enderman.getEyeY() - entityhuman.getEyeY(), enderman.getZ() - entityhuman.getZ());
            double d0 = vec3d1.length();
            vec3d1 = vec3d1.normalize();
            double d1 = vec3d.dot(vec3d1);
            return d1 > 1.0 - 0.025 / d0 && entityhuman.hasLineOfSight(enderman);
        }
    }

    // Earlier impls carry a copy of vanilla's teleport because Enderman.teleport(double, double,
    // double) was private through MC 26.2. MC 26.3 made it public and changed it (no teleport
    // while riding, never onto #enderman_does_not_teleport_to blocks such as bedrock, new
    // particles) and removed BlockState.blocksMotion() the copy relied on — so delegate.
    public static void teleport(Enderman enderman) {
        enderman.teleport();
    }

    public static boolean teleport(Enderman enderman, double d0, double d1, double d2) {
        return enderman.teleport(d0, d1, d2);
    }

    public static boolean teleportTowards(Enderman enderman, net.minecraft.world.entity.Entity entity) {
        return enderman.teleportTowards(entity);
    }

    @Override
    public void installRecipeDisplayPatcher(HLPlugin plugin, RecipeDisplayRegistry registry) {
        this.recipeDisplayPatcher = new RecipeDisplayPatcher_v26_3_R1(plugin, registry);
        org.bukkit.Bukkit.getPluginManager().registerEvents(this.recipeDisplayPatcher, plugin);
    }

    @Override
    public void uninstallRecipeDisplayPatcher() {
        if (this.recipeDisplayPatcher != null) {
            HandlerList.unregisterAll(this.recipeDisplayPatcher);
            this.recipeDisplayPatcher = null;
        }
    }

    private static final String SENTRY_PIPELINE_NAME = "harshlands_bossbar_sentry";

    /**
     * Reflectively obtains the Netty ChannelPipeline for the given player.
     *
     * <p>The {@code connection} field on {@code ServerCommonPacketListenerImpl} is
     * {@code protected}, so it is not directly accessible from this package.
     * We obtain it via reflection once and cache the Field for reuse.</p>
     */
    private static final java.lang.reflect.Field CONNECTION_FIELD;
    static {
        java.lang.reflect.Field f = null;
        try {
            f = net.minecraft.server.network.ServerCommonPacketListenerImpl.class
                    .getDeclaredField("connection");
            f.setAccessible(true);
        } catch (NoSuchFieldException e) {
            // Will surface as NPE/IllegalStateException at install time — logged there.
        }
        CONNECTION_FIELD = f;
    }

    private static ChannelPipeline getPipeline(Player player) throws Exception {
        if (CONNECTION_FIELD == null) throw new IllegalStateException(
                "ServerCommonPacketListenerImpl#connection field not found");
        net.minecraft.server.network.ServerGamePacketListenerImpl packetListener =
                ((CraftPlayer) player).getHandle().connection;
        Connection conn = (Connection) CONNECTION_FIELD.get(packetListener);
        return conn.channel.pipeline();
    }

    @Override
    public boolean supportsBossbarSentry() {
        return true;
    }

    @Override
    public void installBossbarSentry(@javax.annotation.Nonnull Player player) {
        try {
            ChannelPipeline pipeline = getPipeline(player);
            if (pipeline.get(SENTRY_PIPELINE_NAME) != null) return; // idempotent
            // Locate the Connection handler and insert before it
            String anchorName = null;
            for (var entry : pipeline.toMap().entrySet()) {
                if (entry.getValue() instanceof Connection) {
                    anchorName = entry.getKey();
                    break;
                }
            }
            if (anchorName == null) return; // pipeline missing Connection — bail
            pipeline.addBefore(anchorName, SENTRY_PIPELINE_NAME,
                    new HLBossbarSentry_v26_3_R1(
                            cz.hashiri.harshlands.HLPlugin.getPlugin(),
                            player.getUniqueId()));
        } catch (Throwable t) {
            cz.hashiri.harshlands.HLPlugin.getPlugin().getLogger()
                    .warning("Failed to install bossbar sentry for " + player.getName() + ": " + t);
        }
    }

    @Override
    public void uninstallBossbarSentry(@javax.annotation.Nonnull Player player) {
        try {
            ChannelPipeline pipeline = getPipeline(player);
            if (pipeline.get(SENTRY_PIPELINE_NAME) != null) {
                pipeline.remove(SENTRY_PIPELINE_NAME);
            }
            cz.hashiri.harshlands.HLPlugin.getPlugin().getAnchorRegistry()
                    .clear(player.getUniqueId());
        } catch (Throwable t) {
            cz.hashiri.harshlands.HLPlugin.getPlugin().getLogger()
                    .warning("Failed to uninstall bossbar sentry for " + player.getName() + ": " + t);
        }
    }

    // Spigot 26.1.x keeps the legacy nested enum TimeSkipEvent.SkipReason; Paper/Purpur 26.1.x
    // refactored the event so getSkipReason() is inherited from a new ClockTimeSkipEvent parent
    // and returns ClockTimeSkipEvent.SkipReason. We compile against Spigot here but the target
    // runtime is Purpur, so a direct call would produce NoSuchMethodError on the deployed jar.
    // Reflective lookup-by-name resolves either declared or inherited method and avoids tying
    // the bytecode to a specific return type.
    @Override
    public boolean isNightSkip(TimeSkipEvent event) {
        try {
            Object reason = event.getClass().getMethod("getSkipReason").invoke(event);
            return reason instanceof Enum<?> e && "NIGHT_SKIP".equals(e.name());
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }
}
