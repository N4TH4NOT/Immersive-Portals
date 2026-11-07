# Dépannage

[English](troubleshooting.md) · [Français](troubleshooting.fr.md)

## Crash pendant le rendu ou la réouverture d'un portail sauvegardé

La version `7.0.0-alpha.3` corrige un contexte d'extraction Fabric incomplet dans les vues des portails. Dans les versions précédentes, un autre mod utilisant `LevelExtractionEvents` pouvait provoquer un crash en accédant à `context.levelState()`, comme l'aperçu d'assemblage de BlockLab. Le moteur de rendu hors écran prépare désormais le contexte Fabric avec le monde, l'état de rendu, les moteurs de rendu, la caméra et le suivi du temps de la scène avant l'extraction.

Remplace l'ancien JAR d'Immersive Portals par la version corrigée provenant d'une [exécution GitHub Actions réussie](https://github.com/N4TH4NOT/Immersive-Portals/actions). Cette erreur de rendu ne nécessite pas de supprimer les entités de portail sauvegardées. Sa cause précise est reconnaissable à un `LevelExtractionContext.levelState()` nul dans le rapport de crash ; les autres erreurs demandent un diagnostic distinct.

Pour arrêter temporairement le rendu des portails, ferme le jeu puis règle `"portalRenderLimit": 0` dans le fichier `config/immersive_portals.json` de l'instance. Après installation du JAR corrigé, rétablis la limite précédente (`200` par défaut). Cette option contrôle le rendu, et non les déplacements à travers les portails ou les données sauvegardées du monde.

Le scénario de régression GitHub enregistre un consommateur indépendant des événements Fabric, attache des données à l'état du monde extrait et les relit pendant le rendu. Il vérifie les contextes principal, hors écran et imbriqué avant et après le rechargement des ressources. Cela reproduit l'utilisation de l'API qui révélait le défaut ; cela ne remplace pas les essais de chaque version de BlockLab ou de chaque autre mod.

## Un portail posé sur un mur bloque le passage

La version `7.0.0-alpha.4` corrige une limite de collision du côté origine d'un portail placé exactement sur la face d'un mur solide. Le bloc de support dont la face touche le plan du portail doit être exclu dans l'ouverture pour permettre à la caméra du joueur de franchir le plan. L'ancienne comparaison stricte conservait ce bloc comme obstacle solide.

Le test côté origine accepte désormais une tolérance d'arrondi de collision de `1e-7` bloc. Les blocs hors de l'ouverture et les obstacles solides du côté visible de la destination conservent leurs collisions. Un portail réellement enfoui dans un bloc reste bloqué. Installe la version corrigée pour traverser un portail posé en surface en conservant son mur de support intact.

## La vue du portail clignote avec la texture du mur

La version `7.0.0-alpha.4` donne au portail posé sur un mur une priorité de profondeur cohérente sur la face de bloc située sur la même surface. Les tests de couverture et la composition du portail partagent le même petit décalage relatif de profondeur reverse-Z : les arrondis de rastérisation ne sélectionnent plus alternativement le mur et le portail. Les véritables obstacles au premier plan conservent leur priorité de profondeur. Cet ajustement de rendu ne déplace ni le portail ni son plan de collision.
