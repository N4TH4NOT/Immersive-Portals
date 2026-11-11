# Bibliothèque de dimensions intégrée

[English](dimension-library.md) · [Français](dimension-library.fr.md)

Le mod Fabric intègre la conception de l'API DimLib et ses commandes de dimensions sous licence Apache-2.0. L'adaptation provient de [iPortalTeam/DimLib, branche 1.21.3](https://github.com/iPortalTeam/DimLib/tree/1.21.3) ; la licence et l'attribution sont conservées dans [licenses](../licenses). Aucun JAR DimLib séparé n'est nécessaire.

Les opérateurs disposent de `/dims list`, `/dims add_dimension "example:new_world" void`, `/dims clone_dimension minecraft:overworld "example:copy"`, `/dims view_dim_config example:new_world` et `/dims remove_dimension example:new_world`. Immersive Portals enregistre également les modèles skyland, bright_skyland, chaos et bright_void. Le clonage copie la définition du générateur, sans copier les blocs, les entités ou une instance mutable du générateur.

Pour les intégrations, `qouteall.dimlib.api.DimensionAPI` fournit la création, la création conditionnelle, la suppression différée, les vérifications d'existence, les modèles, les événements de cycle de vie et les correspondances dimension/type synchronisées côté client. Minecraft 26.2 emploie `Identifier` à la place de l'ancien type `ResourceLocation`. Les mutations doivent s'exécuter sur le thread du serveur, après la création des mondes natifs. Les types de dimensions doivent être déjà enregistrés.

L'ajout à chaud actualise le registre natif et les données sauvegardées `WorldGenSettings`. La suppression intervient en fin de tick, hors du parcours des mondes : les joueurs rejoignent le point d'apparition du monde principal, le suivi des portails est notifié, les chunks et entités sont sauvegardés, le monde est fermé et sa définition de génération est retirée. Les répertoires du monde sont conservés. Un datapack ou la configuration des dimensions alternatives par défaut peut réintroduire cette dimension au chargement suivant.

La bibliothèque partage avec Immersive Portals les identifiants entiers, la synchronisation des types et les événements de nettoyage. Elle actualise aussi les suggestions de dimensions du client vanilla. `SERVER_DIMENSIONS_LOAD_EVENT` intervient après la construction des mondes natifs et avant l'application des piles de dimensions ; ses abonnés peuvent alors créer des mondes supplémentaires. Ce moment diffère de l'ancien événement précédant la construction.

Les méthodes `suppressExperimentalWarning()` et `suppressExperimentalWarningForNamespace(...)` restent disponibles ; le mod déclare son propre espace de noms stable. Par défaut, il ne masque pas globalement les paramètres expérimentaux des autres mods. L'ancien écran de configuration MidnightConfig séparé n'est pas fourni.

La compilation et les validations en jeu s'exécutent exclusivement sur GitHub Actions. Le scénario des dimensions dynamiques doit établir, pour la révision exacte, la synchronisation d'un client connecté, le nettoyage et l'évacuation à la suppression, puis le rechargement du monde sauvegardé avant d'en revendiquer les résultats. Voir le [guide de validation](port-26.2.fr.md).
