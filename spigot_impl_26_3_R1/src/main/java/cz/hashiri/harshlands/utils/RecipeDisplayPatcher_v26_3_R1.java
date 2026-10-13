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
package cz.hashiri.harshlands.utils;

import cz.hashiri.harshlands.HLPlugin;
import cz.hashiri.harshlands.utils.recipe.RecipeDisplayRegistry;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundPlaceGhostRecipePacket;
import net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public class RecipeDisplayPatcher_v26_3_R1 implements Listener {

    private static final String HANDLER_NAME = "harshlands_recipe_display";

    private static final Field CONNECTION_FIELD;
    static {
        try {
            CONNECTION_FIELD = ServerCommonPacketListenerImpl.class.getDeclaredField("connection");
            CONNECTION_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    final HLPlugin plugin;
    final RecipeDisplayRegistry registry;

    private final Set<UUID> warnedPlayers = ConcurrentHashMap.newKeySet();
    final AtomicInteger rewriteFailureCount = new AtomicInteger();

    public RecipeDisplayPatcher_v26_3_R1(HLPlugin plugin, RecipeDisplayRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player bukkitPlayer = event.getPlayer();
        ServerPlayer serverPlayer;
        try {
            serverPlayer = ((CraftPlayer) bukkitPlayer).getHandle();
            Channel channel = resolveChannel(serverPlayer);
            if (channel.pipeline().get(HANDLER_NAME) == null) {
                channel.pipeline().addBefore("packet_handler", HANDLER_NAME, new RewriteHandler());
            }
        } catch (Throwable t) {
            UUID uuid = bukkitPlayer.getUniqueId();
            if (warnedPlayers.add(uuid)) {
                plugin.getLogger().log(Level.WARNING,
                        "Recipe display patcher could not be installed for player " + uuid + ".", t);
            }
            return;
        }
        // Resend the initial recipe book so our just-installed handler can rewrite it.
        // Without this resend, the recipe book listing remains stuck with whatever
        // PlayerList.placeNewPlayer sent before our handler was attached.
        try {
            serverPlayer.getRecipeBook().sendInitialRecipeBook(serverPlayer);
        } catch (Throwable t) {
            UUID uuid = bukkitPlayer.getUniqueId();
            if (warnedPlayers.add(uuid)) {
                plugin.getLogger().log(Level.WARNING,
                        "Recipe book resend after patcher install failed for player " + uuid + "; recipe book listing may show base materials until next server resync.", t);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        try {
            ServerPlayer serverPlayer = ((CraftPlayer) event.getPlayer()).getHandle();
            Channel channel = resolveChannel(serverPlayer);
            if (channel.pipeline().get(HANDLER_NAME) != null) {
                channel.pipeline().remove(HANDLER_NAME);
            }
        } catch (Throwable ignored) {
            // pipeline already torn down by server shutdown — nothing to do
        }
    }

    private Channel resolveChannel(ServerPlayer serverPlayer) throws IllegalAccessException {
        ServerGamePacketListenerImpl listener = serverPlayer.connection;
        Connection connection = (Connection) CONNECTION_FIELD.get(listener);
        return connection.channel;
    }

    private ClientboundRecipeBookAddPacket rewriteRecipeBookAdd(ClientboundRecipeBookAddPacket original) {
        List<ClientboundRecipeBookAddPacket.Entry> entries = original.entries();
        List<ClientboundRecipeBookAddPacket.Entry> rewritten = new ArrayList<>(entries.size());
        boolean changed = false;
        RecipeManager recipeManager = ((CraftServer) Bukkit.getServer()).getServer().getRecipeManager();
        for (ClientboundRecipeBookAddPacket.Entry entry : entries) {
            RecipeDisplayEntry inner = entry.contents();
            RecipeDisplayEntry replacement = maybeRewriteEntry(inner, recipeManager);
            if (replacement != inner) {
                rewritten.add(new ClientboundRecipeBookAddPacket.Entry(replacement, entry.flags()));
                changed = true;
            } else {
                rewritten.add(entry);
            }
        }
        if (!changed) {
            return original;
        }
        return new ClientboundRecipeBookAddPacket(rewritten, original.replace());
    }

    private RecipeDisplayEntry maybeRewriteEntry(RecipeDisplayEntry entry, RecipeManager recipeManager) {
        RecipeDisplayId displayId = entry.id();
        RecipeManager.ServerDisplayInfo info = recipeManager.getRecipeFromDisplay(displayId);
        if (info == null) {
            return entry;
        }
        RecipeHolder<?> holder = info.parent();
        ResourceKey<Recipe<?>> recipeKey = holder.id();
        Identifier ident = recipeKey.identifier();
        NamespacedKey bukkitKey = new NamespacedKey(ident.getNamespace(), ident.getPath());
        if (!registry.contains(bukkitKey)) {
            return entry;
        }
        Map<Integer, List<org.bukkit.inventory.ItemStack>> slots = registry.get(bukkitKey);
        if (slots == null || slots.isEmpty()) {
            return entry;
        }
        RecipeDisplay rewrittenDisplay = rewriteDisplay(entry.display(), slots);
        if (rewrittenDisplay == entry.display()) {
            return entry;
        }
        return new RecipeDisplayEntry(entry.id(), rewrittenDisplay, entry.group(), entry.category(), entry.craftingRequirements());
    }

    private RecipeDisplay rewriteDisplay(RecipeDisplay display, Map<Integer, List<org.bukkit.inventory.ItemStack>> slots) {
        if (display instanceof ShapedCraftingRecipeDisplay shaped) {
            List<SlotDisplay> rewritten = rewriteSlotList(shaped.ingredients(), slots);
            if (rewritten == shaped.ingredients()) return display;
            return new ShapedCraftingRecipeDisplay(shaped.width(), shaped.height(), rewritten, shaped.result(), shaped.craftingStation());
        }
        if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
            List<SlotDisplay> rewritten = rewriteSlotList(shapeless.ingredients(), slots);
            if (rewritten == shapeless.ingredients()) return display;
            return new ShapelessCraftingRecipeDisplay(rewritten, shapeless.result(), shapeless.craftingStation());
        }
        if (display instanceof FurnaceRecipeDisplay furnace) {
            List<org.bukkit.inventory.ItemStack> stacks = slots.get(0);
            if (stacks == null || stacks.isEmpty()) return display;
            SlotDisplay rewritten = buildCompositeSlot(stacks);
            return new FurnaceRecipeDisplay(rewritten, furnace.fuel(), furnace.result(), furnace.craftingStation(), furnace.duration(), furnace.experience());
        }
        if (display instanceof StonecutterRecipeDisplay cutter) {
            List<org.bukkit.inventory.ItemStack> stacks = slots.get(0);
            if (stacks == null || stacks.isEmpty()) return display;
            SlotDisplay rewritten = buildCompositeSlot(stacks);
            return new StonecutterRecipeDisplay(rewritten, cutter.result(), cutter.craftingStation());
        }
        if (display instanceof SmithingRecipeDisplay smith) {
            SlotDisplay tmpl = rewriteSingleSlot(smith.template(), slots.get(0));
            SlotDisplay base = rewriteSingleSlot(smith.base(), slots.get(1));
            SlotDisplay addn = rewriteSingleSlot(smith.addition(), slots.get(2));
            if (tmpl == smith.template() && base == smith.base() && addn == smith.addition()) {
                return display;
            }
            return new SmithingRecipeDisplay(tmpl, base, addn, smith.result(), smith.craftingStation());
        }
        return display;
    }

    private List<SlotDisplay> rewriteSlotList(List<SlotDisplay> originalSlots, Map<Integer, List<org.bukkit.inventory.ItemStack>> slots) {
        List<SlotDisplay> rewritten = null;
        for (int i = 0; i < originalSlots.size(); i++) {
            List<org.bukkit.inventory.ItemStack> stacks = slots.get(i);
            if (stacks == null || stacks.isEmpty()) continue;
            if (rewritten == null) {
                rewritten = new ArrayList<>(originalSlots);
            }
            rewritten.set(i, buildCompositeSlot(stacks));
        }
        return rewritten == null ? originalSlots : rewritten;
    }

    private SlotDisplay rewriteSingleSlot(SlotDisplay original, List<org.bukkit.inventory.ItemStack> stacks) {
        if (stacks == null || stacks.isEmpty()) return original;
        return buildCompositeSlot(stacks);
    }

    private SlotDisplay buildCompositeSlot(List<org.bukkit.inventory.ItemStack> bukkitStacks) {
        List<SlotDisplay> options = new ArrayList<>(bukkitStacks.size());
        for (org.bukkit.inventory.ItemStack bukkit : bukkitStacks) {
            net.minecraft.world.item.ItemStack nms = CraftItemStack.asNMSCopy(bukkit);
            ItemStackTemplate template = ItemStackTemplate.fromNonEmptyStack(nms);
            options.add(new SlotDisplay.ItemStackSlotDisplay(template));
        }
        return options.size() == 1 ? options.get(0) : new SlotDisplay.Composite(options);
    }

    private ClientboundPlaceGhostRecipePacket rewritePlaceGhost(ClientboundPlaceGhostRecipePacket original) {
        RecipeDisplay incoming = original.recipeDisplay();
        if (incoming == null) {
            return original;
        }
        RecipeManager recipeManager = ((CraftServer) Bukkit.getServer()).getServer().getRecipeManager();
        for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
            ResourceKey<Recipe<?>> recipeKey = holder.id();
            Identifier ident = recipeKey.identifier();
            NamespacedKey bukkitKey = new NamespacedKey(ident.getNamespace(), ident.getPath());
            if (!registry.contains(bukkitKey)) {
                continue;
            }
            boolean[] match = {false};
            recipeManager.listDisplaysForRecipe(recipeKey, entry -> {
                if (entry.display() == incoming) {
                    match[0] = true;
                }
            });
            if (!match[0]) {
                continue;
            }
            Map<Integer, List<org.bukkit.inventory.ItemStack>> slots = registry.get(bukkitKey);
            if (slots == null || slots.isEmpty()) {
                return original;
            }
            RecipeDisplay rewrittenDisplay = rewriteDisplay(incoming, slots);
            if (rewrittenDisplay == incoming) {
                return original;
            }
            return new ClientboundPlaceGhostRecipePacket(original.containerId(), rewrittenDisplay);
        }
        return original;
    }

    /**
     * Outbound-rewrite handler installed per-player connection.
     */
    private final class RewriteHandler extends ChannelDuplexHandler {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            try {
                if (msg instanceof ClientboundRecipeBookAddPacket pkt) {
                    Object rewritten = rewriteRecipeBookAdd(pkt);
                    super.write(ctx, rewritten, promise);
                    return;
                }
                if (msg instanceof ClientboundPlaceGhostRecipePacket pkt) {
                    Object rewritten = rewritePlaceGhost(pkt);
                    super.write(ctx, rewritten, promise);
                    return;
                }
            } catch (Throwable t) {
                if (rewriteFailureCount.getAndIncrement() == 0) {
                    plugin.getLogger().log(Level.WARNING,
                            "Recipe display rewrite failed; falling back to original packet. Further failures will be silent.", t);
                }
            }
            super.write(ctx, msg, promise);
        }
    }
}
