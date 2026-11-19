# Ocimio

Ocimio est une application mobile qui prépare la liste de courses en posant des questions :
« Avez-vous besoin de X ? », puis la quantité dans l'unité adaptée au produit.

- **Apps** : logique partagée en Kotlin Multiplatform jusqu'aux ViewModels, UI native en Jetpack Compose (Android) et SwiftUI (iOS, via SKIE).
- **Sans serveur** : tout fonctionne sur le téléphone. Le catalogue est livré avec l'app, et les listes, l'historique et le foyer restent dans la base locale, comprise dans la sauvegarde du téléphone.

## Sommaire

- [Structure](#structure)
- [Prérequis](#prérequis)
- [Lancer l'app Android](#lancer-lapp-android)
- [Lancer l'app iOS](#lancer-lapp-ios)
- [Le catalogue](#le-catalogue)
- [Tests et vérifications](#tests-et-vérifications)
- [CI et dépendances](#ci-et-dépendances)
- [Conventions](#conventions)
- [Publication](#publication)
- [Documentation](#documentation)

## Structure

```
courses/
├─ settings.gradle.kts · build.gradle.kts · gradle.properties · gradle/libs.versions.toml · gradlew
├─ build-logic/                 # build inclus : plugins de convention compagnon.kmp.library, compagnon.kmp.mobile, compagnon.android.application
├─ shared/
│  ├─ core/catalog-format/      # :shared:core:catalog-format  format JSON du catalogue : DTO @Serializable, CatalogJson, ProductTags
│  ├─ core/model/               # :shared:core:model       unités, Quantity (sans Double), entités, mappers DTO → domaine, clés d'images
│  ├─ core/catalog-bundle/      # :shared:core:catalog-bundle  catalogue embarqué (release de catalog-content/release.json, fr-FR et en), généré depuis catalog-content/
│  ├─ core/database/            # :shared:core:database    SQLDelight : schéma, migrations (databases/n.db)
│  ├─ core/data/                # :shared:core:data        dépôts : catalogue, foyer (« Mon foyer »), listes, séances
│  ├─ engine/                   # :shared:engine           moteur de questions, Kotlin pur
│  ├─ feature/catalog/          # :shared:feature:catalog  CatalogStatusViewModel (catalogue de l'appareil)
│  ├─ feature/question-flow/    # :shared:feature:question-flow  QuestionSessionViewModel
│  ├─ feature/lists/            # :shared:feature:lists    ShoppingListViewModel
│  ├─ feature/onboarding/       # :shared:feature:onboarding  AppGateViewModel, OnboardingViewModel (« Mon foyer »)
│  ├─ feature/about/            # :shared:feature:about    « À propos » : licences open source (générées), liens publics
│  └─ umbrella/                 # :shared:umbrella         graphe Koin, framework iOS « Shared » (SKIE appliqué ici seulement)
├─ androidApp/                  # :androidApp              app Compose (com.ocimio.courses)
├─ iosApp/                      # iosApp.xcodeproj         app SwiftUI (com.ocimio.courses)
├─ catalog-content/             # données sources du catalogue (JSON)
├─ config/detekt/detekt.yml     # règles detekt + ktlint (baselines par module : detekt-baseline.xml)
└─ .github/workflows/ci.yml · renovate.json · .editorconfig
```

L'app s'appelle **Ocimio**, d'après *Ocimum*, le nom botanique du basilic (domaine prévu : `ocimio` en .com, .fr et .app). Ses identifiants sur les stores sont `com.ocimio.courses` (`applicationId` Android et bundle id iOS) ; ils ne pourront plus changer après la première publication. Le package Kotlin `com.compagnon.courses` reste un nom interne, invisible des utilisateurs.

## Prérequis

| Outil | Version | Remarque |
|---|---|---|
| JDK | 17 | Corretto ou Temurin. Toutes les compilations utilisent `jvmToolchain(17)` |
| Gradle | wrapper 9.7.1 | Toujours passer par `./gradlew` |
| Android SDK | plateforme 37 (`compileSdk`), `targetSdk` 36, `minSdk` 26 | Chemin dans `local.properties` (`sdk.dir=…`, non versionné) ou `ANDROID_HOME` |
| Xcode | 26.1 ou plus récent | macOS sur Apple Silicon ; app iOS 17+, framework construit pour `iosArm64` et `iosSimulatorArm64` |
| Python | 3.9 ou plus récent | Générateurs du catalogue embarqué et des licences |

Exemple de `local.properties` à la racine :

```properties
sdk.dir=/Users/<vous>/Library/Android/sdk
```

La première compilation iOS télécharge la chaîne Kotlin/Native (plus de 1 Go, dans `~/.konan`) : comptez plusieurs minutes.

## Lancer l'app Android

Démarrer un émulateur Android (API 26 ou plus), ou brancher un téléphone, puis :

```bash
./gradlew :androidApp:installDebug
```

Pour seulement construire l'APK : `./gradlew :androidApp:assembleDebug` (sortie dans `androidApp/build/outputs/apk/debug/`).

## Lancer l'app iOS

Ouvrir le projet, choisir le schéma `iosApp` et un simulateur (par exemple iPhone 17 Pro), puis lancer (⌘R) :

```bash
open iosApp/iosApp.xcodeproj
```

Une phase de build appelle `./gradlew :shared:umbrella:embedAndSignAppleFrameworkForXcode` : elle compile le framework Kotlin `Shared` (avec SKIE) pour l'architecture du simulateur.

En ligne de commande :

```bash
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  -derivedDataPath build/ios-derived build

xcrun simctl boot "iPhone 17 Pro" 2>/dev/null || true
open -a Simulator
xcrun simctl install booted "$(find build/ios-derived/Build/Products/Debug-iphonesimulator -maxdepth 1 -name '*.app' | head -n 1)"
xcrun simctl launch booted com.ocimio.courses
```

## Le catalogue

Les données sources du catalogue sont dans `catalog-content/` ([format et règles éditoriales](catalog-content/README.md)). L'app embarque leur projection dans `shared/core/catalog-bundle/` (`BundledSnapshots.kt`, fichier généré). À chaque changement du catalogue livré, `catalog-content/release.json` doit être incrémenté, sinon les téléphones gardent leur copie de la même release.

## Tests et vérifications

| Commande | Ce qui est vérifié |
|---|---|
| `./gradlew jvmTest` | Tests JVM de tous les modules partagés : format du catalogue, modèle, base, dépôts, moteur, ViewModels |
| `./gradlew :shared:engine:koverVerify` | Couverture du moteur ≥ 90 % des lignes (`koverHtmlReport` montre ce qui manque) |
| `./gradlew :shared:umbrella:iosSimulatorArm64Test` | Graphe Koin et fournisseurs de ViewModels sur le simulateur iOS (macOS uniquement) |
| `./gradlew :androidApp:testDebugUnitTest` | Tests unitaires de l'app Android |
| `./gradlew detekt` | Lint Kotlin (detekt + ktlint) ; `-Pdetekt.autoCorrect` corrige la mise en forme, `detektBaseline` accepte les alertes existantes |
| `./gradlew :androidApp:lintDebug` | Android Lint, tout nouvel avertissement est bloquant (`lint-baseline.xml`) |
| SwiftLint | À chaque build Xcode (plugin `SwiftLintPlugins`, `iosApp/.swiftlint.yml`) |

Les rapports de tests sont dans `<module>/build/reports/tests/`.

## CI et dépendances

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) tourne sur chaque PR et sur `main` :

| Job | Contenu |
|---|---|
| `jvm` (ubuntu, JDK 17) | Tests JVM, couverture du moteur (`koverVerify`, ≥ 90 %), `detekt`, tests unitaires Android, `:androidApp:assembleDebug` et `lintDebug` |
| `ios` (macos-26) | `:shared:feature:catalog:iosSimulatorArm64Test` + build Xcode (avec SwiftLint) ; seulement si `shared/`, `iosApp/` ou la configuration Gradle changent |

[`renovate.json`](renovate.json) : mises à jour hebdomadaires, groupées par famille. Kotlin, SKIE et KSP sont groupés et **jamais automatiques** :
SKIE est lié à une version exacte de Kotlin, la montée se valide depuis le tableau de bord Renovate puis dans une PR dédiée.

## Conventions

- **Versions** : uniquement dans `gradle/libs.versions.toml` (Kotlin 2.4.20 épinglé pour SKIE 0.10.15).
- **Build** : les modules appliquent les plugins de convention de `build-logic` ; le namespace Android des bibliothèques KMP est dérivé du chemin du module.
- **Style** : [`.editorconfig`](.editorconfig) ; Kotlin selon les conventions officielles (`kotlin.code.style=official`).
- **Code partagé** : pas de `Double` pour les quantités ; les ViewModels exposent un `StateFlow` d'état et ne lèvent jamais d'exception.
- **Hors ligne** : l'app ne déclare aucune permission réseau et ne contient aucun client HTTP.
- **Secrets** : aucun secret dans le dépôt (la clé d'upload Android est lue dans `~/.gradle/gradle.properties`).

## Publication

La release Android est signée avec la clé d'upload Google Play, lue dans `~/.gradle/gradle.properties` (`ocimio.upload.storeFile`, `ocimio.upload.storePassword`, `ocimio.upload.keyAlias`, `ocimio.upload.keyPassword`) ; sans elle, elle est signée avec la clé de debug. Numéros de version : `versionCode` et `versionName` dans `androidApp/build.gradle.kts`, `MARKETING_VERSION` et `CURRENT_PROJECT_VERSION` dans le projet Xcode.

## Documentation

| Document | Contenu |
|---|---|
| [catalog-content/README.md](catalog-content/README.md) | Format du catalogue et règles éditoriales |
| [androidApp/README.md](androidApp/README.md), [iosApp/README.md](iosApp/README.md) | Les deux apps |
| [shared/engine/README.md](shared/engine/README.md), [shared/umbrella/README.md](shared/umbrella/README.md) | Moteur de questions, graphe Koin et framework iOS |
