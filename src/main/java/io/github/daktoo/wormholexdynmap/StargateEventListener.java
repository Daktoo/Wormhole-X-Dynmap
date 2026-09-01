package io.github.daktoo.wormholexdynmap;

import de.luricos.bukkit.WormholeXTreme.Wormhole.events.StargateCreatedEvent;
import de.luricos.bukkit.WormholeXTreme.Wormhole.events.StargateRemovedEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * The good path: Wormhole X-Treme tells us directly when a gate appears or
 * disappears, so the map updates on the same tick instead of guessing from
 * commands and sign edits.
 *
 * This class is only loaded if the running Wormhole X-Treme build actually has
 * the event classes — see {@link WormholeXDynmap#registerUpdateStrategy()}.
 * Referencing it on an older build would throw NoClassDefFoundError, which is
 * why nothing else in the plugin mentions these types.
 */
final class StargateEventListener implements Listener {

    private final WormholeXDynmap plugin;

    StargateEventListener(WormholeXDynmap plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onStargateCreated(StargateCreatedEvent event) {
        // LOADED gates arrive while Wormhole X-Treme is still enabling, before
        // this plugin exists. They are picked up by the first sync instead.
        plugin.requestDebouncedSync();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onStargateRemoved(StargateRemovedEvent event) {
        plugin.requestDebouncedSync();
    }
}
