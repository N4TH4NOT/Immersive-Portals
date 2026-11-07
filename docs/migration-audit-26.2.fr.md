# Constats de migration vers Minecraft 26.2

[English](migration-audit-26.2.md) · [Français](migration-audit-26.2.fr.md)

Ce document distingue le travail de migration hérité de la validation de ce dépôt Fabric autonome. La [provenance](provenance.fr.md) identifie la révision importée ; le [périmètre amont](upstream-1.21-review.fr.md) décrit sa revue antérieure.

## Adaptations héritées

| Domaine | Adaptation |
| --- | --- |
| Compilation et API | Java 25, classes Minecraft sans remappage, dépendances Fabric/Loom actuelles, NBT et codecs de registres. |
| Cycle de vie des mondes | Cycle de vie natif des dimensions, mixins de création, mondes distants et transitions de tickets de chunks. |
| Téléportation et physique | Références/transitions d’entités actuelles, collisions, échelle native, adaptateur de gravité et interpolation. |
| Rendu | Rendu des mondes et composition des portails par Blaze3D sous OpenGL/Vulkan ; adaptateurs Sodium/Iris optionnels distincts. |
| Objets et génération | Définitions d’objets actuelles, règles inverses de génération, commandes, GUI et formats du datapack d’exemple. |
| Réseau | Paquets natifs redirigés et pont client hérité en version 2. |

La migration autonome rétablit l’enregistrement automatique des cinq dimensions alternatives historiques, leur persistance, le choix de la graine du monde, leur création par les empilements et les modèles. L’API interne compatible DimLib remplace la dépendance externe indisponible. Voir les [dimensions alternatives](alternate-dimensions.fr.md) pour la configuration et les preuves GitHub requises ; les seuls exemples de datapacks ne préserveraient pas ces comportements.

Les anciens adaptateurs Pehkui, Flywheel, Porting Lib et Requiem ne sont pas fournis comme intégrations 26.2. L’échelle native est indépendante de Pehkui. Retirer un ancien adaptateur ne démontre pas la compatibilité avec un éventuel remplacement installé.

## Suivi actuel du rendu

Les invalidations de blocs Sodium et de sections voisines sont propagées vers les extracteurs de chaque vue de portail. Ce changement cible le terrain distant obsolète après mise à jour, y compris la reconstruction des maillages liée à l’éclairage, sans nécessiter de shaderpack actif. La correction des collisions sur les murs conserve sa tolérance de `1e-7` bloc ; la couverture et la composition coplanaires conservent leur décalage reverse-Z commun de 16 pas flottants. Aucun de ces changements ne déplace le plan géométrique du portail. La limitation de profondeur des ouvertures planes utilise un calcul du plan du portail sur le CPU en double précision et des uniformes affines de profondeur par pixel, évitant les annulations numériques de l’interpolation du W inverse près de l’œil. Les ouvertures non planes conservent le chemin général de reconstruction. Le contact de collision à destination est actualisé à la position finale de l’entité, sans dépendre d’un index de section encore situé à la source ; il peut rester valide pendant une traversée entre deux ticks grâce à la position précédente ou actuelle des yeux.

Confirmer le comportement combiné avec les résultats GitHub Actions du commit correspondant ; la compilation seule ne le démontre pas. L’approche lente, l’arrêt au niveau du portail, l’inversion des commandes et la traversée avec le mur de support intact sont des critères de déplacement distincts ; les seules corrections de collision ou de rendu ne les démontrent pas. Voir les [détails du rendu](rendering-26.2.fr.md) et le [dépannage](troubleshooting.fr.md) pour le périmètre de l’implémentation.

## Preuves et périmètre restant

Les documents du fork source rapportent des essais manuels antérieurs, sans constituer des preuves rejouées pour ce dépôt. Le workflow GitHub importé démontre seulement la réussite de la compilation et des tests unitaires. Le [workflow de validation](port-26.2.fr.md) du dépôt cible ajoute des contrôles explicites en jeu et conserve les journaux pour la revue.

Une migration complète exige la réussite des compilations/tests du dépôt cible, le fonctionnement des dimensions et de la sauvegarde des mondes, de la génération et des traversées, des collisions/échelles/rotations, des interactions distantes, de la récursion, du rendu GUI, du rechargement des ressources et des deux backends natifs pris en charge. Les intégrations optionnelles demandent des preuves distinctes. Une suite finie de scénarios ne démontre pas toutes les combinaisons de mods ou tous les matériels de rendu.
