# Dépannage

[English](troubleshooting.md) · [Français](troubleshooting.fr.md)

## Crash pendant le rendu ou la réouverture d'un portail sauvegardé

La version `7.0.0-alpha.3` corrige un contexte d'extraction Fabric incomplet dans les vues des portails. Dans les versions précédentes, un autre mod utilisant `LevelExtractionEvents` pouvait provoquer un crash en accédant à `context.levelState()`, comme l'aperçu d'assemblage de BlockLab. Le moteur de rendu hors écran prépare désormais le contexte Fabric avec le monde, l'état de rendu, les moteurs de rendu, la caméra et le suivi du temps de la scène avant l'extraction.

Remplace l'ancien JAR d'Immersive Portals par la version corrigée provenant d'une [exécution GitHub Actions réussie](https://github.com/N4TH4NOT/Immersive-Portals/actions). Cette erreur de rendu ne nécessite pas de supprimer les entités de portail sauvegardées. Sa cause précise est reconnaissable à un `LevelExtractionContext.levelState()` nul dans le rapport de crash ; les autres erreurs demandent un diagnostic distinct.

Pour arrêter temporairement le rendu des portails, ferme le jeu puis règle `"portalRenderLimit": 0` dans le fichier `config/immersive_portals.json` de l'instance. Après installation du JAR corrigé, rétablis la limite précédente (`200` par défaut). Cette option contrôle le rendu, et non les déplacements à travers les portails ou les données sauvegardées du monde.

Le scénario de régression GitHub enregistre un consommateur indépendant des événements Fabric, attache des données à l'état du monde extrait et les relit pendant le rendu. Il vérifie les contextes principal, hors écran et imbriqué avant et après le rechargement des ressources. Cela reproduit l'utilisation de l'API qui révélait le défaut ; cela ne remplace pas les essais de chaque version de BlockLab ou de chaque autre mod.

## Le terrain ou l’éclairage d’un portail reste obsolète avec Sodium

Les mises à jour des blocs et sections voisines sont propagées vers toutes les vues de portail du même monde. Auparavant, Sodium pouvait actualiser la vue principale en contournant l’invalidation nécessaire aux maillages du terrain distant. Le changement concerne aussi Sodium seul : ces chemins de mise à jour du terrain ne nécessitent pas de shaderpack Iris actif.

Des artefacts de développement peuvent partager un numéro de version tout en contenant des commits différents. Le commit associé à l’artefact GitHub Actions choisi et ses résultats en jeu permettent d’identifier la modification des mises à jour du terrain. Le scénario de contexte Fabric décrit plus haut utilise un consommateur d’événements indépendant ; il ne démontre pas la compatibilité avec chaque autre mod et n’isole pas tous les calculs d’éclairage adouci.

Pour un signalement reproductible, joindre le nom du JAR d’Immersive Portals, sa version et son SHA-256, le commit de compilation GitHub lorsqu’il est connu, les versions de Sodium et BlockLab, les réglages d’éclairage adouci, la présence d’Iris ou d’un shaderpack actif, ainsi qu’une capture ou une courte vidéo des modifications de blocs. Voir l’[architecture des mises à jour de terrain](rendering-26.2.fr.md#mises-à-jour-du-terrain-avec-sodium) et le [guide de validation GitHub](port-26.2.fr.md).

## Un portail posé sur un mur bloque le passage

La version `7.0.0-alpha.4` corrige une limite de collision du côté origine d'un portail placé exactement sur la face d'un mur solide. Le bloc de support dont la face touche le plan du portail doit être exclu dans l'ouverture pour permettre à la caméra du joueur de franchir le plan. L'ancienne comparaison stricte conservait ce bloc comme obstacle solide.

Le test côté origine accepte une tolérance d’arrondi de collision de `1e-7` bloc. Les blocs hors de l’ouverture et les obstacles solides du côté visible de la destination conservent leurs collisions. Un portail réellement enfoui dans un bloc reste bloqué. Cela traite la limite du mur affleurant ; le relais des contacts à destination et les déplacements lents disposent de contrôles de régression distincts.

## La vue du portail clignote avec la texture du mur

La version `7.0.0-alpha.4` introduit une priorité de profondeur commune au portail et à la face du bloc de support située sur la même surface. La couverture et la composition utilisent le même petit décalage relatif reverse-Z. Pour les ouvertures planes proches de la caméra, le moteur calcule l’équation de profondeur du plan du portail sur le CPU en double précision. La couverture et la composition directe évaluent cette équation par pixel et bornent sa profondeur : l’ouverture est conservée lorsque des sommets passent derrière l’œil, sans interpolation instable du W inverse. Les formes non planes conservent le chemin général de reconstruction de profondeur. Les compositions masquée et directe suivent toutes deux ce chemin lorsque `useDepthClampForPortalRendering` est activé, comme c’est le cas par défaut.

Ces ajustements préservent les plans géométriques et de collision du portail. La scène GitHub vérifie des images consécutives proches de l’ouverture, sous des angles obliques et avec un véritable obstacle au premier plan. Utiliser les résultats en jeu sur GitHub pour le commit correspondant afin de confirmer le comportement de profondeur ; une compilation réussie ne le démontre pas à elle seule.

## Le joueur accroche près de la sortie d’un portail dans le même monde

Après téléportation, l’implémentation actuelle fixe la position finale des yeux et la boîte englobante de l’entité avant de chercher les portails de destination. Elle notifie directement à l’entité ses contacts à destination au lieu de réexaminer une section d’entités qui peut encore désigner la position source pendant une téléportation dans le même monde. Le contact est conservé lorsque la position précédente ou actuelle des yeux reste devant le portail et que le corps chevauche encore son ouverture.

Cela maintient l’exclusion des collisions du mur de support associée au portail inverse pendant un arrêt ou un recul. Pour les portails rectangulaires, un mouvement partant exactement du plan et poursuivant vers l’intérieur compte aussi comme une traversée ; rester sur le plan, simplement l’atteindre ou s’en éloigner ne déclenche pas de téléportation. Ce changement traite le relais des contacts et ne remplace pas le mécanisme existant de correction des chevauchements. Le scénario GitHub de déplacement lent contrôle le mouvement résultant de la caméra et du joueur. Utiliser ses résultats et ses traces pour le commit correspondant afin de confirmer le comportement ; la compilation seule n’exerce pas cette trajectoire.
