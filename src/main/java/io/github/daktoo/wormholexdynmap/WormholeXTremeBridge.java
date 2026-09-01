package io.github.daktoo.wormholexdynmap;

import de.luricos.bukkit.WormholeXTreme.Wormhole.model.StargateManager;
import org.bukkit.block.Block;

/** Thin wrapper so every call into Wormhole X-Treme sits in one place. */
final class WormholeXTremeBridge {

    private WormholeXTremeBridge() {
    }

    static boolean isGateBlock(Block block) {
        try {
            return block != null && StargateManager.isBlockInGate(block);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
