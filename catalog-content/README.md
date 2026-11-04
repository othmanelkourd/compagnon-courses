# Catalogue : seed FR

> **Statut : brouillon éditorial v0, à relire par l'éditeur catalogue.**
> Les cadences (`typicalRepurchaseDays`), taux de pénétration (`householdPenetration`), formats et politiques d'unités sont des **estimations éditoriales**, à calibrer avec les données de la bêta. Ce ne sont pas des statistiques sourcées.

Ce dossier contient le catalogue de départ lu par l'importeur du serveur (`server/app`, voir `SPEC` §5 et §6). Au premier démarrage, si `catalog_release` est vide, le serveur importe ces fichiers en une transaction, vérifie leur intégrité et publie la release 1.

## Contenu

| Fichier | Contenu |
|---|---|
| `locales.json` | 5 locales : `en`, `fr`, `fr-FR`, `fr-BE`, `fr-CH` |
| `countries.json` | FR (`beta`), BE et CH (`planned`, sans données de marché) |
| `units.json` | 33 unités : masse, volume, comptage et conditionnements |
| `storage-zones.json` | 9 zones de rangement (ordre de la « tournée de la maison ») |
| `categories.json` | 19 rayons de premier niveau, avec leur question porte |
| `release.json` | Numéro de la release du catalogue, embarquée dans les apps et publiée par le serveur |
| `products.json` | 313 produits génériques (alimentaire et non alimentaire), marché FR, avec leurs tags de régime et d'allergènes |

Répartition des produits par rayon : fruits et légumes 44 · boulangerie 12 · crèmerie 20 · fromages 13 · boucherie et volaille 14 · charcuterie et traiteur 15 · poissonnerie 8 · surgelés 13 · épicerie salée 48 · petit-déjeuner 18 · épicerie sucrée 17 · apéritif 8 · eaux, jus et sodas 9 · vins, bières et alcools 10 · hygiène et beauté 19 · entretien 19 · papier et emballages 9 · bébé 9 · animaux 8.

V0 a ajouté 117 produits aux 196 du Sprint 0 : herbes fraîches et épices, surgelés, petit-déjeuner, spécialités régionales courantes (cassoulet, piment d’Espelette, chicorée, reblochon, pastis, galettes bretonnes et de sarrasin), produits utiles aux foyers halal (merguez, blanc de dinde, saucisses de volaille, agneau) ou végétariens (tofu, falafels, galettes végétales, boisson et desserts au soja, lentilles corail), et consommables du foyer. Aucun slug existant n'a changé.

## Catalogue embarqué dans les apps

Les apps embarquent ce dossier, projeté sur (FR, fr-FR) et (FR, en) comme `/v1/catalog/snapshot` ([ADR-012](../docs/adr/012-catalogue-embarque.md)), sous le numéro de release de `release.json`.

**Après toute modification de ce dossier livrée dans une version de l'app**, incrémentez `release.json`. Sinon les appareils qui ont déjà cette release gardent leur ancienne copie : la version publiée n'a pas de serveur pour la mettre à jour ([ADR-013](../docs/adr/013-version-sans-connexion.md)). Régénérez ensuite le catalogue embarqué :

```
python3 catalog-content/tools/bundle.py          # écrit shared/core/catalog-bundle/.../BundledSnapshots.kt
python3 catalog-content/tools/bundle.py --check  # échoue si le fichier n'est plus à jour (CI)
```

Le serveur publie ce dossier sous le même numéro. `CatalogBundleParityTest` (server/app) vérifie que le résultat est identique à ce qu'il publie.

## Format

Règles communes :

- Tous les fichiers sont des **tableaux JSON en UTF-8**.
- Les entités se référencent par **slug** (ASCII, kebab-case, par exemple `lait-demi-ecreme`). Les unités se référencent par leur `code`.
- Le serveur dérive des UUID stables avec `UUID.nameUUIDFromBytes("<entité>:<slug>")`, où l'entité vaut `category`, `zone`, `product` ou `unit`. **Ne jamais renommer un slug publié** : cela crée une nouvelle entité.
- Les **décimaux sont des chaînes** sous forme canonique (`"0.5"`, `"1"`, jamais `"1.0"` ni `0.5`). Les quantités (pas, choix rapides, bornes, contenus) ont **au plus 3 décimales**, car le client les stocke en millièmes (`Quantity.milli`).
- Les textes traduits sont des objets indexés par locale. `fr` et `en` sont obligatoires. Une locale régionale (`fr-FR`) ne contient que ses écarts : sinon, la chaîne de repli s'applique (`fr-FR → fr → en`).

