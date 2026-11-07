# Changes

[English](changelog.md) · [Français](changelog.fr.md)

## 7.0.0-alpha.4

- Fix traversal through portals placed exactly on a solid wall face while preserving collisions outside the opening and at the destination.

- Stabilize coplanar wall-mounted portal rendering with shared coverage/composition depth priority.

## 7.0.0-alpha.3

- Prepare Fabric extraction contexts for portal views so external render-state consumers receive valid scene data.
- Add event-consumer coverage before and after resource reload, including nested views.

The standalone Fabric 26.2 migration preserves upstream history and imports the 26.2 adaptations from the Fabric/Minestom source fork. Compilation, automated tests and runtime scenarios are assigned to GitHub Actions.

See the [destination commit history](https://github.com/N4TH4NOT/Immersive-Portals/commits/port-26.2), [source provenance](docs/provenance.md) and [migration findings](docs/migration-audit-26.2.md). Validation status belongs to the workflow for the exact commit, not this changelog.
