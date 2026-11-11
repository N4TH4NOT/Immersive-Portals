# Revue de la référence amont 1.21

[English](upstream-1.21-review.md) · [Français](upstream-1.21-review.fr.md)

Le fork source importé consigne une revue du 20 octobre 2026 de la référence `0dbdc1d493c3906943147668092a41fc67a49527` → amont `2b1d3546aa60c7d866adfcb7236fba136a8433cb` : 69 commits, dont trois merges. Cette revue porte sur les comportements amont 1.21.1 hérités par ses adaptations 26.2 ; il s’agit d’une analyse historique des sources, pas d’un nouveau résultat en jeu pour ce dépôt.

Le dépôt cible conserve aussi l’[instantané amont 1.21.3](https://github.com/iPortalTeam/ImmersivePortalsMod/tree/6831a3a72ba159d19951d80929a5809868bb08f9), révision `6831a3a72ba159d19951d80929a5809868bb08f9`, comme référence avant migration. Les différences avec l’implémentation 26.2 demandent une revue des comportements, plutôt qu’une reprise indifférenciée des anciens adaptateurs du moteur.

| Comportement à conserver | Commits représentatifs |
| --- | --- |
| Modificateur SCALE natif, modificateurs externes et position des yeux | `874588b8`, `c148926d`, `cabd8b69` |
| Temps d’interpolation et copies de projection | `89c35c2b`, `f1e09897` |
| Caméra à la troisième personne et API GUI de viewport | `ec60bedc`, `5127ad56`, `6bbc5bfa` |
| Identité/validité des conteneurs séparées de la portée | `6bb58c52` |
| Sérialisation sans mutation des maillages publiés | `a9bd638e` |
| Redirection chunks/attachements et codecs liés aux registres | `b7d6ac94`, `c7ecf525` |
| Cadres, recherche différée du portail inverse et conversion des objets sauvegardés | `3a5de6b9`, `253d77dc`, `632789a8`, `672fca43` |
| Nettoyage explicite des ressources et retrait du groupement de portails | `7fa7ab17`, `0e34101a`, `4facf06f`, `a3291b1f` |

La série Sodium/Iris contient des reverts et réapplications : porter son comportement final, pas chaque essai. Ses anciens mixins OpenGL et versions de dépendances ne fournissent pas un backend 26.2. Pehkui n’est pas requis pour l’échelle native ; les formes en volume existaient déjà avant cet intervalle.

La revue a aussi identifié des erreurs de bornes exclusives de chunks et des wrappers GUI ignorant les coordonnées du viewport. Ces cas demandent une adaptation explicite. Les corrections et contrôles actuels figurent dans les [constats de migration](migration-audit-26.2.fr.md).

L’intervalle de commits reste accessible pour examiner l’historique et les sources. Les compilations, les tests et les scénarios en jeu actuels s’exécutent exclusivement sur GitHub Actions, comme décrit dans le [guide de validation](port-26.2.fr.md).