### `locales.json`

`{ "code", "language", "region", "fallback" }`. Le fichier est ordonné pour qu'une locale apparaisse après sa locale de repli. Toute chaîne de repli se termine par `en`.

### `countries.json`

`{ "code", "defaultLocale", "measurementSystem" (metric | us_customary | uk_mixed), "currency", "launchStatus" (planned | beta | live) }`.

Un pays `planned` n'a pas encore de marché dans `products.json`. L'API répond donc 404 `COUNTRY_NOT_AVAILABLE` pour BE et CH.

### `units.json`

`{ "code", "dimension", "system", "toBase", "symbol": {fr, en}, "forms": {fr: {one, other}, en: {one, other}} }`.

- **Unités de base** (`toBase = "1"`) : `gram` (masse), `milliliter` (volume), `piece` (comptage).
- `toBase` est le facteur **exact** vers l'unité de base, sous forme de chaîne décimale :

  | Unité | Facteur |
  |---|---|
  | `kilogram` | 1000 g |
  | `pound` | 453.59237 g |
  | `ounce` | 28.349523125 g |
  | `centiliter` | 10 mL |
  | `liter` | 1000 mL |
  | `fluid-ounce` (US) | 29.5735295625 mL |
  | `fluid-ounce-imperial` | 28.4130625 mL |
  | `pint` (US) | 473.176473 mL |
  | `pint-imperial` | 568.26125 mL |
  | `gallon` (US) | 3785.411784 mL |
  | `gallon-imperial` | 4546.09 mL |
  | `dozen` | 12 pièces |

- `system` vaut `metric`, `us`, `imperial` ou `universal`. La livre et l'once avoirdupois sont classées `us`, bien que leur facteur soit identique au Royaume-Uni.
- **Conditionnements** (`dimension = "package"`, `toBase = null`) : `can`, `box`, `bottle`, `flask`, `carton`, `pack`, `packet`, `bag`, `sachet`, `jar`, `tub`, `tray`, `tube`, `bar`, `block`, `roll`, `bunch`, `net`.
  - Ils ne se convertissent **que par produit et par marché**, via `packages`.
  - `dozen` n'est pas un conditionnement : c'est une unité de comptage (12).
- Le français distingue des termes que l'anglais confond, et inversement :
  - `can` et `box` s'affichent tous deux « boîte » ;
  - `carton` s'affiche « brique » ;
  - `flask` s'affiche « flacon », mais *bottle* en anglais ;
  - `block` s'affiche « plaquette » (beurre) ;
  - `bar` s'affiche « tablette » (chocolat) ;
  - `tub` s'affiche « bac » (glace) ;
  - `jar` s'affiche « pot ».

### `storage-zones.json`

`{ "slug", "position", "names" }`. Les zones sont `frigo`, `congelateur`, `placard`, `fruits-legumes`, `salle-de-bain`, `entretien`, `animaux`, `bebe` et `cave-boissons`. Les positions suivent l'ordre de la tournée de la maison (F05).

### `categories.json`

`{ "slug", "parent", "position", "names", "gateQuestion" }`.

- Le Sprint 0 ne contient que des rayons de premier niveau (`parent: null`). Les sous-catégories viendront en V0.
- `position` donne l'ordre par défaut des rayons. L'ordre appris par magasin le remplacera.
- `gateQuestion` est la question porte du palier T3 (« Besoin de fromage ? »).

### `products.json`

