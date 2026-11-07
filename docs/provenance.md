# Source provenance and repository scope

[English](provenance.md) · [Français](provenance.fr.md)

The original mod is [iPortalTeam/ImmersivePortalsMod](https://github.com/iPortalTeam/ImmersivePortalsMod), by qouteall and contributors, licensed under [Apache-2.0](../LICENSE). Its commit history and copyright notices are retained.

The 26.2 implementation is imported from [N4TH4NOT/immersive-portals-minestom](https://github.com/N4TH4NOT/immersive-portals-minestom/tree/67bf29333b82b0d6c594c65d84256c881f0182b4), revision `67bf29333b82b0d6c594c65d84256c881f0182b4`. That repository is an upstream fork. The import preserves its individual migration commits and authors, rather than presenting those adaptations as new work in this repository.

The pre-migration branch preserves the [upstream 1.21.3 snapshot](https://github.com/iPortalTeam/ImmersivePortalsMod/tree/6831a3a72ba159d19951d80929a5809868bb08f9), revision `6831a3a72ba159d19951d80929a5809868bb08f9`, alongside the destination initialization.

This repository targets the standalone Fabric mod. `portal-protocol` remains an internal dependency for the inherited client bridge; the Minestom server SDK and example application belong to the source fork. Gravity is supplied by the Apache-2.0 [gravity-changer repository](https://github.com/N4TH4NOT/gravity-changer), pinned by the submodule entry in Git and [.gitmodules](../.gitmodules).

The destination repository existed independently before the import. Importing Git history preserves ancestry and attribution, but does not change GitHub's repository-level fork relationship. No repository deletion or history replacement is required for this import.

Historical validation from another repository is identified as such. Current validation is the GitHub Actions run attached to the exact destination commit; see the [validation guide](port-26.2.md).
