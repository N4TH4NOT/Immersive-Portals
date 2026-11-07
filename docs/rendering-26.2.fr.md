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

## Réglages et validation

Les réglages actuels figurent dans [`IPConfig`](../src/main/java/qouteall/imm_ptl/core/platform_specific/IPConfig.java) et les diagnostics dans [`PortalDebugCommands`](../src/main/java/qouteall/imm_ptl/core/commands/PortalDebugCommands.java). Les options OpenGL obsolètes de tampons/caches ont été retirées. Les anciennes clés JSON sont ignorées.

Le [guide de validation GitHub](port-26.2.fr.md) décrit les scénarios de rendu et les preuves conservées. La suite intégrée ne démontre pas une compatibilité arbitraire avec Sodium/Iris ou les shaderpacks. Le cave culling conserve un repli pour les caméras transformées incompatibles ; le rejet d’octree exige que tout le volume soit hors de la région visible.
