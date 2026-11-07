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

L’implémentation importée avait supprimé l’enregistrement automatique des cinq dimensions alternatives historiques. Rétablir leur cycle de vie et leur configuration fait partie de la migration autonome ; les seuls exemples de datapacks ne préservent pas ce comportement. Consulter l’implémentation actuelle et la CI du dépôt cible pour connaître son état.

Les anciens adaptateurs externes DimLib, Pehkui, Flywheel, Porting Lib et Requiem ne sont pas fournis comme intégrations 26.2. L’échelle native est indépendante de Pehkui. Retirer un ancien adaptateur ne démontre pas la compatibilité avec un éventuel remplacement installé.

## Preuves et périmètre restant

Les documents du fork source rapportent des essais manuels antérieurs, sans constituer des preuves rejouées pour ce dépôt. Le workflow GitHub importé démontre seulement la réussite de la compilation et des tests unitaires. Le [workflow de validation](port-26.2.fr.md) du dépôt cible ajoute des contrôles explicites en jeu et conserve les journaux pour la revue.

Une migration complète exige la réussite des compilations/tests du dépôt cible, le fonctionnement des dimensions et de la sauvegarde des mondes, de la génération et des traversées, des collisions/échelles/rotations, des interactions distantes, de la récursion, du rendu GUI, du rechargement des ressources et des deux backends natifs pris en charge. Les intégrations optionnelles demandent des preuves distinctes. Une suite finie de scénarios ne démontre pas toutes les combinaisons de mods ou tous les matériels de rendu.
