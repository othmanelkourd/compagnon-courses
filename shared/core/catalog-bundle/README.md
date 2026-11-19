# :shared:core:catalog-bundle

The catalog shipped in the apps: the release of `catalog-content/` (`catalog-content/release.json`),
projected onto (FR, fr-FR) and (FR, en), as JSON strings of `CatalogSnapshotDto` (`BundledCatalog.json(country, locale)`).
On first launch, after an app update that ships a newer release, and when the phone language changes,
`CatalogRepository.installBundled` writes it to the local database.

- `BundledSnapshots.kt` is generated from `catalog-content/`: do not edit it by hand.
- No dependency; the shared tests use it as their catalog (`SeedCatalog`).
