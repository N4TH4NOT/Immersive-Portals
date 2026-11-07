# Changements

[English](changelog.md) · [Français](changelog.fr.md)

## 7.0.0-alpha.6 — validation en attente

- Met à jour les lightmaps hors écran au rythme des ticks client, publie les sections en attente du moteur d’éclairage distant par les mises à jour natives du monde et fait avancer les particules distantes une seule fois.
- Découpe les corps des joueurs indépendamment des vues de portail, préserve les vues légitimes du joueur au loin et suit les coins de caméra à la troisième personne à travers les ouvertures.
- Rétablit le ciblage natif des blocs/entités, les contours de sélection distants et la continuité du monde miné avec validation du chemin serveur ; ajoute le ramassage distant automatique selon les règles vanilla.
- Isole le tick serveur entier du minage dans son monde enregistré ; conserve la portée ordinaire du seau, le commerce distant persistant, la montée synchronisée et la gestion des dégâts renvoyés/mort/objets lâchés dans le monde physique.
- Conserve la distance sonore dépliée et l’atténuation native ; isole l’interpolation de pluie du brouillard atmosphérique par moteur de rendu et monde.
- Étend les scénarios exclusivement sur GitHub avec l’optique inclinée, les comparaisons de pièces réellement sombres, les corps découpés, 18 phases d’interaction native, sept traversées de posture/utilisation maintenue, l’observation de l’environnement, deux véritables joueurs et les contrôles de sauvegarde/reconnexion. Les traversées avec arc et alimentation maintiennent l’utilisation entre dimensions.

Le [suivi de la continuité des portails](docs/seamless-26.2.fr.md) distingue l’implémentation des preuves d’acceptation en attente et indique les limites de couverture restantes.

## 7.0.0-alpha.4

- Corrige la traversée des portails placés exactement sur la face d'un mur solide, en conservant les collisions hors de l'ouverture et à destination.

- Stabilise le rendu des portails affleurant un mur grâce à une priorité de profondeur commune aux tests de couverture et à la composition.

## 7.0.0-alpha.3

- Prépare les contextes d'extraction Fabric des vues de portails pour fournir des données de scène valides aux autres mods.
- Ajoute une couverture des consommateurs d'événements avant et après rechargement des ressources, y compris pour les vues imbriquées.

La migration Fabric 26.2 autonome conserve l’historique amont et importe les adaptations 26.2 du fork source Fabric/Minestom. Les compilations, les tests automatisés et les scénarios en jeu sont confiés à GitHub Actions.

Voir l’[historique du dépôt cible](https://github.com/N4TH4NOT/Immersive-Portals/commits/port-26.2), la [provenance des sources](docs/provenance.fr.md) et les [constats de migration](docs/migration-audit-26.2.fr.md). L’état de validation dépend du workflow du commit exact, pas de ce journal.
