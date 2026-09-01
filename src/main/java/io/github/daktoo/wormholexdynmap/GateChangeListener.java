package io.github.daktoo.wormholexdynmap;

import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Locale;
import java.util.Set;

/**
 * Wormhole X-Treme does not fire an event when a gate is built or removed, so
 * we watch the things that cause it — the WXT commands, and sign edits — and
 * ask for a refresh shortly afterwards. The periodic poll in the main class is
 * the safety net; this listener is what makes it feel instant.
 */
final class GateChangeListener implements Listener {

    private static final Set<String> WATCHED_COMMANDS = Set.of(
            "wxbuild", "wxcomplete", "wxremove", "wxreload", "wxre",
            "wxshape", "wxs", "wxconvertdb", "wormhole", "wxt", "wxidc");

    private final WormholeXDynmap plugin;

    GateChangeListener(WormholeXDynmap plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message.length() < 2) {
            return;
        }
        int space = message.indexOf(' ');
        String command = (space == -1 ? message.substring(1) : message.substring(1, space))
                .toLowerCase(Locale.ROOT);
        int colon = command.indexOf(':');
        if (colon != -1) {
            command = command.substring(colon + 1);
        }
        if (WATCHED_COMMANDS.contains(command)) {
            plugin.requestDebouncedSync();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        plugin.requestDebouncedSync();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (WormholeXTremeBridge.isGateBlock(block)) {
            plugin.requestDebouncedSync();
        }
    }
}
