# Troubleshooting

[English](troubleshooting.md) · [Français](troubleshooting.fr.md)

## Crash while rendering or reopening a saved portal

Version `7.0.0-alpha.3` fixes an incomplete Fabric extraction context in portal views. In earlier builds, another mod consuming `LevelExtractionEvents` could crash when accessing `context.levelState()`, for example BlockLab's assembly preview. The offscreen renderer now prepares Fabric's context with the scene's world, render state, renderers, camera and delta tracker before extraction.

Replace the previous Immersive Portals JAR with the corrected build from the successful [GitHub Actions run](https://github.com/N4TH4NOT/Immersive-Portals/actions). This rendering failure does not require deleting the saved portal entities. The exact cause is identified by a null `LevelExtractionContext.levelState()` in the crash report; unrelated crashes need their own diagnosis.

To temporarily stop portal rendering, close the game and set `"portalRenderLimit": 0` in the instance's `config/immersive_portals.json`. After installing the corrected JAR, restore the previous limit (`200` by default). The setting controls rendering, not portal travel or saved world data.

The GitHub runtime regression registers an independent Fabric event consumer, attaches data to the extracted world state and reads it during rendering. It checks primary, offscreen and nested contexts before and after resource reload. This reproduces the API usage that exposed the failure; it does not substitute for testing every version of BlockLab or every other mod.

## Wall-mounted portal blocks movement

Version `7.0.0-alpha.4` fixes a collision boundary at the origin side of a portal placed exactly on a solid wall face. A backing block whose face touches the portal plane must be excluded within the opening so that the player's camera can cross the plane. The earlier strict comparison retained that block as a solid obstacle.

The origin-side test now allows a collision rounding tolerance of `1e-7` blocks. Blocks outside the portal opening and solid obstacles on the visible destination side retain their collisions. A portal genuinely embedded inside a block is still blocked. Install the corrected build to traverse a surface-mounted portal with its backing wall intact.

## Portal view flickers with the backing block texture

Version `7.0.0-alpha.4` gives a wall-mounted portal consistent depth priority over a block face on the same surface. Coverage checks and portal composition share the same small, relative reverse-Z depth offset, so frame-to-frame raster rounding does not alternately select the wall and the portal. Real foreground occluders retain their depth priority. This rendering adjustment does not move the portal or change its collision plane.
