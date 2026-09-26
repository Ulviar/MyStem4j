/**
 * Executes an installed MyStem command-line tool and exposes raw results.
 *
 * <p>The exported {@link io.github.ulviar.mystem4j} package contains the client entry point, typed CLI
 * options, process-pool settings, and runtime exceptions. No morphology parsing or executable download
 * occurs in this module.
 */
module io.github.ulviar.mystem4j {
    requires io.github.ulviar.procwright;

    exports io.github.ulviar.mystem4j;
}
