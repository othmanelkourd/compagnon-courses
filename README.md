# Compagnon de courses

Monorepo de « Compagnon de courses », une application mobile qui prépare la liste de courses en posant des questions :
« Avez-vous besoin de X ? », puis la quantité dans l'unité adaptée au produit.

- **Apps** : logique partagée en Kotlin Multiplatform jusqu'aux ViewModels, UI native en Jetpack Compose (Android) et SwiftUI (iOS, via SKIE).
- **Serveur** : Ktor + PostgreSQL, contrat d'API partagé avec les apps.
- **Backoffice** : React-admin (catalogue).

> **Statut : Sprint 0.** Le dépôt contient un *walking skeleton* :
> `GET /v1/catalog/version` (Ktor + PostgreSQL) → client Ktor partagé → ViewModel KMP → un écran Compose et un écran SwiftUI.
> S'y ajoutent le moteur de questions v0 et ses tests, un backoffice en lecture seule et un seed du catalogue FR.
> Bilan, écarts et checklist : [docs/SPRINT-0.md](docs/SPRINT-0.md).

## Sommaire

- [Structure](#structure)
- [Prérequis](#prérequis)
- [Lancer le serveur](#lancer-le-serveur)
- [Lancer l'app Android](#lancer-lapp-android)
- [Lancer l'app iOS](#lancer-lapp-ios)
- [Lancer le backoffice](#lancer-le-backoffice)
- [Tests et vérifications](#tests-et-vérifications)
- [Contrat OpenAPI](#contrat-openapi)
- [Services locaux (Docker, optionnel)](#services-locaux-docker-optionnel)
- [CI et dépendances](#ci-et-dépendances)
- [Conventions](#conventions)
- [Documentation](#documentation)

## Structure

```
courses/
├─ settings.gradle.kts · build.gradle.kts · gradle.properties · gradle/libs.versions.toml · gradlew
├─ build-logic/                 # build inclus : plugins de convention compagnon.kmp.library, compagnon.android.application, compagnon.jvm.server…
├─ contract/
│  ├─ api-contract/             # :contract:api-contract   KMP : DTO @Serializable, ApiPaths, ErrorCode, ContractJson
│  └─ openapi/openapi.json      # exporté par le serveur, versionné
├─ shared/
│  ├─ core/model/               # :shared:core:model       unités, Quantity (sans Double), entités, mappers DTO → domaine
│  ├─ core/network/             # :shared:core:network     client Ktor, CatalogApi, NetworkError
│  ├─ engine/                   # :shared:engine           moteur de questions v0, Kotlin pur
│  ├─ feature/catalog/          # :shared:feature:catalog  CatalogStatusViewModel
│  └─ umbrella/                 # :shared:umbrella         graphe Koin, framework iOS « Shared » (SKIE appliqué ici seulement)
├─ androidApp/                  # :androidApp              app Compose (com.compagnon.courses)
├─ iosApp/                      # iosApp.xcodeproj         app SwiftUI (com.compagnon.courses)
├─ server/app/                  # :server:app              serveur Ktor, Flyway, Exposed, import du seed
├─ backoffice/                  # Vite + React + TypeScript + react-admin (npm)
├─ catalog-content/             # seed éditorial du catalogue (JSON)
├─ infra/
│  ├─ docker/compose.yml        # PostgreSQL 18, Mailpit, stockage S3 (RustFS) en local
│  └─ tofu/                     # OpenTofu (en attente de l'ADR-007)
├─ .github/workflows/ci.yml · renovate.json · .editorconfig
└─ docs/                        # plan, ADR, API, modèle de données, moteur de questions
```

Le package de base `com.compagnon.courses` (et l'identifiant d'app du même nom) est provisoire, en attendant le choix du nom et du domaine.

## Prérequis

| Outil | Version | Remarque |
|---|---|---|
| JDK | 17 | Corretto ou Temurin. Toutes les compilations utilisent `jvmToolchain(17)` |
| Gradle | wrapper 9.7.1 | Toujours passer par `./gradlew` |
| Android SDK | plateforme 37 (`compileSdk`), `targetSdk` 36, `minSdk` 26 | Chemin dans `local.properties` (`sdk.dir=…`, non versionné) ou `ANDROID_HOME` |
| Xcode | 26.1 ou plus récent | macOS sur Apple Silicon ; app iOS 17+, framework construit pour `iosArm64` et `iosSimulatorArm64` |
| Node.js + npm | Node 22.12 ou plus récent | Backoffice uniquement |
| Docker | optionnel | Seulement pour `infra/docker/compose.yml` |

Exemple de `local.properties` à la racine :

```properties
sdk.dir=/Users/<vous>/Library/Android/sdk
```

La première compilation iOS télécharge la chaîne Kotlin/Native (plus de 1 Go, dans `~/.konan`) : comptez plusieurs minutes.

## Lancer le serveur

```bash
./gradlew :server:app:run
```

- Sans `DATABASE_URL`, le serveur démarre en **mode dev** : un PostgreSQL 18 embarqué (zonky), dont les données sont dans `server/app/build/dev-pg`. Aucun Docker n'est nécessaire.
- Au premier démarrage, si aucune release n'existe, le contenu de `catalog-content/` est validé puis importé comme release 1 du catalogue.
- Le serveur écoute sur `http://localhost:8080`.

```bash
curl "http://localhost:8080/healthz"
curl "http://localhost:8080/v1/catalog/version?country=FR"
curl "http://localhost:8080/v1/catalog/snapshot?country=FR&locale=fr-FR"
curl -i "http://localhost:8080/admin/v1/products?_start=0&_end=10"
```

| URL | Contenu |
|---|---|
| `/healthz` | `{"status":"ok"}` |
| `/v1/config/app` | configuration de l'app (pays, version minimale) |
| `/v1/catalog/version?country=FR` | version du catalogue publié |
| `/v1/catalog/snapshot?country=FR&locale=fr-FR` | catalogue projeté sur un pays et une locale (ETag, 304) |
| `/v1/catalog/changes?since=1&country=FR&locale=fr-FR` | delta (vide au Sprint 0, sinon 410 `SNAPSHOT_REQUIRED`) |
| `/admin/v1/categories`, `/admin/v1/products` | API du backoffice, conventions json-server (**dev uniquement, sans authentification**) |
| `/swagger`, `/openapi.json` | Swagger UI et document OpenAPI |

Variables d'environnement (voir `server/app/src/main/resources/application.conf`) :

| Variable | Défaut | Rôle |
|---|---|---|
| `PORT` | `8080` | Port HTTP (écoute sur `0.0.0.0`) |
| `DATABASE_URL` | absente → mode dev | URL JDBC, par exemple `jdbc:postgresql://localhost:5432/courses` |
| `DATABASE_USER`, `DATABASE_PASSWORD` | — | Identifiants de la base |
| `CATALOG_SEED_DIR` | `catalog-content` | Dossier du seed, relatif à la racine du dépôt |
| `ADMIN_ENABLED` | `true` en mode dev seulement | Active l'API `/admin/v1` (aucune authentification au Sprint 0) |
| `DEV_PG_DIR`, `DEV_PG_PORT` | `server/app/build/dev-pg`, port libre | PostgreSQL embarqué du mode dev |

Pour repartir d'une base vide en mode dev : arrêter le serveur, puis supprimer `server/app/build/dev-pg`.

## Lancer l'app Android

1. Démarrer le serveur (voir ci-dessus).
2. Démarrer un émulateur Android (API 26 ou plus), puis :

```bash
./gradlew :androidApp:installDebug
```

En debug, l'app appelle `http://10.0.2.2:8080`, l'alias de la machine hôte vu depuis l'émulateur.
Le trafic HTTP en clair n'est autorisé qu'en debug, et seulement vers `10.0.2.2` et `localhost`.

Si l'émulateur n'a pas de réseau (l'app affiche « Impossible de joindre le serveur »), ou pour un appareil physique, passer par `adb reverse` :

```bash
adb reverse tcp:8080 tcp:8080
./gradlew :androidApp:installDebug -Pcompagnon.apiBaseUrl=http://localhost:8080
```

Pour seulement construire l'APK : `./gradlew :androidApp:assembleDebug` (sortie dans `androidApp/build/outputs/apk/debug/`).

## Lancer l'app iOS

1. Démarrer le serveur (voir ci-dessus). Le simulateur partage le réseau de la machine : l'app appelle `http://localhost:8080`.
2. Ouvrir le projet, choisir le schéma `iosApp` et un simulateur (par exemple iPhone 17 Pro), puis lancer (⌘R) :

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
xcrun simctl launch booted com.compagnon.courses
```

## Lancer le backoffice

```bash
cd backoffice
npm install
npm run dev        # http://localhost:5173
```

Le serveur doit tourner : le backoffice lit `VITE_API_URL` (défaut `http://localhost:8080/admin/v1`) et `VITE_PUBLIC_API_URL` (défaut `http://localhost:8080`).
Pour les surcharger, copier `backoffice/.env.example` en `backoffice/.env.local`.
Le port 5173 est imposé : c'est l'origine autorisée par le CORS du serveur.
Détails : [backoffice/README.md](backoffice/README.md).

## Tests et vérifications

Tests JVM (contrat, modèle, moteur, réseau, ViewModel, serveur avec PostgreSQL embarqué) :

```bash
./gradlew :contract:api-contract:jvmTest :shared:core:model:jvmTest :shared:engine:jvmTest \
  :shared:core:network:jvmTest :shared:feature:catalog:jvmTest :server:app:test
```

| Commande | Ce qui est vérifié |
|---|---|
| `./gradlew :shared:engine:jvmTest` | Moteur v0 : tests unitaires et tests de propriétés Kotest (budget, tags exclus, déterminisme, arrondis, conversions) |
| `./gradlew :server:app:test` | Import du seed, routes, ETag/304, erreurs `problem+json`, `X-Total-Count`, import du vrai `catalog-content/` |
| `./gradlew :shared:feature:catalog:iosSimulatorArm64Test` | Tests du ViewModel sur le simulateur iOS (macOS uniquement) |
| `./gradlew :androidApp:assembleDebug` | Compilation de l'app Android |
| `cd backoffice && npm run typecheck && npm run build` | Typage et build du backoffice (`npm test` pour les tests Vitest) |

Les rapports de tests sont dans `<module>/build/reports/tests/`.

## Contrat OpenAPI

Le document `contract/openapi/openapi.json` est généré depuis le serveur et versionné. Après toute modification d'une route ou d'un DTO :

```bash
./gradlew :server:app:exportOpenApi           # régénère contract/openapi/openapi.json
cd backoffice && npm run gen:api              # régénère les types TypeScript (src/api/schema.d.ts)
```

Les deux fichiers générés sont versionnés : la CI échoue si l'un d'eux ne correspond plus au code.
Les types du backoffice (`backoffice/src/api/types.ts`) sont des alias de `schema.d.ts` : un changement de DTO casse donc `npm run typecheck`.

## Services locaux (Docker, optionnel)

`infra/docker/compose.yml` fournit les services de l'environnement `local` (identifiants **de dev uniquement**, ports ouverts sur `127.0.0.1`) :

| Service | Accès |
|---|---|
| PostgreSQL 18 | `localhost:5432`, base, utilisateur et mot de passe `courses` |
| Mailpit | SMTP `localhost:1025`, interface http://localhost:8025 |
| RustFS (stockage compatible S3) | API http://localhost:9000, console http://localhost:9001 |

```bash
docker compose -f infra/docker/compose.yml up -d

DATABASE_URL=jdbc:postgresql://localhost:5432/courses DATABASE_USER=courses DATABASE_PASSWORD=courses \
ADMIN_ENABLED=true ./gradlew :server:app:run
```

`ADMIN_ENABLED=true` n'est nécessaire que pour le backoffice : hors mode dev, l'API d'administration est désactivée par défaut.
RustFS remplace MinIO, dont l'édition communautaire n'est plus distribuée qu'en sources ; la cible de production reste Cloudflare R2 (ADR-011).

## CI et dépendances

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) tourne sur chaque PR et sur `main` :

| Job | Contenu |
|---|---|
| `jvm` (ubuntu, JDK 17) | Tests JVM de tous les modules + `:androidApp:assembleDebug` |
| `ios` (macos-26) | `:shared:feature:catalog:iosSimulatorArm64Test` + build Xcode ; seulement si `shared/`, `iosApp/`, le contrat ou la configuration Gradle changent |
| `backoffice` (Node 22) | `npm ci`, `npm run typecheck`, tests Vitest, `npm run build` |
| `openapi` | Régénère `contract/openapi/openapi.json` et les types TypeScript du backoffice (`backoffice/src/api/schema.d.ts`), et échoue en cas de différence |

[`renovate.json`](renovate.json) : mises à jour hebdomadaires, groupées par famille. Kotlin, SKIE et KSP sont groupés et **jamais automatiques** :
SKIE est lié à une version exacte de Kotlin, la montée se valide depuis le tableau de bord Renovate puis dans une PR dédiée.

## Conventions

- **Versions** : uniquement dans `gradle/libs.versions.toml` (Kotlin 2.4.20 épinglé pour SKIE 0.10.15) et dans `backoffice/package.json` (versions exactes, `package-lock.json` versionné).
- **Build** : les modules appliquent les plugins de convention de `build-logic` ; le namespace Android des bibliothèques KMP est dérivé du chemin du module.
- **Style** : [`.editorconfig`](.editorconfig) ; Kotlin selon les conventions officielles (`kotlin.code.style=official`).
- **Code partagé** : pas de `Double` pour les quantités ; erreurs réseau typées (`NetworkError`) ; les ViewModels exposent un `StateFlow` d'état et ne lèvent jamais d'exception.
- **Secrets** : aucun secret dans le dépôt. Les identifiants présents (`courses`/`courses`, etc.) sont des valeurs de dev, signalées comme telles.

## Documentation

| Document | Contenu |
|---|---|
| [docs/README.md](docs/README.md) | Point d'entrée de la documentation produit et technique |
| [docs/PLAN-SYNTHESE.md](docs/PLAN-SYNTHESE.md) | Plan validé : vision, architecture, stack, roadmap, Sprint 0 |
| [docs/SPRINT-0.md](docs/SPRINT-0.md) | Bilan du Sprint 0 : livrables, écarts, checklist, suite |
| [docs/adr/](docs/adr/README.md) | Décisions d'architecture (ADR 001 à 011) |
| [docs/api/endpoints.md](docs/api/endpoints.md) | Conventions de l'API et endpoints |
| [docs/data-model.md](docs/data-model.md) | Modèle de données PostgreSQL et entités partagées |
| [docs/question-engine.md](docs/question-engine.md) | Algorithme du moteur de questions |
| [catalog-content/README.md](catalog-content/README.md) | Format du seed et règles éditoriales |
