package io.github.daktoo.wormholexdynmap;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.DynmapCommonAPIListener;

import java.util.logging.Level;

/**
 * Bridges Wormhole X-Treme and Dynmap: every complete stargate becomes a
 * "portal" marker on the web map, and the map keeps itself in step as gates
 * are built and removed.
 */
public final class WormholeXDynmap extends JavaPlugin {

    private StargateMarkers markers;
    private DynmapCommonAPIListener apiListener;
    private BukkitTask pollTask;
    private long debounceUntilTick = -1L;
    private boolean usingGateEvents = false;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (getServer().getPluginManager().getPlugin("dynmap") == null) {
            getLogger().severe("Dynmap not found. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        Plugin wxt = getServer().getPluginManager().getPlugin("WormholeXTreme");
        if (wxt == null) {
            getLogger().severe("Wormhole X-Treme not found. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        markers = new StargateMarkers(this);

        apiListener = new DynmapCommonAPIListener() {
            @Override
            public void apiEnabled(DynmapCommonAPI api) {
                if (markers.attach(api)) {
                    startPolling();
                    getLogger().info("Hooked into Dynmap; stargate layer is live.");
                }
            }

            @Override
            public void apiDisabled(DynmapCommonAPI api) {
                stopPolling();
                markers.detach();
            }
        };
        DynmapCommonAPIListener.register(apiListener);

        registerUpdateStrategy();
    }

    /**
     * Prefers Wormhole X-Treme's own gate events when the running build has
     * them, and falls back to watching commands and sign edits when it does
     * not. Either way the periodic sync in {@link #startPolling()} remains as
     * a safety net, just at a slower interval when events are available.
     */
    private void registerUpdateStrategy() {
        if (!getConfig().getBoolean("instant-updates", true)) {
            getLogger().info("Instant updates disabled; relying on the periodic sync only.");
            return;
        }

        try {
            Class.forName("de.luricos.bukkit.WormholeXTreme.Wormhole.events.StargateCreatedEvent");
            getServer().getPluginManager().registerEvents(new StargateEventListener(this), this);
            usingGateEvents = true;
            getLogger().info("Using Wormhole X-Treme gate events for instant map updates.");
            return;
        } catch (ClassNotFoundException | NoClassDefFoundError expected) {
            // Older Wormhole X-Treme build without the event API. Not an error.
        }

        getServer().getPluginManager().registerEvents(new GateChangeListener(this), this);
        getLogger().info("This Wormhole X-Treme build has no gate events; "
                + "watching commands and sign edits instead.");
    }

    @Override
    public void onDisable() {
        stopPolling();
        if (apiListener != null) {
            DynmapCommonAPIListener.unregisterCommonAPIListener(apiListener);
            apiListener = null;
        }
        if (markers != null) {
            markers.detach();
            markers = null;
        }
    }

    private void startPolling() {
        stopPolling();
        String key = usingGateEvents ? "safety-net-interval-seconds" : "update-interval-seconds";
        long seconds = Math.max(1L, getConfig().getLong(key, usingGateEvents ? 120L : 10L));
        long period = seconds * 20L;
        pollTask = getServer().getScheduler().runTaskTimer(this, this::syncQuietly, 20L, period);
    }

    private void stopPolling() {
        if (pollTask != null) {
            pollTask.cancel();
            pollTask = null;
        }
    }

    /**
     * Schedules a sync a few ticks from now, collapsing bursts of events into a
     * single refresh so a busy server does not rebuild the layer dozens of
     * times a second.
     */
    void requestDebouncedSync() {
        if (markers == null || !markers.isReady()) {
            return;
        }
        long configured = getConfig().getLong("instant-update-delay-ticks", -1L);
        // Events fire after the gate is already in the list, so 2 ticks is
        // plenty. The fallback listener fires *before* Wormhole X-Treme has
        // handled the command, so it needs to wait a good deal longer.
        long delay = configured > 0L ? configured : (usingGateEvents ? 2L : 20L);
        long now = getServer().getCurrentTick();
        if (debounceUntilTick > now) {
            return;
        }
        debounceUntilTick = now + delay;
        getServer().getScheduler().runTaskLater(this, this::syncQuietly, delay);
    }

    /** Runs a full sync, logging rather than throwing if something goes wrong. */
    void syncQuietly() {
        try {
            if (markers != null && markers.isReady()) {
                markers.sync();
            }
        } catch (Throwable t) {
            getLogger().log(Level.WARNING, "Failed to update stargate markers", t);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase() : "update";

        switch (sub) {
            case "update", "refresh" -> {
                syncQuietly();
                sender.sendMessage("[WormholeXDynmap] Stargate markers refreshed ("
                        + (markers == null ? 0 : markers.markerCount()) + " gates).");
            }
            case "reload" -> {
                reloadConfig();
                if (markers != null) {
                    markers.reloadSettings();
                }
                startPolling();
                syncQuietly();
                sender.sendMessage("[WormholeXDynmap] Config reloaded and markers rebuilt.");
            }
            default -> sender.sendMessage("[WormholeXDynmap] Usage: /" + label + " <update|reload>");
        }
        return true;
    }
}