```json
{ "slug": "lait-demi-ecreme", "category": "cremerie", "storageZone": "frigo", "dimension": "volume",
  "cycleClass": "short", "isStaple": true,
  "names": { "fr": {"one": "lait demi-écrémé", "other": "laits demi-écrémés"}, "en": {...} },
  "needPhrase": { "fr": "du lait demi-écrémé", "en": "semi-skimmed milk" },
  "tags": ["contains-lactose"],
  "markets": { "FR": { "availability": "common", "typicalRepurchaseDays": 6, "householdPenetration": 0.85,
      "isQuestionCandidate": true,
      "unitPolicies": [ {"unit": "bottle", "isDefault": true, "step": "1", "presets": ["1","2","6"], "min": "1", "max": "24"}, ... ],
      "packages": [ {"packageUnit": "bottle", "contentQty": "1", "contentUnit": "liter", "typical": true}, ... ] } } }
```

Les champs se lisent ainsi :

- **`dimension`** : `mass`, `volume` ou `count`. C'est la dimension de la quantité de base (`qty_base` en g, mL ou pièces). Elle n'est jamais `package`.
- **`cycleClass`** se déduit de la cadence a priori FR :
  - `short` jusqu'à 10 jours ;
  - `medium` de 11 à 40 jours ;
  - `long` au-delà de 40 jours.

  Le radar des oubliés (F04) cible les produits `long`.
- **`isStaple`** : produit de base que presque tous les foyers rachètent (lait, œufs, pain, pâtes, papier toilette…).
- **`tags`** : tags de régime et d'allergènes, voir [Tags](#tags). Le champ est toujours présent, éventuellement vide.
- **`markets.<PAYS>`** : données propres à un pays.
  - `availability` vaut `common`, `available`, `rare` ou `unavailable`. Un produit `unavailable` est exclu du snapshot du pays. `masa-harina` sert d'exemple.
  - `typicalRepurchaseDays` est la cadence a priori pour un foyer de référence d'environ 2,2 personnes. Le moteur la corrige selon la taille du foyer.
  - `householdPenetration` est la part estimée des foyers qui achètent le produit, entre 0 et 1.
  - `isQuestionCandidate` indique si le produit peut être proposé sans historique du foyer :
    - il vaut `false` pour un produit `rare` ou `unavailable` ;
    - il vaut `false` pour les produits bébé et animaux, que le profil du foyer activera en V1 ;
    - il vaut `false` pour les achats occasionnels (champagne, magret, huîtres, fromage à raclette).

### Politiques d'unités (`unitPolicies`) et formats (`packages`)

Invariants vérifiés à l'import :

1. **Exactement une** politique `isDefault: true` par marché, et une seule politique par unité.
2. `step > 0`. Les `presets` (choix rapides) sont non vides et strictement croissants. Chacun est un **multiple de `step`** et se situe dans `[min, max]`.
3. `min` et `max` sont des multiples de `step`, et `min ≤ max`. Le seed renseigne toujours ces bornes : par défaut, `min = step` et `max = 4 × le plus grand choix rapide`.
4. Chaque unité, catégorie, zone et pays référencé existe.
5. Une politique en **conditionnement** exige au moins un format `packages` pour cette unité. Sinon, la quantité ne serait pas convertible.
6. Dans un format, `packageUnit` est un conditionnement. `contentUnit` est une unité de masse, de volume ou de comptage, de **même dimension que le produit**. Il y a exactement un format `typical` par `packageUnit` : par exemple, une boîte d'œufs de 6 (typique), de 10 ou de 12.
7. Une unité de masse sur un produit compté, ou l'inverse, est tolérée pour les fruits et légumes vendus au poids mais pensés à la pièce. C'est le cas des tomates (kg ou pièce) et des bananes (pièce ou kg). La conversion entre ces deux dimensions n'est **pas possible** tant qu'un poids moyen par pièce n'existe pas (prévu en V0).

Habitudes d'achat françaises retenues :

- Œufs : par 6, 10 ou 12 (pas de 2), ou en boîte.
- Lait : par bouteille de 1 L, au litre, ou en pack de 6.
- Yaourts, crèmes dessert et compotes : par 4 (pas de 4), ou en pack de 4, 8 ou 12.
- Tomates, pommes et courgettes : au kilo (pas de 0,5), ou à la pièce.
- Conserves de légumes : boîte de 400 g (format 1/2), et 800 g en variante.
- Baguette et viennoiseries : à la pièce.
- Beurre : à la plaquette de 250 g.
- Fromage à la coupe : au gramme, par pas de 50 g.
- Eau : pack de 6 × 1,5 L, ou à la bouteille.
- Vin : bouteille de 75 cL.
- Papier toilette : pack de 12 rouleaux (6 et 24 en variantes).
- Sacs poubelle : rouleau de 20.
- Aluminium, film et papier cuisson : au rouleau.

