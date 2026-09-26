/**
 * Kotlin DSL and synchronous extension functions over the MyStem4j Java runtime.
 *
 * <p>Re-exports the runtime and Kotlin standard library for consumers of this facade. The DSL creates
 * ordinary {@link io.github.ulviar.mystem4j.MystemClient} instances; lifecycle, process modes, limits,
 * and failures retain their Java contracts.
 */
module io.github.ulviar.mystem4j.kotlin {
    requires transitive io.github.ulviar.mystem4j;
    requires transitive kotlin.stdlib;

    exports io.github.ulviar.mystem4j.kotlin;
}
