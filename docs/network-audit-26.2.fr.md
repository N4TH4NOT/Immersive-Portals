# Compatibilité réseau

[English](network-audit-26.2.md) · [Français](network-audit-26.2.fr.md)

Le mod Fabric conserve les paquets natifs de mondes redirigés et le pont client en version 2 importés du [fork source](https://github.com/N4TH4NOT/immersive-portals-minestom/tree/67bf29333b82b0d6c594c65d84256c881f0182b4). Les messages et bornes du pont figurent dans [`BridgeMessage`](../portal-protocol/src/main/java/io/github/n4th4not/immersiveportals/protocol/BridgeMessage.java) et [`BridgeCodec`](../portal-protocol/src/main/java/io/github/n4th4not/immersiveportals/protocol/BridgeCodec.java) ; les types de paquets natifs acceptés sont définis dans [`BridgeClient`](../src/main/java/qouteall/imm_ptl/core/network/bridge/BridgeClient.java).

Les paquets de vues distantes remplacent la livraison ordinaire correspondante, sans dupliquer le flux du monde courant. Les en-têtes de fragments identifient des contextes de réassemblage bornés. La répétition d’événements comme les sons, les dégâts ou les acquittements de prédiction peut être intentionnelle ; l’égalité des octets ne justifie pas à elle seule leur suppression.

L’[audit réseau du fork source](https://github.com/N4TH4NOT/immersive-portals-minestom/blob/67bf29333b82b0d6c594c65d84256c881f0182b4/docs/network-audit-26.2.fr.md) décrit l’optimisation de son émetteur Minestom, ses tests et les comptages de paquets enregistrés. Ces modules serveur et leurs tests sont hors de cette distribution Fabric. Leurs résultats historiques ne doivent pas être présentés comme des tests exécutés dans ce dépôt.

Les tests actuels du protocole Fabric et les preuves de traversée/interaction en jeu relèvent de [GitHub Actions](port-26.2.fr.md). Les pairs du pont doivent cibler le même protocole Minecraft ; modifier l’encadrement des messages ou les 28 octets historiques conservés dans `OpenView` demande une évolution négociée du protocole.