## Règles éditoriales

- **Noms génériques, jamais de marque.** On écrit « essuie-tout » et non Sopalin, « pâte à tartiner », « mouchoirs », « bâtonnets ouatés » et non Coton-Tige. Les marques passées dans le langage courant deviendront des **alias** (`product_alias`, kind `genericized_brand`) en V0.
- **Minuscules**, sauf les noms propres : « champignon de Paris », « herbes de Provence », « saucisse de Strasbourg », « eau de Javel ». L'interface met la majuscule initiale.
- **Pluriels** (`names.<locale>`) : on renseigne les catégories de pluriel CLDR `one` et `other`.
  - En français, `one` couvre toute valeur dont la partie entière vaut 0 ou 1 (0 ; 0,5 ; 1 ; 1,5) : on écrit « 1,5 kilo », pas « 1,5 kilos ».
  - La catégorie CLDR `many` du français (grands nombres, « un million de ») n'est pas renseignée. Le client se replie alors sur `other`.
  - Un nom qui n'existe qu'au pluriel (pâtes, céréales, chips, lentilles, croquettes) a `one = other`.
  - Pour un produit compté à la pièce, la forme doit se lire après un nombre : « 6 œufs », « 2 baguettes ».
  - Quand l'unité « pièce » n'a pas de sens pour le client, on ne la propose pas. Par exemple, le jambon se compte en tranches, mais on l'achète au paquet de 4 ou de 6.
- **`needPhrase.fr`** est le groupe nominal avec son **partitif**, tel qu'il s'insère dans « Il vous faut … ? ». Il commence par :
  - « du » : du lait, du beurre ;
  - « de la » : de la farine, de la moutarde ;
  - « de l’ », avec élision devant une voyelle ou un h muet : de l’huile d’olive, de l’eau plate, de l’ail ;
  - « des », pour les produits comptables : des œufs, des bananes, des yaourts nature.

  `needPhrase.en` est le nom sans article (« semi-skimmed milk », « eggs »).
- **Typographie française** :
  - apostrophe typographique `’` (U+2019), jamais `'` ;
  - espace insécable (U+00A0) avant `?`, `!`, `;` et `:`. Toutes les questions portes françaises se terminent par une espace insécable suivie de « ? ».
- **Anglais** : `en` suit l'orthographe et le vocabulaire britanniques (aubergine, courgette, washing-up liquid, nappies, crisps). Une future locale `en-US` ne portera que les écarts (zucchini, eggplant, chips…).
- **Pas de données sensibles** : les tags décrivent des **produits**, pas des personnes. Les exclusions d'un foyer (sans porc, sans alcool, végétarien…) peuvent révéler une conviction religieuse ou une donnée de santé (art. 9 du RGPD) : elles restent sur l'appareil par défaut (F14, `member_diet_profile`). Le seed ne contient pas de label `halal` : c'est une certification de marque, pas un attribut d'un produit générique.

## Tags

Liste fermée, identique à `ProductTags.ALL` du contrat. Chaque produit liste ses tags **dans cet ordre** :

