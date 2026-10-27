# :shared:core:catalog-bundle

The catalog shipped in the apps (ADR-012): the server's release 1, projected onto (FR, fr-FR) and (FR, en) like
`/v1/catalog/snapshot`, as JSON strings (`BundledCatalog.json(country, locale)`). On first launch, and after an app
update that ships a newer release, `CatalogRepository.installBundled` writes it to the local database; the startup check
(`CatalogUpdater`) then fetches what the server published since, as a delta.

- `BundledSnapshots.kt` is generated: `python3 catalog-content/tools/bundle.py` (from `catalog-content/`), or
  `--from-server <url>` (the snapshots a server publishes). `--check` fails when the file is out of date with
  `catalog-content/` (CI).
- `CatalogBundleParityTest` (server/app) checks that the bundle equals the server's projection of release 1: same ids,
  names and policies, so that the deltas apply on top of it.
- No dependency, so that the server's tests can read it; the shared tests use it as their catalog (`SeedCatalog`).
