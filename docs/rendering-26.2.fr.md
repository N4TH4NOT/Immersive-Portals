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

`useDepthClampForPortalRendering` vaut `true` par défaut ; le désactiver conserve le découpage proche/lointain natif. Lorsqu’il est activé, le shader de sommets utilise un Z de découpage neutre afin qu’une ouverture traversant le plan de l’œil reste couverte après découpage. Pour les ouvertures planes, [`NativePortalDepth`](../src/main/java/qouteall/imm_ptl/core/render/NativePortalDepth.java) calcule sur le CPU en double précision l’équation transformée du plan du portail et la convertit en coefficients affines de profondeur fenêtre. La couverture et la composition directe reçoivent ces coefficients comme uniformes et évaluent la profondeur à partir de la position en pixels de chaque fragment. Cela évite les annulations numériques de l’interpolation du W inverse près de l’œil, y compris dans les vues obliques dont certains sommets sont derrière lui.

La profondeur des fragments est bornée à l’intervalle du backend avant application du décalage de surface commun. Les formes non planes, dont les portails en boîte, et les plans transformés dégénérés conservent la reconstruction générale à partir du Z de découpage interpolé et du W inverse. Le découpage par le plan parent reste actif ; aucun des deux chemins ne déplace l’ouverture géométrique. Utiliser les résultats en jeu sur GitHub pour le commit correspondant afin de confirmer ces chemins ; la compilation des shaders ne démontre pas à elle seule le résultat affiché.

## Mises à jour du terrain avec Sodium

Chaque vue distante possède son extracteur et ses maillages de terrain. Les modifications de blocs doivent invalider tous les extracteurs du même monde client, y compris les sections voisines dont l’éclairage du maillage peut changer. Sodium remplace les points d’entrée de ces mises à jour même lorsqu’aucun shaderpack Iris n’est actif.

Les points d’entrée des mises à jour propagent `setBlocksDirty`, `setBlockDirty` et `setSectionDirtyWithNeighbors` vers chaque extracteur. Les plages de blocs incluent les blocs voisins avant conversion en coordonnées de sections ; le garde de propagation empêche les relais récursifs. Les scénarios GitHub exercent des modifications de blocs à destination et les déplacements multijoueurs avec Sodium, dont les traversées dans le même monde et la relance. Leurs scènes fixes n’isolent pas chaque calcul d’occlusion ambiante et ne démontrent pas la compatibilité avec toutes les combinaisons de mods.

## Réglages et validation

Les réglages actuels figurent dans [`IPConfig`](../src/main/java/qouteall/imm_ptl/core/platform_specific/IPConfig.java) et les diagnostics dans [`PortalDebugCommands`](../src/main/java/qouteall/imm_ptl/core/commands/PortalDebugCommands.java). Les options OpenGL obsolètes de tampons/caches ont été retirées. Les anciennes clés JSON sont ignorées.

Le [guide de validation GitHub](port-26.2.fr.md) décrit les scénarios de rendu et les preuves conservées. La suite intégrée ne démontre pas une compatibilité arbitraire avec Sodium/Iris ou les shaderpacks. Le cave culling conserve un repli pour les caméras transformées incompatibles ; le rejet d’octree exige que tout le volume soit hors de la région visible.
