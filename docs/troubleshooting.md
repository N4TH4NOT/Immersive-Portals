# Troubleshooting

[English](troubleshooting.md) · [Français](troubleshooting.fr.md)

## Crash while rendering or reopening a saved portal

Version `7.0.0-alpha.3` fixes an incomplete Fabric extraction context in portal views. In earlier builds, another mod consuming `LevelExtractionEvents` could crash when accessing `context.levelState()`, for example BlockLab's assembly preview. The offscreen renderer now prepares Fabric's context with the scene's world, render state, renderers, camera and delta tracker before extraction.

Replace the previous Immersive Portals JAR with the corrected build from the successful [GitHub Actions run](https://github.com/N4TH4NOT/Immersive-Portals/actions). This rendering failure does not require deleting the saved portal entities. The exact cause is identified by a null `LevelExtractionContext.levelState()` in the crash report; unrelated crashes need their own diagnosis.

To temporarily stop portal rendering, close the game and set `"portalRenderLimit": 0` in the instance's `config/immersive_portals.json`. After installing the corrected JAR, restore the previous limit (`200` by default). The setting controls rendering, not portal travel or saved world data.

The GitHub runtime regression registers an independent Fabric event consumer, attaches data to the extracted world state and reads it during rendering. It checks primary, offscreen and nested contexts before and after resource reload. This reproduces the API usage that exposed the failure; it does not substitute for testing every version of BlockLab or every other mod.

## Sodium portal terrain or lighting remains stale

The development change in [commit `9a90027b`](https://github.com/N4TH4NOT/Immersive-Portals/commit/9a90027b955914ecdfd1363b489fcc788e5f2cfa) forwards block and neighboring-section updates to every portal view of the same world. Previously, Sodium could update the main view while bypassing the invalidation needed by remote terrain meshes. The change applies with Sodium alone; an active Iris shaderpack is not required to use those terrain update paths.

Development artifacts can share a version string while containing different commits. Use the commit associated with the selected GitHub Actions artifact and its runtime results to identify the terrain-update change. The Fabric-context regression above uses an independent event consumer; it does not establish compatibility with every other mod or isolate all smooth-lighting calculations.

For a reproducible report, include the Immersive Portals JAR filename, version and SHA-256, the GitHub build commit when available, the Sodium and BlockLab versions, smooth-lighting settings, whether Iris or a shaderpack is active, and a screenshot or short recording showing the block changes. See the [terrain-update design](rendering-26.2.md#terrain-updates-with-sodium) and [GitHub validation guide](port-26.2.md).

## Wall-mounted portal blocks movement

Version `7.0.0-alpha.4` fixes a collision boundary at the origin side of a portal placed exactly on a solid wall face. A backing block whose face touches the portal plane must be excluded within the opening so that the player's camera can cross the plane. The earlier strict comparison retained that block as a solid obstacle.

The origin-side test allows a collision rounding tolerance of `1e-7` blocks. Blocks outside the portal opening and solid obstacles on the visible destination side retain their collisions. A portal genuinely embedded inside a block is still blocked. This addresses the flush-wall boundary; destination contact handoff and slow movement have separate regression checks.

## Portal view flickers with the backing block texture

Version `7.0.0-alpha.4` introduces shared depth priority for a portal and a backing block face on the same surface. Coverage and composition use the same small relative reverse-Z offset. The current shader update also handles an aperture crossing the camera's near plane: it preserves its coverage when corners pass behind the eye, then reconstructs and clamps surface depth in the fragment shader. Both masked and direct composition follow this depth-clamp path with `useDepthClampForPortalRendering` enabled, as it is by default.

These adjustments preserve the portal's geometric and collision planes. The GitHub scene checks consecutive frames close to the aperture, at oblique angles, and with a real foreground occluder. Validation of the latest shader combination is pending its GitHub Actions run.

## The player catches near a same-world portal exit

After teleportation, the current implementation sets the entity's final eye position and bounding box before discovering destination portals. It notifies that entity directly of its destination contacts instead of rescanning an entity section that can still refer to the source position during a same-world teleport. Contact is retained while either the previous or current eye position remains in front of the portal and the body still overlaps its opening.

This keeps the backing-wall collision clipping associated with the reverse portal during a stop or reversal. For rectangular portals, a movement starting exactly on the plane and continuing inward is also a crossing; stopping on the plane, merely arriving there or moving away is not. It changes contact handoff and does not replace the existing anti-overlap handling. The slow-movement GitHub regression checks the resulting camera and player motion; validation of this revision is pending.
