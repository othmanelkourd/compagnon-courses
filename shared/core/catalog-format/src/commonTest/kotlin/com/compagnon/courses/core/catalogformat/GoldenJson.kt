package com.compagnon.courses.core.catalogformat

/**
 * Golden JSON document of the catalog format, as `catalog-content/tools/bundle.py` writes it. A failing golden
 * test means a breaking change of the bundled catalog files.
 *
 * Kept as Kotlin constants rather than test resources so that they are readable on every target,
 * Kotlin/Native included.
 */
internal object GoldenJson {

    const val CATALOG_SNAPSHOT: String = """
{
  "country": "FR",
  "locale": "fr-FR",
  "version": 3,
  "categories": [
    {
      "id": "4f9d8b1e-2c3a-3b5d-9e7f-0a1b2c3d4e5f",
      "slug": "cremerie",
      "position": 20,
      "name": "Crèmerie",
      "gateQuestion": "Besoin de quelque chose en crèmerie ?"
    },
    {
      "id": "7a6b5c4d-3e2f-3a1b-8c9d-0e1f2a3b4c5d",
      "slug": "yaourts",
      "parentId": "4f9d8b1e-2c3a-3b5d-9e7f-0a1b2c3d4e5f",
      "position": 10,
      "name": "Yaourts"
    }
  ],
  "storageZones": [
    { "id": "1b2c3d4e-5f6a-3b7c-8d9e-0f1a2b3c4d5e", "slug": "frigo", "position": 10, "name": "Frigo" }
  ],
  "units": [
    {
      "code": "liter",
      "dimension": "volume",
      "system": "metric",
      "toBase": "1000",
      "symbol": "L",
      "forms": { "one": "litre", "other": "litres" }
    },
    {
      "code": "bottle",
      "dimension": "package",
      "system": "any",
      "symbol": "bout.",
      "forms": { "one": "bouteille", "other": "bouteilles" }
    },
    {
      "code": "dozen",
      "dimension": "count",
      "system": "any",
      "toBase": "12",
      "symbol": "douz.",
      "forms": { "one": "douzaine", "other": "douzaines" }
    }
  ],
  "products": [
    {
      "id": "9c8b7a6d-5e4f-3a2b-9c1d-0e2f3a4b5c6d",
      "slug": "lait-demi-ecreme",
      "categoryId": "4f9d8b1e-2c3a-3b5d-9e7f-0a1b2c3d4e5f",
      "storageZoneId": "1b2c3d4e-5f6a-3b7c-8d9e-0f1a2b3c4d5e",
      "dimension": "volume",
      "name": "lait demi-écrémé",
      "forms": { "one": "lait demi-écrémé", "other": "laits demi-écrémés" },
      "needPhrase": "du lait demi-écrémé",
      "cycleClass": "short",
      "isStaple": true,
      "typicalRepurchaseDays": 6,
      "householdPenetration": 0.85,
      "isQuestionCandidate": true,
      "unitPolicies": [
        { "unit": "liter", "isDefault": false, "step": "1", "presets": ["1", "2", "6"], "min": "1", "max": "24" },
        { "unit": "bottle", "isDefault": true, "step": "1", "presets": ["1", "2", "6"] }
      ],
      "packages": [
        { "packageUnit": "bottle", "contentQty": "1", "contentUnit": "liter", "typical": true }
      ],
      "tags": ["contains-lactose"]
    },
    {
      "id": "2d3e4f5a-6b7c-3d8e-9f0a-1b2c3d4e5f6a",
      "slug": "oeufs",
      "categoryId": "4f9d8b1e-2c3a-3b5d-9e7f-0a1b2c3d4e5f",
      "storageZoneId": "1b2c3d4e-5f6a-3b7c-8d9e-0f1a2b3c4d5e",
      "dimension": "count",
      "name": "œufs",
      "forms": { "one": "œuf", "other": "œufs" },
      "cycleClass": "medium",
      "isStaple": false,
      "isQuestionCandidate": false,
      "unitPolicies": [
        { "unit": "dozen", "isDefault": true, "step": "0.5", "presets": ["0.5", "1"] }
      ],
      "packages": [],
      "tags": []
    }
  ]
}
"""
}
