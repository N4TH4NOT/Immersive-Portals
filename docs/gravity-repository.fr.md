# Dépôt de gravité

[English](gravity-repository.md) · [Français](gravity-repository.fr.md)

[`gravity-changer`](https://github.com/N4TH4NOT/gravity-changer) contient l’implémentation de gravité sous Apache-2.0. `dependencies/gravity` épingle sa révision ; le composite Gradle fournit les bibliothèques d’adaptation sans publication Maven distante.

GitHub Actions récupère récursivement les sous-modules et compile la dépendance épinglée. Publier les commits de gravité avant d’avancer le sous-module. Toutes les compilations, les tests et les scénarios en jeu de ce dépôt s’exécutent uniquement sur GitHub Actions ; consulter le [guide de validation](port-26.2.fr.md).

Les façades et enveloppes de messages des portails restent ici. L’implémentation de gravité et ses scénarios d’intégration appartiennent au dépôt de gravité. Voir son [guide d’intégration](https://github.com/N4TH4NOT/gravity-changer/blob/main/docs/gravity-26.2.fr.md) et son [guide d’installation Fabric](https://github.com/N4TH4NOT/gravity-changer/blob/main/README.fr.md#installation-et-utilisation-fabric). L’adaptateur/API embarqué ne remplace pas le mod d’exécution supplémentaire nécessaire à la gravité directionnelle du joueur.
