# Choose Modules

Declare the highest-level module that matches your task. Transitive dependencies
bring supporting modules when they are part of that module's public API.

| Task | Declare | Also needed at runtime |
| --- | --- | --- |
| Run MyStem and read raw JSON, XML, or text | `mystem4j-runtime` | a MyStem executable path or `PATH` entry |
| Parse existing MyStem JSON without running MyStem | `mystem4j-model` | no MyStem executable |
| Run MyStem and parse the JSON result | `mystem4j-runtime` + `mystem4j-model` | a MyStem executable |
| Build search tokens from parsed model objects | `mystem4j-tokenization` | parsed `MystemDocument` values |
| Use MyStem inside Lucene | `mystem4j-lucene` | a JSON-configured MyStem client and executable |
| Configure runtime clients from Kotlin | `mystem4j-kotlin` | a MyStem executable if the client runs MyStem |
| Download and prepare MyStem in Gradle | `io.github.ulviar.mystem4j` Gradle plugin | explicit MyStem license acceptance |

## Gradle Dependencies

```kotlin
dependencies {
    implementation("io.github.ulviar.mystem4j:mystem4j-runtime:0.1.0")
}
```

For Lucene projects, usually declare only the Lucene module:

```kotlin
dependencies {
    implementation("io.github.ulviar.mystem4j:mystem4j-lucene:0.1.0")
}
```

`mystem4j-lucene` exposes `mystem4j-runtime` and `mystem4j-tokenization`
types in its public API, so Gradle brings them transitively.

For Kotlin runtime helpers:

```kotlin
dependencies {
    implementation("io.github.ulviar.mystem4j:mystem4j-kotlin:0.1.0")
}
```

`mystem4j-kotlin` brings `mystem4j-runtime` transitively.

## Maven Dependencies

```xml
<dependency>
  <groupId>io.github.ulviar.mystem4j</groupId>
  <artifactId>mystem4j-runtime</artifactId>
  <version>0.1.0</version>
</dependency>
```

Use the same group and version with `mystem4j-model`, `mystem4j-tokenization`,
`mystem4j-lucene`, or `mystem4j-kotlin`.

## Gradle Plugin

The Gradle plugin is not required to call the Java API. Use it only when the build
should download, verify, extract, probe, or pass a MyStem executable path to tests
or distribution tasks.
