# Upstream 1.21 baseline review

[English](upstream-1.21-review.md) · [Français](upstream-1.21-review.fr.md)

The imported source fork records an October 20, 2026 review of baseline `0dbdc1d493c3906943147668092a41fc67a49527` → upstream `2b1d3546aa60c7d866adfcb7236fba136a8433cb`: 69 commits, including three merges. That review concerns the upstream 1.21.1 behavior inherited by its 26.2 adaptations; it is historical source analysis, not a new gameplay result for this repository.

The destination also preserves the [upstream 1.21.3 snapshot](https://github.com/iPortalTeam/ImmersivePortalsMod/tree/6831a3a72ba159d19951d80929a5809868bb08f9), revision `6831a3a72ba159d19951d80929a5809868bb08f9`, as the pre-migration baseline. Differences from the 26.2 implementation require behavior review rather than indiscriminate cherry-picking of obsolete engine adapters.

| Behavior to preserve | Representative commits |
| --- | --- |
| Native SCALE modifier, external modifiers and eye placement | `874588b8`, `c148926d`, `cabd8b69` |
| Interpolation timing and projection copies | `89c35c2b`, `f1e09897` |
| Third-person camera and GUI viewport APIs | `ec60bedc`, `5127ad56`, `6bbc5bfa` |
| Container identity/validity separate from reach | `6bb58c52` |
| Serialization without mutating published meshes | `a9bd638e` |
| Chunk/attachment redirection and registry-bound codecs | `b7d6ac94`, `c7ecf525` |
| Frame searches, delayed reverse-portal lookup and saved item conversion | `3a5de6b9`, `253d77dc`, `632789a8`, `672fca43` |
| Explicit render-resource cleanup; removal of portal grouping | `7fa7ab17`, `0e34101a`, `4facf06f`, `a3291b1f` |

The Sodium/Iris series contains reverts and reapplications: port its final behavior, not every intermediate patch. Its old OpenGL mixins and dependency pins do not supply a 26.2 backend. Pehkui is not required for native scale; volumetric shapes already existed before this range.

The review also identified exclusive chunk-bound errors and legacy GUI wrappers ignoring viewport arguments. These require explicit adaptation, not blind reuse. Current fixes and verification are recorded in [migration findings](migration-audit-26.2.md).

The commit range remains available for history/source inspection. Current compilation, tests and runtime scenarios run exclusively on GitHub Actions, as described in the [validation guide](port-26.2.md).
