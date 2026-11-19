# :shared:core:model

Domain model shared by every client module, plus the mappers from the catalog
format (`:shared:core:catalog-format`, `com.compagnon.courses.core.model.mapping`).

- **Quantities are exact.** `Quantity(milli, unit)` stores thousandths of the unit (1.5 kg =
  `Quantity(1500, kilogram)`); `Quantity.parse("1.5", unit)` and `toDecimalString()` use the wire format of
  the API (decimal strings). No Double ever holds a quantity.
- **Conversions are exact.** Unit factors are `Rational`s (1 lb = 45359237/100000 g). `UnitCatalog.convert`
  rounds once, half-even, to the milli unit, with a 128-bit intermediate product; an overflow throws
  `ArithmeticException` (`convertOrNull` returns null). Package units (bottle, can…) convert only through
  a product's `PackageFormat`s: `units.convert(q, liter, product.packages)`.
- **Invariants live in the constructors** (`UnitPolicy`: presets are multiples of the step within the bounds;
  `Product`: exactly one default policy…) and throw `IllegalArgumentException`.
- **Mapping a snapshot never fails as a whole**: `CatalogSnapshotDto.toDomain()` skips invalid units and
  products and lists them in `CatalogMappingResult.issues`. Single-DTO mappers throw
  `CatalogMappingException` with the offending field.