| Tag | Sens |
|---|---|
| `contains-pork` | Porc ou dérivé du porc : viande, charcuterie, saindoux, **gélatine de porc** (bonbons gélifiés). Implique `contains-meat`. |
| `contains-alcohol` | Boisson alcoolisée, y compris la bière « sans alcool » (jusqu'à 1,2 % vol. en France). |
| `contains-gluten` | Blé, seigle, orge ou avoine non certifiée sans gluten (pain, pâtes, semoule, boulgour, bière, levure chimique à l'amidon de blé, sauce soja). |
| `contains-lactose` | Lait ou dérivé du lait : lait, beurre, crème, yaourts, **tous les fromages** (même affinés), viennoiseries au beurre. |
| `contains-meat` | Viande ou volaille, ou sous-produit d'abattage (gélatine). Les poissons et fruits de mer n'en font pas partie. |
| `contains-fish` | Poissons, crustacés et mollusques (crevettes, moules, huîtres, surimi). |
| `contains-nuts` | Fruits à coque et arachide (amandes, noix, noix de cajou, pistaches, noisettes de la pâte à tartiner, cacahuètes, pignons ou cajou du pesto). |
| `contains-eggs` | Œufs ou ingrédient à base d'œuf (mayonnaise, pâtes fraîches, viennoiseries dorées à l'œuf, surimi au blanc d'œuf). |
| `vegan` | Aucun ingrédient d'origine animale, ni miel, ni gélatine, ni colorant carmin. |
| `baby` | Rayon bébé (`bebe`), et lui seul. |
| `pet` | Rayon animaux (`animaux`), et lui seul. |

Règles d'attribution :

- Un tag `contains-*` est posé quand **la version française usuelle** du produit générique contient l'ingrédient : la quiche lorraine contient des lardons, le pesto du parmesan et des pignons, les lasagnes surgelées une bolognaise au bœuf. Une variante qui s'en écarte (pesto sans fromage, lasagnes végétariennes) sera un autre produit.
- `vegan` n'est posé que **si l'on en est sûr** pour toutes les versions courantes : fruits et légumes frais, féculents secs, conserves de légumes nature, huiles, épices, café et thé, eaux, jus et sodas, fruits à coque nature, tofu, falafels, houmous. On ne le pose pas sur le chocolat noir (matière grasse lactique fréquente), les vinaigres et les vins (collage possible aux protéines animales), les sirops (carmin dans la grenadine), la margarine (lactosérum possible) ni le miel.
- Quand les recettes varient trop pour l'affirmer, le produit n'a **aucun tag plutôt qu'un tag faux** : poêlée de légumes, soupe de légumes, bouillon cube, purée en flocons, galettes de sarrasin, galettes végétales, chips tortilla, chocolat en poudre.
- Les produits non alimentaires (hygiène et beauté, entretien, papier et emballages) n'ont **aucun tag**.
- Les produits du rayon bébé portent `baby`, plus leurs allergènes s'ils se mangent (`contains-lactose` pour le lait infantile).
- Les produits du rayon animaux portent **uniquement** `pet` : le régime du foyer ne s'applique pas à ses animaux, et la pâtée d'un chat ne doit pas disparaître d'une liste végétarienne.
- `vegan` est incompatible avec `contains-pork`, `contains-meat`, `contains-fish`, `contains-lactose` et `contains-eggs`.
- Les allergènes réglementaires absents de la liste (soja, sésame, céleri, moutarde, sulfites, lupin) ne sont pas encore tagués.

Répartition (313 produits) : `contains-pork` 13 · `contains-alcohol` 10 · `contains-gluten` 46 · `contains-lactose` 44 · `contains-meat` 29 · `contains-fish` 11 · `contains-nuts` 7 · `contains-eggs` 14 · `vegan` 129 · `baby` 9 · `pet` 8. Aucun tag : les 47 produits non alimentaires et 14 produits alimentaires.

## Limites connues et suites (V0)

- 313 produits, sans sous-catégories : il faut encore en ajouter 80 à 150.
- À ajouter ensuite :
  - alias et synonymes ;
  - genre grammatical ;
  - surcharge de la question par produit ;
  - saisonnalité ;
  - niveau de certitude des tags (`always`, `typically`, `may`) et allergènes manquants (soja, sésame, céleri, moutarde, sulfites) ;
  - poids moyen à la pièce ;
  - marchés BE et CH.
- Relecture éditoriale complète (partitifs, pluriels, traductions), puis calibrage des cadences et des taux de pénétration avec la bêta.
- Le script de validation (Sprint 0, étendu en V0 aux tags et à la fourchette de 300 à 320 produits) est resté hors du dépôt. Il faudra l'intégrer à la CI, ou s'appuyer sur la validation de l'importeur serveur, qui applique les mêmes invariants.
