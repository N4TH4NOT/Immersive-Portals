# Validation sur GitHub Actions

[English](port-26.2.md) · [Français](port-26.2.fr.md)

Les compilations, les tests automatisés et les scénarios en jeu s’exécutent exclusivement sur [GitHub Actions](https://github.com/N4TH4NOT/Immersive-Portals/actions). Le workflow [build.yml](../.github/workflows/build.yml) fait foi pour les commandes, les dépendances et les délais. Ne pas exécuter ces contrôles localement.

## Preuves de compilation

Le job `build` récupère les sous-modules épinglés, sélectionne Java 25, compile le mod Fabric et ses bibliothèques internes, exécute les tests JUnit, valide l’access widener et compile les scénarios de validation optionnels. Ces scénarios ne sont pas inclus dans le mod distribué. Télécharger les artefacts du passage réussi correspondant au commit exact examiné.

La référence importée dispose d’un [passage CI réussi dans le dépôt source](https://github.com/N4TH4NOT/immersive-portals-minestom/actions/runs/40115478061). Son workflow compilait les scénarios sans les exécuter. Il s’agit d’une preuve historique, pas d’un résultat pour les modifications réalisées ici.

## Preuves en jeu

La matrice `fabric-runtime` lance de vrais clients Minecraft sous OpenGL et Vulkan dans Xvfb, avec les pilotes logiciels Mesa. Ses scénarios multijoueur, portails à échelle modifiée, rendu et solo exercent les serveurs dédiés/intégrés, les traversées, les ressources et le rendu. Huit cas vanilla couvrent les deux backends ; deux cas OpenGL supplémentaires utilisent les intégrations Sodium et Iris épinglées. Le journal doit confirmer le backend demandé ; un repli de Vulkan vers OpenGL doit faire échouer le contrôle Vulkan.

La réussite exige les marqueurs de fin de chaque scénario, notamment `PORTAL_SMOKE_COMPLETE` ou `PORTAL_RENDER_COMPLETE`, leurs marqueurs serveur/ressources spécifiques et l’assertion finale `FABRIC_RUNTIME_VERIFIED`. Les contrôles de dimensions couvrent les graines, le rechargement des codecs et la persistance après relance avec les valeurs par défaut automatiques désactivées. Le rendu est aussi contrôlé par le contenu des pixels des captures, sans considérer qu’une capture non vide prouve le bon affichage des portails. Le simple démarrage d’un processus ne suffit pas. Les journaux de console, les journaux du jeu, les rapports de plantage et les captures sont conservés en artefacts, y compris en cas d’échec.

Examiner les deux jobs pour le même commit. Une compilation seule ne valide ni l’application des mixins, ni la création de monde, les dimensions, les interactions distantes ou le résultat affiché. Consulter journaux et captures avant d’interpréter un succès en jeu ; les scènes automatisées ne couvrent pas chaque fonction, GPU ou shaderpack. Les cas Sodium/Iris épinglés couvrent leurs scènes de rendu fixes ; les autres versions, shaderpacks et mods externes demandent des preuves de compatibilité distinctes.

## Conditions de revue

Indiquer dans la PR le commit exact, le lien du workflow, les jobs réussis ou échoués et les fonctions non exercées. Ne pas déclarer le portage terminé lorsqu’un job requis échoue ou qu’une omission fonctionnelle connue subsiste. Voir les [constats de migration](migration-audit-26.2.fr.md) et l’[architecture du rendu](rendering-26.2.fr.md).
