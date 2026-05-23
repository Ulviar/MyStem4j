/**
 * Runtime API for launching Yandex MyStem from JVM applications.
 *
 * <p>This package exposes the CLI-facing layer only: executable resolution, typed MyStem options, one-shot requests,
 * reusable JSON-line sessions, pooled JSON-line sessions, file requests, probe checks, raw output results, and runtime
 * exceptions. It intentionally does not parse MyStem JSON/XML/text into morphological model objects and does not depend
 * on Lucene.
 */
package io.github.ulviar.mystem4j;
