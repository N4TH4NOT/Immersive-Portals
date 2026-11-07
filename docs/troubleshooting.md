# Troubleshooting

[English](troubleshooting.md) · [Français](troubleshooting.fr.md)

## Crash while rendering or reopening a saved portal

Version `7.0.0-alpha.3` fixes an incomplete Fabric extraction context in portal views. In earlier builds, another mod consuming `LevelExtractionEvents` could crash when accessing `context.levelState()`, for example BlockLab's assembly preview. The offscreen renderer now prepares Fabric's context with the scene's world, render state, renderers, camera and delta tracker before extraction.

Replace the previous Immersive Portals JAR with the corrected build from the successful [GitHub Actions run](https://github.com/N4TH4NOT/Immersive-Portals/actions). This rendering failure does not require deleting the saved portal entities. The exact cause is identified by a null `LevelExtractionContext.levelState()` in the crash report; unrelated crashes need their own diagnosis.

To temporarily stop portal rendering, close the game and set `"portalRenderLimit": 0` in the instance's `config/immersive_portals.json`. After installing the corrected JAR, restore the previous limit (`200` by default). The setting controls rendering, not portal travel or saved world data.

The GitHub runtime regression registers an independent Fabric event consumer, attaches data to the extracted world state and reads it during rendering. It checks primary, offscreen and nested contexts before and after resource reload. This reproduces the API usage that exposed the failure; it does not substitute for testing every version of BlockLab or every other mod.
