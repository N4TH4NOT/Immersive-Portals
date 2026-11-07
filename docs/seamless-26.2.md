# Seamless portal follow-up

[English](seamless-26.2.md) · [Français](seamless-26.2.fr.md)

The alpha.6 work addresses defects reported after earlier runtime checks: pitched close views flickering, dark rooms appearing lit, detached first-person cameras, residual player images, terrain leaks and unavailable interaction through portals. Its expanded GitHub validation is **pending**. Earlier green runs do not validate these new changes or establish a perfect visual transition.

## Rendering and camera

Each offscreen scene owns a lightmap extractor. Only the main game renderer receives the normal vanilla tick, so remote extractors need an initial update and subsequent client-tick updates. The scene retains the destination's native light, sky and ambient values while sharing the viewer's block-light flicker and boss-overlay darkening. This is separate from propagating Sodium terrain invalidations.

Player bodies use a clipping pass independent of the portal view's own plane. Body color and depth are rendered into an isolated target, then composed with both body and view half-spaces. A geometric camera-inside-body guard prevents rendering the local head around a first-person eye; legitimate distant views of the player through another portal remain possible. Third-person camera collision follows the native eight corner rays through up to five openings, including corners already across the plane, and respects real destination obstacles.

The optical fixture sweeps eye distances `0.9`, `0.3`, `0.04` and `0.001` blocks, camera pitches `30`, `45`, `60` and `89` degrees, and two yaw angles. Consecutive images check the aperture, source/destination occlusion and hidden terrain sentinels. A sealed dark/lit/dark-again destination is compared with direct native views using actual lamp updates and smooth lighting. Separate body scenes exercise remote self visibility, straddling, crouching and front/back third-person views. HUD/tutorial pixels are excluded from body-color measurements.

## Interaction and pickup

Native block and entity targeting uses one shared ray model on the client and server, bounded to five portal crossings and the player's normal interaction range. Portal scaling contributes to distance. Blocks and entities occlude the ray, including at the exit; the server recomputes the claimed path from loaded geometry. The block selection outline belongs only to the matching remote view. Mining retains the target world when local and remote targeting change, and container validity rechecks the accessible path.

The native-input fixture uses real use/attack keys and checks server outcomes for containers, buttons, placing, breaking, entity use/attack, source and destination obstructions, aperture bounds, reach and mining across a crossing. Projected body interaction has separate cases; passing ordinary remote-entity cases alone does not validate a body half whose entity is anchored in another world. Persistent position-dependent actions such as mounting and trading require their own acceptance evidence.

Automatic item pickup extends the vanilla pickup volume through one accessible opening. It uses the actual aperture, the item's visible portion and vanilla `playerTouch` rules for delay, ownership, inventory space, statistics and removal. One authoritative collection cannot award the same stack twice. Pickup animation includes a server-provided destination anchor for observers who do not have the collector in their loaded world. The quality fixture covers approach, delay, ownership, a full inventory, aperture/range rejection, a two-player race and cross-dimension collection.

## Sound and destination environment

Audio selects the shortest eligible direct or single-portal route within a bounded search radius of at most 128 blocks. The virtual source points toward the opening and encodes the full folded acoustic distance, including portal scale, while native gain, pitch and attenuation remain in charge. This does not implement arbitrary multi-portal acoustic propagation, sound occlusion or diffraction.

Offscreen fog keeps its own atmospheric rain interpolation per renderer and client world. Remote particle engines tick once per remote-world tick, independently of how many portal views are extracted. The environment fixture observes actual redirected sound packets and native sound instances for three routes, counts a remote particle's ticks, and compares a wet Overworld with a dry Nether. It does not establish every biome, fluid or weather transition.

## Two players, persistence and limits

The quality scenario connects two distinct real clients to a dedicated server. They approach, stop, cross in opposite directions and return using normal input. The server also observes natural mob, item and projectile motion, destination projectile impacts, and cross-dimension entity UUID/NBT preservation. The world is saved with both players straddling openings, then the server and both clients restart; saved portal identities, positions and inventories are checked before another natural crossing.

Client frame-time and server tick-time limits are measured on Mesa software runners. They are regression budgets, not a performance promise for a player's GPU. Destination readiness and preload behavior must not be inferred solely from the absence of a crash.

The expanded fixtures are not yet evidence for every held-item/bow-use transition, swimming/flying/falling pose, mount or trading menu, nested sound route, GPU, Sodium/Iris version or shaderpack. Record the exact successful workflow and any remaining gaps in the PR. See the [GitHub validation guide](port-26.2.md) for retained logs and image evidence.
