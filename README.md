# WormholeXDynmap 1.0.0

Shows [Wormhole X-Treme](https://github.com/Daktoo/Wormhole-X-Treme) stargates on your [Dynmap](https://github.com/webbukkit/dynmap) web map.

Every gate on the server becomes a portal marker on a toggleable Stargates layer. Hovering shows the gate name; clicking opens a card with the dial command, coordinates, owner, network and shape. The layer keeps itself up to date as gates are built, renamed and removed - there is nothing to run by hand.

```
Gate name
/dial gatename
X Y Z
O:Owner
N:Network
Shape
```

## Requirements

| | |
| --- | --- |
| Server | Paper 26.2 |
| Dynmap | 3.x, with the `markers` component enabled |
| Wormhole X-Treme | any recent build (the event API is optional — see below) |

Both plugins are hard dependencies. WormholeXDynmap will refuse to load without them rather than half-working.

## Installation

1. Drop `WormholeXDynmap-1.0.0.jar` into `plugins/`.
2. Restart the server.
3. Open your map. The Stargates layer is in the sidebar.

If no markers appear, check the console on startup - the plugin logs which update path it took, and warns if Dynmap reported no marker API.

## Commands

| Command | Description |
| --- | --- |
| `/wxdynmap update` | Refresh the layer immediately |
| `/wxdynmap reload` | Reload `config.yml` and rebuild the layer from scratch |

Alias: `/wxdyn`. Permission: `wormholexdynmap.admin`, default op.

## Configuration

The popup is a template, so you can change what it says and how it looks without recompiling:

```yaml
marker:
  icon: portal

  popup-format: |-
    <div class="regioninfo"><div class="infowindow">
    <div style="font-size:120%;font-weight:bold;margin-bottom:6px;">%name%</div>
    <div style="font-family:monospace;margin-bottom:8px;">/dial %name%</div>
    <div style="margin-bottom:8px;">%x% %y% %z%</div>
    <div>O:%owner%</div>
    <div>N:%network%</div>
    <div>%shape%</div>
    </div></div>
```

Placeholders: `%name%`, `%x%`, `%y%`, `%z%`, `%world%`, `%owner%`, `%network%`, `%shape%`.

Other settings worth knowing:

- `marker.icon` - any icon Dynmap knows about. `portal` is the stargate-looking one.
- `marker-set.label`, `layer-priority`, `hide-by-default`, `min-zoom` - how the layer behaves in the sidebar.
- `hidden-worlds`, `hidden-networks` - keep particular gates off the map entirely. Handy for a staff network you would rather not advertise publicly.
- `instant-updates` - set to `false` to run purely on the periodic timer.

Gate and owner names are HTML-escaped before they reach the popup, so a player cannot inject markup into your map by naming a gate something clever.

## How updates work

Two paths, picked automatically at startup. The console line on enable tells you which one is active.

**Event path.** If your Wormhole X-Treme build has the gate event API, the plugin listens for `StargateCreatedEvent` and `StargateRemovedEvent` and refreshes within a tick or two.

**Fallback path.** Without those events there is nothing to listen for, so the plugin watches what causes gates to change instead: the WXT commands, sign edits, and breaking a block that belongs to a gate. It refreshes about a second later, once WXT has finished its own work. Slightly less precise, but it needs no changes to Wormhole X-Treme at all.

Underneath either path a periodic full re-scan runs as a safety net - every 10 seconds on the fallback path, every 2 minutes on the event path - catching anything no event or command covers, such as a manual database edit. Bursts are collapsed into a single refresh, so a busy build session will not thrash the marker API.

Markers are non-persistent, so Dynmap rebuilds them from Wormhole X-Treme's own data on every restart. There is no separate marker file to drift out of sync.

## Optional: the Wormhole X-Treme event API

`wormholextreme-events.patch` adds a small event API to Wormhole X-Treme. It is entirely optional, but it makes map updates immediate and gives any other plugin a clean way to react to gates.

```bash
cd /path/to/Wormhole-X-Treme
git apply /path/to/wormholextreme-events.patch
mvn clean install
```

What it adds:

- **`StargateCreatedEvent`**, with a `Cause` of `BUILT`, `LOADED` or `IMPORTED`, so listeners can tell a fresh build from gates being read back out of the database on restart. Fired at the end of `StargateManager.addStargate`, once the gate is fully indexed and registered on its network.
- **`StargateRemovedEvent`**, fired at the end of `StargateManager.removeStargate`. The gate name rides on the event separately, because teardown can clear fields on the `Stargate` object itself.
- **`StargateEvents`**, a dispatch helper that hops back to the main thread if it is ever called from an async one, and swallows listener exceptions so a third-party plugin cannot abort a gate build by throwing.

Both events are notifications rather than vetoes - the gate already exists, or is already gone, by the time listeners run. Anything wanting to refuse a build should do so earlier, in the completion path.

Three call sites change so the cause is accurate: `StargateDBManager` passes `LOADED`, `WXConvertDB` passes `IMPORTED`, and everything else gets `BUILT` through the existing single-argument `addStargate`, whose signature is unchanged. No existing behaviour is altered.

## Building

```bash
mvn clean package
```

The jar lands in `target/WormholeXDynmap-1.0.0.jar`.

Wormhole X-Treme comes from JitPack by default. If you are changing both projects together, build WXT locally instead so you are not waiting on a snapshot to catch up:

```bash
cd /path/to/Wormhole-X-Treme
mvn clean install
```

then swap the dependency in `pom.xml` for the local coordinates - there is a comment above it showing exactly what to use. If Maven cannot find `paper-api`, change the `paper.version` property to the API version your Paper build reports.

## Troubleshooting

**No Stargates layer on the map.** Check that Dynmap's `markers` component is enabled in its own `configuration.txt`. The plugin logs a warning if the marker API is missing.

**Markers appear but the popup is stale.** Browsers cache marker data. Hard-refresh the map page.

**Updates feel slow.** Check the startup log for which path is active. If it says it is watching commands and sign edits, the server is running an unpatched Wormhole X-Treme - apply the patch above and rebuild both, in that order. Building the Dynmap plugin first will quietly leave you on the fallback path.

**Markers in the wrong spot.** Gates are anchored on the dial lever. Gates without one — older shapes, or oddly built gates - fall back to the teleport point, then the dial sign, then a portal block.

## Credits

Wormhole X-Treme maintained fork at [Daktoo/Wormhole-X-Treme](https://github.com/Daktoo/Wormhole-X-Treme). Dynmap by [webbukkit/dynmap](https://github.com/webbukkit/dynmap).
