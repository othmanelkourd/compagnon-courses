# androidApp

Application Android (Jetpack Compose, Material 3) de « Ocimio » (identifiant `com.ocimio.courses`). Toute la logique vient du code partagé (`:shared:umbrella`) : l'app ne fait qu'afficher les états des ViewModels KMP et leur renvoyer des événements.

## Écrans

Navigation 3 (`navigation/CompagnonApp.kt`), pile sauvegardée, ViewModels rattachés à leur entrée (`lifecycle-viewmodel-navigation3`) :

| Écran | Fichiers | ViewModel partagé |
|---|---|---|
| **Accueil** : « Faire le point », « Ma liste » et son nombre d'articles, catalogue local et dernière synchronisation | `home/`, `catalog/` | `CatalogStatusViewModel`, `ShoppingListViewModel` |
| **Questionnaire** : questions méta, « Probablement nécessaire », cartes produit (glisser à droite = Oui, à gauche = Non, boutons équivalents), portes de rayon, récapitulatif, fin | `questions/` | `QuestionSessionViewModel` (créé ici seulement) |
| **Liste** : sections dans l'ordre du magasin, cocher, glisser pour supprimer (« Annuler »), recherche, « Autre chose ? » | `list/` | `ShoppingListViewModel` |

- Quitter le questionnaire laisse la séance ouverte : la visite suivante la reprend. Le retour système annule la dernière réponse tant qu'il y en a une.
- Accessibilité : Oui/Non et Supprimer sont aussi des actions TalkBack ; cibles d'au moins 48 dp.
- Textes : français par défaut (`values/`), anglais dans `values-en/`.

## Design

L'app applique la charte commune Android / iOS (`design/CHARTE.md`, maquette dans `design/maquette/`) :

- `ui/theme/` : seule source du thème. `CompagnonColors` (jetons de la charte) et le `lightColorScheme` Material qui en découle, `CompagnonType` (échelle typographique en `sp`), `CompagnonShapes` et `CompagnonSpacing`. Mode clair seulement, même si le système est en sombre ; pas de couleurs dynamiques.
- `ui/components/` : boutons (principal, secondaire, texte, icône), segments de progression, choix de quantité, puce, cases à cocher, les trois pas-à-pas, lignes (suggestion, produit, récapitulatif), blocs de liste, indice, option de réponse, champ de saisie, snackbar. Icônes au trait (`AppIcons`), reprises des tracés SVG de la maquette ; aucune autre famille d'icônes.
- `ui/illustration/` : images 3D (Fluent Emoji, licence MIT dans `src/main/assets/licenses/`) par clé partagée (`Illustrations`) : `Illustration` (décorative, avec ou sans pastille, entrée avec léger rebond), `HeroIllustration`, `Halo`, `LeafPattern` (motif feuilles) et `LeafConfetti` (pluie de feuilles de la fête). `IllustrationRes.kt` est généré par `design/illustrations/build.py`.
- `ui/theme/Motion.kt` : jetons de mouvement (`CompagnonMotion` : 150, 250, 400, 1200 ms, courbes standard / entrée / sortie, ressort doux) et `LocalReduceMotion` (échelle d'animation du système à 0 : de simples fondus, pas de fête). Animations réutilisables dans `ui/components/Animations.kt` (appui, entrée en cascade, pop, chiffres qui roulent, coche qui se dessine).
- Polices embarquées dans `res/font/` : Figtree (400, 500, 600, 700) et Bricolage Grotesque (500, 700), instances statiques sous licence SIL Open Font License 1.1. Les licences sont livrées dans `src/main/assets/licenses/`.

Captures : `docs/images/design-android-*.png`.

## Lancer contre le serveur local

```sh
./gradlew :server:app:run          # serveur sur le port 8080
./gradlew :androidApp:installDebug # émulateur démarré au préalable
```

- **Émulateur** : le build debug appelle `http://10.0.2.2:8080` (l'hôte vu depuis l'émulateur).
- **Appareil physique, autre port** : rediriger le port, puis pointer l'app sur `localhost` :
  ```sh
  adb reverse tcp:8080 tcp:8080
  ./gradlew :androidApp:installDebug -Pcompagnon.apiBaseUrl=http://localhost:8080
  ```

Le HTTP en clair n'est autorisé qu'en debug, et seulement pour `10.0.2.2` et `localhost` (`src/debug/res/xml/network_security_config.xml`). Le build release n'a pas de serveur (`API_BASE_URL` vaut `null`) ni de permission `INTERNET` : elle est déclarée dans `src/debug` et retirée de la release par `src/release/AndroidManifest.xml` (OkHttp l’ajoute). L’app publiée ne se connecte jamais (ADR-013).

## Tests

```sh
./gradlew :androidApp:testDebugUnitTest          # navigation, textes, progression, formatage (JVM)
./gradlew :androidApp:connectedDebugAndroidTest  # écrans avec de faux états (Compose UI test, émulateur requis)
```
