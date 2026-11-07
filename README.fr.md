# Immersive Portals — Minecraft 26.2 / Fabric

[English](README.md) · [Français](README.fr.md)

Portage Fabric d’[Immersive Portals](https://github.com/iPortalTeam/ImmersivePortalsMod) : voir à travers les portails et voyager entre les mondes sans transition. Le rendu cible les backends natifs OpenGL et Vulkan de Minecraft. Ce dépôt distribue le mod Fabric ; le SDK serveur Minestom reste dans son [propre dépôt](https://github.com/N4TH4NOT/immersive-portals-minestom).

## Installation

Utiliser Minecraft Java **26.2**, Java **25**, Fabric Loader et Fabric API. Les versions exactes des dépendances sont épinglées dans [gradle.properties](gradle.properties).

Télécharger le JAR du mod pour une révision réussie depuis [GitHub Actions](https://github.com/N4TH4NOT/Immersive-Portals/actions), puis le placer dans le dossier `mods` du client et du serveur Fabric avec Fabric API. Cloth Config et l’adaptateur/API de gravité sont embarqués. La gravité directionnelle du joueur nécessite aussi [Gravity Changer](https://github.com/N4TH4NOT/gravity-changer/blob/main/README.fr.md#installation-et-utilisation-fabric). Les artefacts de développement ne sont pas des versions stables.

Sélectionner Vulkan avec l’option Minecraft `--graphicsBackend vulkan`. Iris utilise l’adaptateur OpenGL ; la compatibilité avec chaque shaderpack ou GPU demande une vérification distincte.

## Développement et état

Toutes les compilations, les tests automatisés et les scénarios en jeu s’exécutent **exclusivement sur GitHub Actions**. Le [guide de validation](docs/port-26.2.fr.md) précise les contrôles, les preuves conservées et leurs limites. Une compilation réussie ne démontre pas une compatibilité complète en jeu.

- [Provenance et périmètre](docs/provenance.fr.md), [revue amont](docs/upstream-1.21-review.fr.md) et [constats de migration](docs/migration-audit-26.2.fr.md).
- [Architecture du rendu](docs/rendering-26.2.fr.md), [compatibilité réseau](docs/network-audit-26.2.fr.md) et [intégration de la gravité](docs/gravity-repository.fr.md).
- [Dimensions alternatives intégrées](docs/alternate-dimensions.fr.md) et [API/commandes de dimensions](docs/dimension-library.fr.md).
- [Datapack de génération optionnel](examples/custom_portal_gen/README.fr.md).

La gestion des dimensions, dont l’API et les modèles DimLib, est adaptée en interne sans dépendance DimLib externe. Les anciens adaptateurs Pehkui, Flywheel, Porting Lib et Requiem ne sont pas fournis comme intégrations 26.2. L’échelle native des entités ne nécessite pas Pehkui. Les différences restantes et les preuves requises avant de déclarer la migration terminée figurent dans les [constats de migration](docs/migration-audit-26.2.fr.md).

Création originale de qouteall et des contributeurs, sous [Apache-2.0](LICENSE). Ce portage conserve leur historique et réutilise les adaptations 26.2 du [fork Fabric/Minestom de N4TH4NOT](https://github.com/N4TH4NOT/immersive-portals-minestom/tree/67bf29333b82b0d6c594c65d84256c881f0182b4). Le [wiki amont](https://qouteall.fun/immptl/wiki/) décrit les versions antérieures du moteur.
