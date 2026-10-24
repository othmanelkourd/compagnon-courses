# androidApp

Application Android (Compose, Material 3) du *walking skeleton* : un écran qui affiche la version du catalogue FR servie par `GET /v1/catalog/version`, via `CatalogStatusViewModel` (`:shared:feature:catalog`).

## Lancer contre le serveur local

```sh
./gradlew :server:app:run          # serveur sur le port 8080
./gradlew :androidApp:installDebug # émulateur démarré au préalable
```

- **Émulateur** : le build debug appelle `http://10.0.2.2:8080` (l'hôte vu depuis l'émulateur).
- **Appareil physique** : rediriger le port, puis pointer l'app sur `localhost` :
  ```sh
  adb reverse tcp:8080 tcp:8080
  ./gradlew :androidApp:installDebug -Pcompagnon.apiBaseUrl=http://localhost:8080
  ```

Le HTTP en clair n'est autorisé qu'en debug, et seulement pour `10.0.2.2` et `localhost` (`src/debug/res/xml/network_security_config.xml`). Le build release vise une URL provisoire (`https://api.compagnon.invalid`) en attendant l'hébergement (ADR-007).

## Tests

```sh
./gradlew :androidApp:testDebugUnitTest          # formatage date / nombres (JVM)
./gradlew :androidApp:connectedDebugAndroidTest  # états de l'écran (Compose UI test, émulateur requis)
```
