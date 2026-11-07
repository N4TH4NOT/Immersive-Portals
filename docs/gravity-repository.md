# Gravity repository

[English](gravity-repository.md) · [Français](gravity-repository.fr.md)

[`gravity-changer`](https://github.com/N4TH4NOT/gravity-changer) owns the Apache-2.0 gravity implementation. `dependencies/gravity` pins its revision; the Gradle composite supplies the adapter libraries without a remote Maven publication.

GitHub Actions checks out submodules recursively and builds the pinned dependency. Publish gravity commits before advancing the submodule. All compilation, tests and runtime scenarios for this repository run only on GitHub Actions; consult the [validation guide](port-26.2.md).

Portal facades and message envelopes remain here. Gravity implementation and its integration fixtures live in the gravity repository. See its [integration guide](https://github.com/N4TH4NOT/gravity-changer/blob/main/docs/gravity-26.2.md) and [Fabric installation guide](https://github.com/N4TH4NOT/gravity-changer#fabric-installation-and-use). The bundled adapter/API does not replace the additional runtime mod needed for directional player gravity.
