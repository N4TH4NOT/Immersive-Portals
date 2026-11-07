# Dimensions alternatives

[English](alternate-dimensions.md) · [Français](alternate-dimensions.fr.md)

Le portage Fabric 26.2 fournit `immersive_portals:skyland`, `bright_skyland`, `chaos`, `void` et `bright_void` dans le registre natif de dimensions de Minecraft. DimLib et les datapacks supplémentaires ne sont pas nécessaires. Les variantes lumineuses utilisent le type de dimension lumineux existant ; la météo des dimensions alternatives suit toujours celle du monde principal.

`enableAlternateDimensions` vaut `true` par défaut. Au chargement d'un monde, le mod ajoute les définitions absentes avant l'assemblage des couches de registres du serveur. Les définitions sauvegardées et les datapacks restent prioritaires, ce qui préserve les générateurs de terrain existants. Régler l'option sur `false` avant de démarrer un monde empêche l'ajout de ces valeurs par défaut. Cela ne supprime ni les dimensions déjà sauvegardées, ni leurs chunks, ni les définitions fournies par un datapack.

Les skylands natifs utilisent la graine du monde et sauvegardent ce choix avec leur générateur. Les datapacks peuvent toujours fournir un `seed` fixe ; `use_world_seed: true` sélectionne plutôt la graine du serveur lors de l'initialisation du générateur. Le terrain chaos conserve le générateur régional volontairement non déterministe de l'amont. Les deux dimensions vides utilisent un générateur plat vide.

Les cinq dimensions sont disponibles dans les empilements dès la création du monde. Leurs définitions doivent être préparées avant le chargement ; ce portage n'expose pas l'API d'ajout/suppression de dimensions à chaud de DimLib. Redémarrer après l'ajout d'un datapack de dimension ou une modification de configuration.

Les vérifications unitaires GitHub couvrent la désactivation, la priorité des sauvegardes/datapacks, les variantes vides manquantes et la conservation des métadonnées de registre. Le scénario Fabric génère des chunks dans les cinq dimensions et recharge les codecs de leurs générateurs, avec le marqueur `PORTAL_SMOKE_ALTERNATE_DIMENSIONS_OK`. Les scénarios figurent dans le [guide de validation](port-26.2.fr.md) ; la compilation seule ne prouve pas le comportement en jeu.
