# Changements

[English](changelog.md) · [Français](changelog.fr.md)

## 7.0.0-alpha.4

- Corrige la traversée des portails placés exactement sur la face d'un mur solide, en conservant les collisions hors de l'ouverture et à destination.

- Stabilise le rendu des portails affleurant un mur grâce à une priorité de profondeur commune aux tests de couverture et à la composition.

## 7.0.0-alpha.3

- Prépare les contextes d'extraction Fabric des vues de portails pour fournir des données de scène valides aux autres mods.
- Ajoute une couverture des consommateurs d'événements avant et après rechargement des ressources, y compris pour les vues imbriquées.

La migration Fabric 26.2 autonome conserve l’historique amont et importe les adaptations 26.2 du fork source Fabric/Minestom. Les compilations, les tests automatisés et les scénarios en jeu sont confiés à GitHub Actions.

Voir l’[historique du dépôt cible](https://github.com/N4TH4NOT/Immersive-Portals/commits/port-26.2), la [provenance des sources](docs/provenance.fr.md) et les [constats de migration](docs/migration-audit-26.2.fr.md). L’état de validation dépend du workflow du commit exact, pas de ce journal.
