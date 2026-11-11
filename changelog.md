# Changes

[English](changelog.md) · [Français](changelog.fr.md)

## 7.0.0-alpha.6 — validation pending

- Update offscreen lightmaps on the client tick clock, publish pending remote light-engine sections with native world updates, and tick remote particles once.
- Clip player bodies independently from portal views, preserve legitimate remote self views, and trace third-person camera corners through openings.
- Restore native block/entity targeting, remote selection outlines and mining-world continuity with server path validation; add automatic remote pickup with vanilla collection rules.
- Scope the whole server mining tick to its recorded world; preserve normal bucket reach, persistent remote trading, synchronized mounting and physical-world reflected damage/death/drop handling. Exercise ordinary and hanging-sign editors with actual keyboard input and the native Done button, requiring changed destination text and an untouched source sign.
- Sample native fluid contact on the physical source and projected destination body parts without moving the entity; add nine water/lava cases including scaled and rotated openings, a finite aperture edge, non-player lava and destination lava-current strength, plus three independent native-force comparison contracts.
- Split native arrow, throwable and hurting-projectile motion at portal boundaries while retaining destination impacts and a single velocity integration. Keep fishing hooks, fireworks, llama spit and shulker bullets on their inherited server path; their special behavior is not covered by the new segment checks.
- Preserve folded sound distance and native attenuation; isolate atmospheric rain-fog interpolation per renderer and world.
- Expand GitHub-only scenarios with pitched optics, real dark-room comparisons, clipped bodies, 20 native-interaction phases, seven pose/held-use traversals, nine fluid-contact cases and three native-force comparison contracts, seven entity witnesses, environment observations, two real players and save/rejoin checks. The bow and food traversals keep use active across dimensions.

The [seamless portal follow-up](docs/seamless-26.2.md) distinguishes implementation from pending acceptance evidence and records remaining coverage limits.

## 7.0.0-alpha.4

- Fix traversal through portals placed exactly on a solid wall face while preserving collisions outside the opening and at the destination.

- Stabilize coplanar wall-mounted portal rendering with shared coverage/composition depth priority.

## 7.0.0-alpha.3

- Prepare Fabric extraction contexts for portal views so external render-state consumers receive valid scene data.
- Add event-consumer coverage before and after resource reload, including nested views.

The standalone Fabric 26.2 migration preserves upstream history and imports the 26.2 adaptations from the Fabric/Minestom source fork. Compilation, automated tests and runtime scenarios are assigned to GitHub Actions.

See the [destination commit history](https://github.com/N4TH4NOT/Immersive-Portals/commits/port-26.2), [source provenance](docs/provenance.md) and [migration findings](docs/migration-audit-26.2.md). Validation status belongs to the workflow for the exact commit, not this changelog.
