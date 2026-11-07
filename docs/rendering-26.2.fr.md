# Choix de rendu

[English](rendering-26.2.md) · [Français](rendering-26.2.fr.md)

Le rendu principal utilise Blaze3D sur les deux backends Minecraft. Iris dispose d’un adaptateur OpenGL séparé pour ses cibles actives ; ses pipelines internes ne constituent pas le backend général du rendu des portails.

## Visibilité et composition

La couverture du portail est testée contre la profondeur parente sans la modifier. La réduction GPU retourne quatre octets ; les historiques sont isolés par vue et chemin récursif. Les changements de géométrie, caméra, projection ou cible invalident la réutilisation. Une mesure absente ou obsolète laisse la vue visible.

La prédiction réutilise les résultats récents compatibles. Sans prédiction, la mesure de l’image courante dispose d’une attente bornée et rend la vue par défaut. Des snapshots par vue isolent également frustums natifs/Sodium et invalidations du terrain des tâches asynchrones.

| Mode | Composition |
| --- | --- |
| Normal / Iris normal | Stencil en texture limitant une passe plein écran. |
| Compatibilité / Iris compatibilité | Triangles du portail avec découpage explicite par le plan parent. |
| Debug / désactivé | Première vue distante plein écran / aucun monde distant rendu. |
| Iris expérimental | Prépasses de visibilité groupées et masques bornés. |

Les stencils en texture remplacent les opérations S8 indisponibles dans l’API native. Un changement de mode préserve les pipelines déjà extraits pour l’image ; un rechargement libère contextes et masques.

Pour les murs de support coplanaires, la couverture et la composition partagent `portalSurfaceDepth` : un décalage reverse-Z de 16 pas flottants représentables, borné à l’intervalle de profondeur. La composition masquée lit la profondeur déjà décalée dans sa texture de couverture, sans appliquer deux fois le décalage. L’ajustement concerne la profondeur de rastérisation et laisse inchangés les plans géométriques du portail et de collision.

`useDepthClampForPortalRendering` vaut `true` par défaut ; le désactiver conserve le découpage proche/lointain natif. Lorsqu’il est activé, le shader de sommets conserve un Z de découpage neutre tout en transmettant le Z de découpage original de chaque sommet pour interpolation. Le shader de fragments reconstruit la profondeur à partir de cette valeur interpolée et du W inverse, puis la borne à l’intervalle de profondeur du backend avant d’appliquer le décalage de surface commun. Cela émule la limitation de profondeur sur toute l’ouverture, y compris pour les sommets dont W est négatif, au lieu de borner seulement ceux situés devant l’œil. Le découpage par le plan parent reste actif. Le scénario proche de la caméra et son résultat GitHub déterminent si cette révision des shaders réussit les vues exercées.

## Mises à jour du terrain avec Sodium

Chaque vue distante possède son extracteur et ses maillages de terrain. Les modifications de blocs doivent invalider tous les extracteurs du même monde client, y compris les sections voisines dont l’éclairage du maillage peut changer. Sodium remplace les points d’entrée de ces mises à jour même lorsqu’aucun shaderpack Iris n’est actif.

Le [commit `9a90027b`](https://github.com/N4TH4NOT/Immersive-Portals/commit/9a90027b955914ecdfd1363b489fcc788e5f2cfa) propage les appels `setBlocksDirty`, `setBlockDirty` et `setSectionDirtyWithNeighbors` vers chaque extracteur. Les plages de blocs incluent les blocs voisins avant conversion en coordonnées de sections ; le garde de propagation existant empêche les relais récursifs. Il s’agit du changement dans les sources. Son effet sur un problème observé de maillage obsolète ou d’éclairage adouci demande les preuves GitHub de la révision qui l’inclut.

## Réglages et validation

Les réglages actuels figurent dans [`IPConfig`](../src/main/java/qouteall/imm_ptl/core/platform_specific/IPConfig.java) et les diagnostics dans [`PortalDebugCommands`](../src/main/java/qouteall/imm_ptl/core/commands/PortalDebugCommands.java). Les options OpenGL obsolètes de tampons/caches ont été retirées. Les anciennes clés JSON sont ignorées.

Le [guide de validation GitHub](port-26.2.fr.md) décrit les scénarios de rendu et les preuves conservées. La suite intégrée ne démontre pas une compatibilité arbitraire avec Sodium/Iris ou les shaderpacks. Le cave culling conserve un repli pour les caméras transformées incompatibles ; le rejet d’octree exige que tout le volume soit hors de la région visible.
