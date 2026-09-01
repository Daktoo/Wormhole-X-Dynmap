package io.github.daktoo.wormholexdynmap;

import de.luricos.bukkit.WormholeXTreme.Wormhole.model.Stargate;
import de.luricos.bukkit.WormholeXTreme.Wormhole.model.StargateManager;
import de.luricos.bukkit.WormholeXTreme.Wormhole.model.StargateNetwork;
import de.luricos.bukkit.WormholeXTreme.Wormhole.model.StargateShape;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Owns the Dynmap marker set and keeps it in step with Wormhole X-Treme's
 * in-memory gate list.
 */
final class StargateMarkers {

    private final WormholeXDynmap plugin;

    private MarkerAPI markerApi;
    private MarkerSet markerSet;
    private MarkerIcon icon;

    /** marker id -> marker, for the markers this plugin owns. */
    private final Map<String, Marker> owned = new HashMap<>();

    private String setId;
    private String setLabel;
    private String iconId;
    private String labelFormat;
    private int layerPriority;
    private boolean hideByDefault;
    private int minZoom;
    private List<String> hiddenWorlds;
    private List<String> hiddenNetworks;

    StargateMarkers(WormholeXDynmap plugin) {
        this.plugin = plugin;
        readSettings();
    }

    private void readSettings() {
        ConfigurationSection cfg = plugin.getConfig();
        this.setId = cfg.getString("marker-set.id", "wormholexdynmap.stargates");
        this.setLabel = cfg.getString("marker-set.label", "Stargates");
        this.layerPriority = cfg.getInt("marker-set.layer-priority", 10);
        this.hideByDefault = cfg.getBoolean("marker-set.hide-by-default", false);
        this.minZoom = cfg.getInt("marker-set.min-zoom", 0);
        this.iconId = cfg.getString("marker.icon", "portal");
        this.labelFormat = cfg.getString("marker.label-format", defaultLabelFormat());
        this.hiddenWorlds = lowercase(cfg.getStringList("hidden-worlds"));
        this.hiddenNetworks = lowercase(cfg.getStringList("hidden-networks"));
    }

    private static List<String> lowercase(List<String> in) {
        List<String> out = new ArrayList<>(in.size());
        for (String s : in) {
            out.add(s.toLowerCase(Locale.ROOT));
        }
        return out;
    }

    private static String defaultLabelFormat() {
        return "<div class=\"regioninfo\"><div class=\"infowindow\">"
                + "<span style=\"font-weight:bold;font-size:110%;\">%name% (/dial %name%)</span><br/>"
                + "%x% %y% %z%<br/>"
                + "O:%owner%<br/>"
                + "N:%network%<br/>"
                + "%shape%"
                + "</div></div>";
    }

    boolean isReady() {
        return markerSet != null;
    }

    int markerCount() {
        return owned.size();
    }

    /** Called when Dynmap's API comes up. Returns true if the layer is ready. */
    boolean attach(DynmapCommonAPI api) {
        markerApi = api.getMarkerAPI();
        if (markerApi == null) {
            plugin.getLogger().severe("Dynmap reported no marker API; is the marker component enabled?");
            return false;
        }
        buildSet();
        return markerSet != null;
    }

    void detach() {
        owned.clear();
        if (markerSet != null) {
            markerSet.deleteMarkerSet();
            markerSet = null;
        }
        markerApi = null;
        icon = null;
    }

    void reloadSettings() {
        readSettings();
        if (markerApi != null) {
            // Rebuild from scratch so set id / icon changes take effect.
            owned.clear();
            if (markerSet != null) {
                markerSet.deleteMarkerSet();
                markerSet = null;
            }
            buildSet();
        }
    }

    private void buildSet() {
        markerSet = markerApi.getMarkerSet(setId);
        if (markerSet == null) {
            markerSet = markerApi.createMarkerSet(setId, setLabel, null, false);
        } else {
            markerSet.setMarkerSetLabel(setLabel);
        }
        if (markerSet == null) {
            plugin.getLogger().severe("Could not create the Dynmap marker set '" + setId + "'.");
            return;
        }
        markerSet.setLayerPriority(layerPriority);
        markerSet.setHideByDefault(hideByDefault);
        if (minZoom > 0) {
            markerSet.setMinZoom(minZoom);
        }

        icon = markerApi.getMarkerIcon(iconId);
        if (icon == null) {
            plugin.getLogger().warning("Dynmap has no marker icon called '" + iconId
                    + "'; falling back to the default icon.");
            icon = markerApi.getMarkerIcon(MarkerIcon.DEFAULT);
        }
    }

