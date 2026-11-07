# Provenance des sources et périmètre du dépôt

[English](provenance.md) · [Français](provenance.fr.md)

Le mod original est [iPortalTeam/ImmersivePortalsMod](https://github.com/iPortalTeam/ImmersivePortalsMod), de qouteall et des contributeurs, sous [Apache-2.0](../LICENSE). Son historique de commits et ses mentions de droits sont conservés.

L’implémentation 26.2 est importée de [N4TH4NOT/immersive-portals-minestom](https://github.com/N4TH4NOT/immersive-portals-minestom/tree/67bf29333b82b0d6c594c65d84256c881f0182b4), révision `67bf29333b82b0d6c594c65d84256c881f0182b4`. Ce dépôt est un fork de l’amont. L’import conserve ses commits de migration et leurs auteurs, sans présenter ces adaptations comme un nouveau travail réalisé ici.

La branche avant migration conserve l’[instantané amont 1.21.3](https://github.com/iPortalTeam/ImmersivePortalsMod/tree/6831a3a72ba159d19951d80929a5809868bb08f9), révision `6831a3a72ba159d19951d80929a5809868bb08f9`, ainsi que l’initialisation du dépôt cible.

Ce dépôt cible le mod Fabric autonome. `portal-protocol` reste une dépendance interne pour le pont client hérité ; le SDK serveur Minestom et son application d’exemple appartiennent au fork source. La gravité est fournie par le [dépôt gravity-changer](https://github.com/N4TH4NOT/gravity-changer), sous Apache-2.0, dont la révision est épinglée par le sous-module Git et [.gitmodules](../.gitmodules).

L’adaptation interne de gestion des dimensions réutilise l’API publique et les modèles Apache-2.0 d’[iPortalTeam/DimLib](https://github.com/iPortalTeam/DimLib/tree/1.21.3). Sa [licence](../licenses/DimLib-LICENSE.txt) et sa [notice d’attribution](../licenses/DimLib-NOTICE.txt) accompagnent les sources.

Le dépôt cible existait indépendamment avant l’import. Importer l’historique Git conserve la filiation et les attributions, mais ne modifie pas la relation de fork gérée par GitHub. Cet import ne nécessite ni suppression du dépôt ni remplacement de son historique.

Les validations historiques provenant d’un autre dépôt sont identifiées comme telles. La validation actuelle correspond au passage GitHub Actions associé au commit exact du dépôt cible ; voir le [guide de validation](port-26.2.fr.md).
