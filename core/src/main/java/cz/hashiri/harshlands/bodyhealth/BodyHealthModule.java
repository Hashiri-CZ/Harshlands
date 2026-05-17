package cz.hashiri.harshlands.bodyhealth;

import cz.hashiri.harshlands.HLPlugin;
import cz.hashiri.harshlands.api.HarshlandsAPI;
import cz.hashiri.harshlands.api.hud.Hud;
import cz.hashiri.harshlands.data.HLConfig;
import cz.hashiri.harshlands.data.HLModule;
import cz.hashiri.harshlands.debug.DebugProvider;
import cz.hashiri.harshlands.utils.BossbarHUD;
import cz.hashiri.harshlands.utils.DisplayTask;
import cz.hashiri.harshlands.utils.Utils;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BodyHealthModule extends HLModule implements HudImpl.State {

    public static final String NAME = "BodyHealth";

    private final HLPlugin plugin;
    private final Set<UUID> shownPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Map<BodyPart, BodyPartState>> lastRenderedStates = new ConcurrentHashMap<>();
    private final Map<UUID, BossbarHUD> standaloneHuds = new HashMap<>();

    private int anchorX;
    private int tickPeriod;
    private BukkitTask renderTask;
    private BodyHealthQuitListener quitListener;
    private HudImpl hud;
    private boolean active;

    public BodyHealthModule(HLPlugin plugin) {
        super(NAME, plugin, Map.of(), Map.of());
        this.plugin = plugin;
    }

    @Override
    public void initialize() {
        setUserConfig(new HLConfig(plugin, "Settings/bodyhealth.yml"));
        FileConfiguration cfg = getUserConfig().getConfig();
        this.anchorX    = cfg.getInt("BodyHealth.HUD.AnchorX", 160);
        // Floor at 1 — runTaskTimer rejects 0 and a negative period would crash the scheduler.
        this.tickPeriod = Math.max(1, cfg.getInt("BodyHealth.HUD.TickPeriod", 5));

        Utils.logModuleInit("bodyhealth", NAME);

        // We deliberately check getPlugin() != null instead of isPluginEnabled().
        // Harshlands' plugin.yml sets load: STARTUP, so we initialize before the
        // POSTWORLD plugins (including PlaceholderAPI) have run their onEnable.
        // isPluginEnabled would always return false at this point and we would
        // bail out without ever registering HarshlandsAPI - so consumers like
        // BodyHealth would silently see inst() == null and never call Hud.add.
        // The render task is already defensive about unresolved placeholders
        // (returns FULL), and by the time the Bukkit scheduler starts ticking
        // PAPI's onEnable has long since run.
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            warnPapiMissing();
            return;
        }

        this.hud = new HudImpl("bodyhealth", this);
        Map<String, Hud> huds = Map.of("bodyhealth", hud);
        HarshlandsAPI.register(new HarshlandsAPIImpl(
                new PlayerManagerImpl(),
                new HudManagerImpl(huds),
                plugin.getDataFolder()));

        this.quitListener = new BodyHealthQuitListener(uuid -> {
            shownPlayers.remove(uuid);
            lastRenderedStates.remove(uuid);
            BossbarHUD removed = standaloneHuds.remove(uuid);
            if (removed != null) removed.hide();
        });
        Bukkit.getPluginManager().registerEvents(quitListener, plugin);

        BodyHealthRenderTask task = new BodyHealthRenderTask(this, anchorX, this::resolveHud);
        this.renderTask = task.runTaskTimer(plugin, 0L, tickPeriod);

        plugin.getDebugManager().registerProvider(new DebugProvider() {
            @Override public String getModuleName() { return NAME; }
            @Override public Collection<String> getSubsystems() { return List.of("Render", "API"); }
        });

        this.active = true;
    }

    @Override
    public void shutdown() {
        if (!active) return;
        active = false;

        if (renderTask != null) { renderTask.cancel(); renderTask = null; }
        if (quitListener != null) {
            HandlerList.unregisterAll(quitListener);
            quitListener = null;
        }

        // Direct lookup — never go through resolveHud() during shutdown, since that
        // would create-and-show a fresh standalone bossbar just to immediately strip
        // an element from it. Existing HUDs are the only thing worth touching.
        for (UUID uuid : shownPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            DisplayTask dt = DisplayTask.getTasks().get(uuid);
            BossbarHUD existing = (dt != null) ? dt.getBossbarHud() : standaloneHuds.get(uuid);
            if (existing != null) existing.removeElement(BodyHealthRenderTask.ELEMENT_ID);
        }
        for (BossbarHUD h : standaloneHuds.values()) h.hide();
        standaloneHuds.clear();
        shownPlayers.clear();
        lastRenderedStates.clear();

        HarshlandsAPI.register(null);
        Utils.logModuleShutdown("bodyhealth", NAME);
    }

    // -------------------------------------------------------------------------
    // HudImpl.State — package-private so HudImpl can mutate.
    // -------------------------------------------------------------------------

    @Override public boolean markShown(UUID uuid) {
        return shownPlayers.add(uuid);
    }

    @Override public boolean markHidden(UUID uuid) {
        boolean changed = shownPlayers.remove(uuid);
        if (changed) {
            // Schedule main-thread element removal so we don't touch Bukkit objects off-thread.
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) resolveHud(p).removeElement(BodyHealthRenderTask.ELEMENT_ID);
                lastRenderedStates.remove(uuid);
            });
        }
        return changed;
    }

    @Override public boolean isShown(UUID uuid) {
        return shownPlayers.contains(uuid);
    }

    // -------------------------------------------------------------------------
    // Render-task accessors (package-private).
    // -------------------------------------------------------------------------

    Set<UUID> shownPlayers()                                          { return shownPlayers; }
    Map<BodyPart, BodyPartState> lastRendered(UUID uuid)              { return lastRenderedStates.get(uuid); }
    void putLastRendered(UUID uuid, Map<BodyPart, BodyPartState> m)   { lastRenderedStates.put(uuid, m); }
    void clearLastRendered(UUID uuid)                                  { lastRenderedStates.remove(uuid); }

    // -------------------------------------------------------------------------
    // BossbarHUD resolution — same precedence pattern as FoodExpansionModule.
    // -------------------------------------------------------------------------

    private BossbarHUD resolveHud(Player player) {
        UUID uuid = player.getUniqueId();
        DisplayTask dt = DisplayTask.getTasks().get(uuid);
        if (dt != null) {
            BossbarHUD shared = dt.getBossbarHud();
            BossbarHUD prev = standaloneHuds.remove(uuid);
            if (prev != null && prev != shared) prev.hide();
            return shared;
        }
        // No TAN — try FoodExpansion's already-created HUD.
        cz.hashiri.harshlands.foodexpansion.FoodExpansionModule fem =
                (cz.hashiri.harshlands.foodexpansion.FoodExpansionModule)
                        HLModule.getModule(cz.hashiri.harshlands.foodexpansion.FoodExpansionModule.NAME);
        if (fem != null && fem.isGloballyEnabled()) {
            return fem.getOrCreateHud(player);
        }
        // Standalone fallback — module-owned, hidden during shutdown/quit.
        return standaloneHuds.computeIfAbsent(uuid, u -> {
            BossbarHUD h = new BossbarHUD((Audience) player);
            h.show();
            return h;
        });
    }

    private void warnPapiMissing() {
        // Match the load-phase asymmetry in initialize() - check loaded, not enabled.
        // During STARTUP, BodyHealth (POSTWORLD) isn't enabled yet either, so a
        // strict isPluginEnabled gate here would suppress the warning even when
        // BodyHealth IS installed.
        if (Bukkit.getPluginManager().getPlugin("BodyHealth") == null) {
            // BodyHealth itself isn't installed — nothing to warn about. Stay silent.
            return;
        }
        plugin.getLogger().warning("BodyHealth detected but PlaceholderAPI is not installed.");
        plugin.getLogger().warning("PlaceholderAPI is required for the BodyHealth HUD to read live state.");
        plugin.getLogger().warning("Install PlaceholderAPI and the BodyHealth expansion to enable the HUD,");
        plugin.getLogger().warning("or set BodyHealth.Enabled: false in config.yml to suppress this warning.");
    }
}