    /**
     * Diffs Wormhole X-Treme's gate list against the markers currently on the
     * map: adds what is new, updates what moved or was renamed, removes what
     * has gone. Must run on the main thread.
     */
    void sync() {
        if (markerSet == null) {
            return;
        }

        ArrayList<Stargate> gates = StargateManager.getAllGates();
        Set<String> seen = new HashSet<>();

        if (gates != null) {
            for (Stargate gate : gates) {
                if (gate == null || !gate.isValid()) {
                    continue;
                }
                String name = gate.getGateName();
                if (name == null || name.isEmpty()) {
                    continue;
                }
                Location loc = resolveLocation(gate);
                if (loc == null || loc.getWorld() == null) {
                    continue;
                }
                String worldName = loc.getWorld().getName();
                if (hiddenWorlds.contains(worldName.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                String network = networkName(gate);
                if (hiddenNetworks.contains(network.toLowerCase(Locale.ROOT))) {
                    continue;
                }

                String id = markerId(gate, name);
                String label = buildLabel(gate, name, loc, network);
                seen.add(id);

                Marker marker = owned.get(id);
                if (marker == null) {
                    marker = markerSet.findMarker(id);
                }
                if (marker == null) {
                    marker = markerSet.createMarker(id, label, true, worldName,
                            loc.getX() + 0.5D, loc.getY() + 0.5D, loc.getZ() + 0.5D, icon, false);
                    if (marker == null) {
                        continue;
                    }
                } else {
                    marker.setLocation(worldName, loc.getX() + 0.5D, loc.getY() + 0.5D, loc.getZ() + 0.5D);
                    marker.setLabel(label, true);
                    if (icon != null && marker.getMarkerIcon() != icon) {
                        marker.setMarkerIcon(icon);
                    }
                }
                owned.put(id, marker);
            }
        }

        owned.entrySet().removeIf(entry -> {
            if (seen.contains(entry.getKey())) {
                return false;
            }
            entry.getValue().deleteMarker();
            return true;
        });
    }

    private String markerId(Stargate gate, String name) {
        long gateId = gate.getGateId();
        if (gateId > 0L) {
            return "wxt_" + gateId;
        }
        return "wxt_" + name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
    }

    /**
     * Gates are anchored on their dial lever; older or oddly shaped gates may
     * not have one, so fall back through the other known blocks.
     */
    private Location resolveLocation(Stargate gate) {
        Block lever = gate.getGateDialLeverBlock();
        if (lever != null) {
            return lever.getLocation();
        }
        Location teleport = gate.getGatePlayerTeleportLocation();
        if (teleport != null) {
            return teleport;
        }
        Block sign = gate.getGateDialSignBlock();
        if (sign != null) {
            return sign.getLocation();
        }
        List<Location> portal = gate.getGatePortalBlocks();
        if (portal != null && !portal.isEmpty()) {
            return portal.get(0);
        }
        List<Location> structure = gate.getGateStructureBlocks();
        if (structure != null && !structure.isEmpty()) {
            return structure.get(0);
        }
        World world = gate.getGateWorld();
        return world == null ? null : world.getSpawnLocation();
    }

    private String networkName(Stargate gate) {
        StargateNetwork network = gate.getGateNetwork();
        String name = network == null ? null : network.getNetworkName();
        return (name == null || name.isEmpty()) ? "none" : name;
    }

    private String shapeName(Stargate gate) {
        StargateShape shape = gate.getGateShape();
        String name = shape == null ? null : shape.getShapeName();
        return (name == null || name.isEmpty()) ? "Standard" : name;
    }

    private String buildLabel(Stargate gate, String name, Location loc, String network) {
        String owner = gate.getGateOwner();
        if (owner == null || owner.isEmpty()) {
            owner = "unknown";
        }
        return labelFormat
                .replace("%name%", escape(name))
                .replace("%x%", String.valueOf(loc.getBlockX()))
                .replace("%y%", String.valueOf(loc.getBlockY()))
                .replace("%z%", String.valueOf(loc.getBlockZ()))
                .replace("%world%", escape(loc.getWorld().getName()))
                .replace("%owner%", escape(owner))
                .replace("%network%", escape(network))
                .replace("%shape%", escape(shapeName(gate)));
    }

    /** Gate names and owners are player-supplied, so never trust them as HTML. */
    private static String escape(String in) {
        return in.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
