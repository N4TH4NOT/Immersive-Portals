# Validation sur GitHub Actions

[English](port-26.2.md) · [Français](port-26.2.fr.md)

Les compilations, les tests automatisés et les scénarios en jeu s’exécutent exclusivement sur [GitHub Actions](https://github.com/N4TH4NOT/Immersive-Portals/actions). Le workflow [build.yml](../.github/workflows/build.yml) fait foi pour les commandes, les dépendances et les délais. Ne pas exécuter ces contrôles localement.

## Preuves de compilation

Le job `build` récupère les sous-modules épinglés, sélectionne Java 25, compile le mod Fabric et ses bibliothèques internes, exécute les tests JUnit, valide l’access widener et compile les scénarios de validation optionnels. Ces scénarios ne sont pas inclus dans le mod distribué. Télécharger les artefacts du passage réussi correspondant au commit exact examiné.

La référence importée dispose d’un [passage CI réussi dans le dépôt source](https://github.com/N4TH4NOT/immersive-portals-minestom/actions/runs/40115478061). Son workflow compilait les scénarios sans les exécuter. Il s’agit d’une preuve historique, pas d’un résultat pour les modifications réalisées ici.

## Preuves en jeu

La matrice `fabric-runtime` lance de vrais clients Minecraft sous OpenGL et Vulkan dans Xvfb, avec les pilotes logiciels Mesa. Ses 20 cas en jeu configurés couvrent le multijoueur, les portails à échelle modifiée, le rendu, l’optique, l’interaction native, la qualité à deux clients et le solo. Les scénarios vanilla couvrent les deux backends, sauf la qualité à deux clients, actuellement exécutée sous OpenGL. Les cas Sodium épinglés couvrent le rendu, l’optique, l’interaction, le multijoueur et la qualité ; les cas Iris épinglés couvrent le rendu et l’optique sous OpenGL. Le journal doit confirmer le backend demandé ; un repli de Vulkan vers OpenGL doit faire échouer le contrôle Vulkan. La validation des cas alpha.6 étendus est en attente : un job configuré n’est pas un résultat réussi.

La réussite exige les marqueurs de fin de chaque scénario, notamment `PORTAL_SMOKE_COMPLETE` ou `PORTAL_RENDER_COMPLETE`, leurs marqueurs serveur/ressources spécifiques et leur assertion CI finale. Les anciens scénarios signalent `FABRIC_RUNTIME_VERIFIED` ; les nouveaux scénarios indépendants disposent des assertions décrites ci-dessous. Les contrôles de dimensions couvrent les graines, le rechargement des codecs et la persistance après relance avec les valeurs par défaut automatiques désactivées. Le rendu est aussi contrôlé par le contenu des pixels des captures, sans considérer qu’une capture non vide prouve le bon affichage des portails. Le simple démarrage d’un processus ne suffit pas. Les journaux de console, les journaux du jeu, les rapports de plantage et les captures sont conservés en artefacts, y compris en cas d’échec.

Examiner les deux jobs pour le même commit. Une compilation seule ne valide ni l’application des mixins, ni la création de monde, les dimensions, les interactions distantes ou le résultat affiché. Consulter journaux et captures avant d’interpréter un succès en jeu ; les scènes automatisées ne couvrent pas chaque fonction, GPU ou shaderpack. Les cas Sodium/Iris épinglés couvrent leurs scènes de rendu fixes ; les autres versions, shaderpacks et mods externes demandent des preuves de compatibilité distinctes.

## Acceptation visuelle et fonctionnelle étendue

Le [suivi de la continuité des portails](seamless-26.2.fr.md) décrit l’implémentation alpha.6 et les cas d’acceptation restants. Ses scénarios indépendants complètent les anciens contrôles du mur et du déplacement :

| Scénario | Preuves exigées |
| --- | --- |
| `optics` | Vues proches inclinées consécutives ; obstacles source/destination et témoins cachés dans le terrain ; images de portail sombre/éclairé/sombre à nouveau comparées aux vues natives de destination et aux véritables niveaux de lumière des blocs. |
| `rendering` | Contrôles existants de profondeur/terrain/rechargement, puis scènes de corps découpés géométriquement et de caméra à la troisième personne ; véritables chemins audio redirigés, comptage des ticks de particules distantes et observations de l’environnement de destination. |
| `interaction` | 18 phases de commandes natives avec effets autoritaires, dont utilisation/attaque de corps projetés, portée ordinaire du seau, commerce maintenu ouvert 25 ticks puis bloqué, montée synchronisée dans un bateau et dégâts mortels d’Épines/objets lâchés dans le monde physique source ; différences de pixels du contour activé/désactivé et minage pendant une traversée. |
| `quality` | Deux véritables clients distincts simultanés ; traversées opposées et caméras arrêtées ; règles et animation du ramassage distant ; mouvement naturel des entités/projectiles et identité entre dimensions ; sauvegarde puis redémarrage du monde et des deux clients ; limites des temps d’image/tick sous Mesa. |
| `multiplayer` | Contrôles existants de déplacement/rechargement, puis sept cas de sprint, accroupissement, vol, chute, nage, arc et alimentation par véritables commandes ; observations indépendantes client/serveur de la posture et de la caméra ; tir à l’arc et consommation après utilisation maintenue entre dimensions. |

La comparaison de pièce sombre exige aussi que les données de lumière du client à destination correspondent à celles du serveur. `ClientLevel.update()` publie les sections en attente du moteur d’éclairage ; faire avancer une lightmap hors écran ou récupérer les paquets de lumière ne démontre pas à lui seul que les valeurs du monde sont à jour. Les nouveaux cas de posture/utilisation et d’interaction compilent avec la suite ; les résultats étendus en jeu restent en attente pour le commit exact.

Le scénario de qualité sauvegarde son propre état dans `portal-smoke-quality.json` puis redémarre le serveur et les deux clients. Son assertion finale est `FABRIC_QUALITY_RESTART_VERIFIED` ; les scénarios d’interaction et d’optique utilisent leurs propres assertions de fin. Examiner ces scripts et leurs marqueurs spécifiques au lieu d’exiger uniquement le marqueur générique des anciens scénarios. Les artefacts conservent les journaux des deux clients de qualité, la sauvegarde d’état, les résumés par scénario et les captures en cas d’échec.

## Portails sur mur et déplacements lents

`RectangularPortalCrossingTest` couvre la reprise d’un mouvement vers l’intérieur depuis le plan exact, les extrémités immobiles ou s’éloignant, les traversées inverses et les ouvertures transformées. Ces assertions unitaires s’exécutent uniquement sur GitHub Actions ; leur réussite est distincte des assertions GPU proches de la caméra.

La scène de rendu conserve le mur de support en pierre et les faces des portails créés à la baguette. Des images GPU consécutives finalisées vérifient la prédiction de visibilité, les requêtes synchrones, un changement d’angle, la composition directe, un véritable obstacle au premier plan et son retrait. Le mur d’émeraude de destination est ajouté pendant le scénario : son apparition exerce aussi les mises à jour du terrain distant. Les séries proches de la caméra incluent des distances œil-plan de `0.04`, `0.01`, `0.001` et `0.000001` bloc, des vues axiales et à 45 degrés, ainsi que la composition directe. Le contrôle à très faible distance exige que la caméra physique reste devant le plan, à `1e-9` bloc près de sa distance cible : déplacer la caméra ne peut donc pas remplacer la correction du rendu de l’ouverture. Ces contrôles d’image n’isolent pas tous les calculs d’éclairage adouci et ne remplacent pas les essais de compatibilité d’un autre mod.

Le nouveau scénario `PortalSeamlessSmoke` utilise les commandes de déplacement ordinaires : approche et arrêt avant le plan, approche accroupie jusqu’à chevauchement du corps, arrêt, recul, traversée lente, arrêt juste après le plan et retour. Il observe les véritables caméras de la vue principale, compare les coordonnées de destination une fois ramenées dans l’espace source et contrôle les limites de déplacement de la caméra, sa direction, la vitesse et la hauteur des pieds. Chaque phase arrêtée inclut au moins huit véritables images extraites : le rattrapage de ticks de simulation ne peut donc pas sauter les contrôles de caméra. L’éclairage adouci est activé et exigé par l’observateur. Les phases arrêtées rejettent tout mouvement ajouté pour terminer la traversée. `PORTAL_SMOKE_SEAMLESS_CAMERA_OK` signale les contrôles de caméra ; `PORTAL_SMOKE_SEAMLESS_MOVEMENT_OK` signale la séquence commandée d’arrêt/recul. Après le cycle entre mondes, une véritable paire de portails à quatre faces créée à la baguette translate le joueur de 64 blocs dans le Nether, soit une distance de quatre chunks. Le joueur rejoint cette seconde ouverture à pied et répète la séquence d’arrêt/recul sans téléportation de préparation. L’observateur exige le bon contact de collision avec le portail inverse dès la première image rendue après chaque traversée. `PORTAL_SMOKE_SEAMLESS_SAME_WORLD_OK` et la confirmation serveur correspondante couvrent cette phase. Le cas avec rotation et changement d’échelle observe aussi la continuité de caméra lors de sa traversée existante.

Utiliser les résultats GitHub Actions et les traces conservées pour le commit correspondant afin de confirmer ces régressions. Une compilation réussie ne démontre pas à elle seule les comportements de déplacement ou de rendu qu’elles exercent.

## Conditions de revue

Indiquer dans la PR le commit exact, le lien du workflow, les jobs réussis ou échoués et les fonctions non exercées. Ne pas déclarer le portage terminé lorsqu’un job requis échoue ou qu’une omission fonctionnelle connue subsiste. Voir les [constats de migration](migration-audit-26.2.fr.md) et l’[architecture du rendu](rendering-26.2.fr.md).
