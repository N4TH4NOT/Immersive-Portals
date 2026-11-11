# Changements

[English](changelog.md) · [Français](changelog.fr.md)

## 7.0.0-alpha.6 — acceptation suivie sur GitHub

- Met à jour les lightmaps hors écran au rythme des ticks client, publie les sections en attente du moteur d’éclairage distant par les mises à jour natives du monde et fait avancer les particules distantes une seule fois.
- Découpe les corps des joueurs indépendamment des vues de portail, préserve les vues légitimes du joueur au loin et suit les coins de caméra à la troisième personne à travers les ouvertures.
- Rétablit le ciblage natif des blocs/entités, les contours de sélection distants et la continuité du monde miné avec validation du chemin serveur ; ajoute le ramassage distant automatique selon les règles vanilla.
- Isole le tick serveur entier du minage dans son monde enregistré ; conserve la portée ordinaire du seau, le commerce distant persistant, la montée synchronisée et la gestion des dégâts renvoyés/mort/objets lâchés dans le monde physique. Exerce les éditeurs de pancartes ordinaires et suspendues avec une véritable saisie au clavier et le bouton natif Terminé, en exigeant la modification du texte à destination et une pancarte source intacte.
- Échantillonne les contacts natifs de fluide sur les parties du corps physique source et projetée à destination sans déplacer l’entité ; ajoute neuf cas d’eau/lave, dont des ouvertures avec changement d’échelle et rotation, un bord d’ouverture fini, la lave pour une entité non joueuse et la force du courant de lave à destination, ainsi que trois contrats indépendants de comparaison de la force native.
- Sépare le mouvement natif des flèches, projectiles lancés et offensifs aux limites des portails tout en conservant les impacts à destination et une seule intégration de vitesse. Les flotteurs de pêche, feux d’artifice, crachats de lama et projectiles de Shulker conservent leur chemin serveur hérité ; les nouveaux contrôles de segments ne couvrent pas leurs comportements particuliers.
- Conserve la distance sonore dépliée et l’atténuation native ; isole l’interpolation de pluie du brouillard atmosphérique par moteur de rendu et monde.
- Étend les scénarios exclusivement sur GitHub avec l’optique inclinée, les comparaisons de pièces réellement sombres, les corps découpés, 20 phases d’interaction native, sept traversées de posture/utilisation maintenue, neuf cas de contact de fluide et trois contrats de comparaison de la force native, sept témoins d’entités, l’observation de l’environnement, deux véritables joueurs et les contrôles de sauvegarde/reconnexion. Les traversées avec arc et alimentation maintiennent l’utilisation entre dimensions.

Le [suivi de la continuité des portails](docs/seamless-26.2.fr.md) distingue la couverture d’acceptation implémentée des preuves en jeu et indique les limites restantes. Consulter la [PR nº 1](https://github.com/N4TH4NOT/Immersive-Portals/pull/1) et les [résultats GitHub Actions](https://github.com/N4TH4NOT/Immersive-Portals/actions) pour les contrôles réussis, échoués ou non exécutés de la révision exacte. Configurer un scénario ne démontre pas sa réussite.

## 7.0.0-alpha.4

- Corrige la traversée des portails placés exactement sur la face d'un mur solide, en conservant les collisions hors de l'ouverture et à destination.

- Stabilise le rendu des portails affleurant un mur grâce à une priorité de profondeur commune aux tests de couverture et à la composition.

## 7.0.0-alpha.3

- Prépare les contextes d'extraction Fabric des vues de portails pour fournir des données de scène valides aux autres mods.
- Ajoute une couverture des consommateurs d'événements avant et après rechargement des ressources, y compris pour les vues imbriquées.

La migration Fabric 26.2 autonome conserve l’historique amont et importe les adaptations 26.2 du fork source Fabric/Minestom. Les compilations, les tests automatisés et les scénarios en jeu sont confiés à GitHub Actions.

Voir l’[historique du dépôt cible](https://github.com/N4TH4NOT/Immersive-Portals/commits/port-26.2), la [provenance des sources](docs/provenance.fr.md) et les [constats de migration](docs/migration-audit-26.2.fr.md). L’état de validation dépend du workflow du commit exact, pas de ce journal.
