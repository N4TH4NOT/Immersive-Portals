# Troubleshooting

[English](troubleshooting.md) · [Français](troubleshooting.fr.md)

## Crash while rendering or reopening a saved portal

Version `7.0.0-alpha.3` fixes an incomplete Fabric extraction context in portal views. In earlier builds, another mod consuming `LevelExtractionEvents` could crash when accessing `context.levelState()`, for example BlockLab's assembly preview. The offscreen renderer now prepares Fabric's context with the scene's world, render state, renderers, camera and delta tracker before extraction.

Replace the previous Immersive Portals JAR with the corrected build from the successful [GitHub Actions run](https://github.com/N4TH4NOT/Immersive-Portals/actions). This rendering failure does not require deleting the saved portal entities. The exact cause is identified by a null `LevelExtractionContext.levelState()` in the crash report; unrelated crashes need their own diagnosis.

To temporarily stop portal rendering, close the game and set `"portalRenderLimit": 0` in the instance's `config/immersive_portals.json`. After installing the corrected JAR, restore the previous limit (`200` by default). The setting controls rendering, not portal travel or saved world data.

The GitHub runtime regression registers an independent Fabric event consumer, attaches data to the extracted world state and reads it during rendering. It checks primary, offscreen and nested contexts before and after resource reload. This reproduces the API usage that exposed the failure; it does not substitute for testing every version of BlockLab or every other mod.

## Sodium portal terrain or lighting remains stale

Block and neighboring-section updates are forwarded to every portal view of the same world. Previously, Sodium could update the main view while bypassing the invalidation needed by remote terrain meshes. The change applies with Sodium alone; an active Iris shaderpack is not required to use those terrain update paths.

Development artifacts can share a version string while containing different commits. Use the commit associated with the selected GitHub Actions artifact and its runtime results to identify the terrain-update change. The Fabric-context regression above uses an independent event consumer; it does not establish compatibility with every other mod or isolate all smooth-lighting calculations.

For a reproducible report, include the Immersive Portals JAR filename, version and SHA-256, the GitHub build commit when available, the Sodium and BlockLab versions, smooth-lighting settings, whether Iris or a shaderpack is active, and a screenshot or short recording showing the block changes. See the [terrain-update design](rendering-26.2.md#terrain-updates-with-sodium) and [GitHub validation guide](port-26.2.md).

## A dark destination appears bright, or the player's head appears during crossing

The alpha.6 follow-up separately addresses offscreen lightmap updates, publication of pending remote light-engine sections, clipping of player bodies and third-person camera collision. Stale terrain meshes, missing client-world light data and an uninitialized remote lightmap are different failures; none is established by a successful compilation. Expanded dark-room, pitched-camera, body-image and seven pose/held-use checks are pending on GitHub Actions. See the [implementation and acceptance scope](seamless-26.2.md) before treating an older successful artifact as evidence for these changes.

## Blocks, entities or selection outlines do not work through a portal

The alpha.6 native interaction path traces blocks and entities through accessible openings, checks ordinary reach and obstacles on the server, and extracts the block outline only in the matching remote view. Remote pickup uses its own automatic collection path and vanilla ownership/delay/inventory rules. The 18 native-input phases include projected-body targeting, survival bucket reach, persistent trading with obstruction closure, synchronized boat mounting and reflected damage in the physical source world. These behaviors have separate native-input, pixel and pickup checks in the expanded GitHub scenarios; their validation is pending. A render-only pass does not establish interaction or collection success. Include the exact artifact commit and whether the target is a block, an entire entity, a projected body half or a dropped item when reporting a remaining failure.

## Wall-mounted portal blocks movement

Version `7.0.0-alpha.4` fixes a collision boundary at the origin side of a portal placed exactly on a solid wall face. A backing block whose face touches the portal plane must be excluded within the opening so that the player's camera can cross the plane. The earlier strict comparison retained that block as a solid obstacle.

The origin-side test allows a collision rounding tolerance of `1e-7` blocks. Blocks outside the portal opening and solid obstacles on the visible destination side retain their collisions. A portal genuinely embedded inside a block is still blocked. This addresses the flush-wall boundary; destination contact handoff and slow movement have separate regression checks.

## Portal view flickers with the backing block texture

Version `7.0.0-alpha.4` introduces shared depth priority for a portal and a backing block face on the same surface. Coverage and composition use the same small relative reverse-Z offset. For planar apertures near the camera, the renderer computes the portal-plane depth equation on the CPU in double precision. Coverage and direct composition evaluate that equation per pixel and clamp its depth, preserving the aperture when corners pass behind the eye without unstable reciprocal-W interpolation. Nonplanar shapes retain the general depth-reconstruction path. Both masked and direct composition follow this depth-clamp path with `useDepthClampForPortalRendering` enabled, as it is by default.

With Sodium, aperture placement and parent clipping also match the rounded origin used to render chunk terrain. If rounding reaches or crosses the aperture plane while the real eye is still in front, the planar aperture uses the real origin instead. This changes only the aperture's rendering basis and leaves the physical camera in place.

These adjustments preserve the portal's geometric and collision planes. The GitHub scene checks consecutive frames close to the aperture, at oblique angles, and with a real foreground occluder. Use the GitHub runtime results for the matching commit to confirm the depth behavior; a successful build alone does not establish it.

## The player catches near a same-world portal exit

Crossing detection rejects tangent rounding noise using a tolerance based on local floating-point precision. It scales with the aperture, including very small portals, rather than imposing a fixed movement threshold.

After teleportation, the current implementation sets the entity's final eye position and bounding box before discovering destination portals. It notifies that entity directly of its destination contacts instead of rescanning an entity section that can still refer to the source position during a same-world teleport. Contact is retained while either the previous or current eye position remains in front of the portal and the body still overlaps its opening.

This keeps the backing-wall collision clipping associated with the reverse portal during a stop or reversal. For rectangular portals, a movement starting exactly on the plane and continuing inward is also a crossing; stopping on the plane, merely arriving there or moving away is not. It changes contact handoff and does not replace the existing anti-overlap handling. The slow-movement GitHub regression checks the resulting camera and player motion. Use its results and traces for the matching commit to confirm the behavior; compilation alone does not exercise the trajectory.
