# androidApp

Application Android (Jetpack Compose, Material 3) de « Compagnon de courses ». Toute la logique vient du code partagé (`:shared:umbrella`) : l'app ne fait qu'afficher les états des ViewModels KMP et leur renvoyer des événements.

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

Le HTTP en clair n'est autorisé qu'en debug, et seulement pour `10.0.2.2` et `localhost` (`src/debug/res/xml/network_security_config.xml`). Le build release vise une URL provisoire (`https://api.compagnon.invalid`) en attendant l'hébergement (ADR-007).

## Tests

```sh
./gradlew :androidApp:testDebugUnitTest          # navigation, textes, progression, formatage (JVM)
./gradlew :androidApp:connectedDebugAndroidTest  # écrans avec de faux états (Compose UI test, émulateur requis)
```
