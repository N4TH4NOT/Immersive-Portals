# Network compatibility

[English](network-audit-26.2.md) · [Français](network-audit-26.2.fr.md)

The Fabric mod retains native redirected world packets and the version-2 client bridge imported from the [source fork](https://github.com/N4TH4NOT/immersive-portals-minestom/tree/67bf29333b82b0d6c594c65d84256c881f0182b4). Bridge messages and bounds live in [`BridgeMessage`](../portal-protocol/src/main/java/io/github/n4th4not/immersiveportals/protocol/BridgeMessage.java) and [`BridgeCodec`](../portal-protocol/src/main/java/io/github/n4th4not/immersiveportals/protocol/BridgeCodec.java); accepted native packet types are defined in [`BridgeClient`](../src/main/java/qouteall/imm_ptl/core/network/bridge/BridgeClient.java).

The Fabric client and server must use the same mod build. The internal crossing packet now snapshots source feet and pose; the server reconstructs the eye offset from its own dimensions, scale and gravity, validates reach and portal access, and preserves that physical anchor even when a posture command arrives later. Earlier alpha crossing payloads are incompatible with this packet. This change does not alter the version-2 Minestom bridge framing.

Remote-view packets replace the corresponding ordinary delivery rather than duplicating the current world's stream. Fragment headers identify bounded reassembly contexts. Repeated events such as sounds, damage or prediction acknowledgements may be intentional; equal bytes alone do not justify suppressing them.

The source fork's [network audit](https://github.com/N4TH4NOT/immersive-portals-minestom/blob/67bf29333b82b0d6c594c65d84256c881f0182b4/docs/network-audit-26.2.md) documents its Minestom sender optimization, tests and recorded packet counts. Those server modules and their tests are outside this Fabric distribution. Their historical results must not be reported as tests executed in this repository.

Current Fabric protocol tests and runtime traversal/interaction evidence belong to [GitHub Actions](port-26.2.md). Bridge peers must target the same Minecraft protocol; changing bridge framing or the 28 retained legacy `OpenView` bytes requires negotiated protocol evolution.
