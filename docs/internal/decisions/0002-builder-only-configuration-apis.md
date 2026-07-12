# ADR 0002: Builder-Only Configuration APIs

Status: accepted
Scope: runtime, tokenization, and Lucene public configuration

## Context

Configuration types gain controls as MyStem behavior, process management, and
Lucene integration evolve. Public records expose a positional constructor and a
component method for every field. Adding one option therefore changes multiple
public signatures and encourages call sites whose booleans and limits are hard to
read.

The 0.1.0 artifacts have not been published, so the initial record API can still
be replaced without user migration cost.

## Decision

Public configuration values are final immutable classes created through named
builders. They expose named read accessors but no public positional constructor
and no destructuring contract. Presets and `toBuilder()` may be provided when they
make policy changes clearer.

Records remain appropriate for result and data-transfer values whose components
are the stable public meaning of the type. Gradle managed extensions continue to
use Gradle `Property` APIs rather than these builders.

## Consequences

Adding a configuration field can preserve source and binary compatibility.
Callers use readable named setters, and Kotlin can wrap builders with marked DSL
receivers. Configuration instances do not promise structural equality; tests
compare their named contract properties.

The API has slightly more implementation code than records, but avoids generated
constructors and component methods that would expand the compatibility surface.

## Verification

- `apiSurfaceCheck` rejects public constructors on registered builder-only
  configuration classes.
- Java and Kotlin API baselines expose builders and named accessors.
- Module tests cover defaults, validation, presets, `toBuilder()`, and DSL
  delegation.
