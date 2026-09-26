/**
 * Gradle preparation of native MyStem distributions, independent of runtime execution.
 *
 * <p>Start with {@link io.github.ulviar.mystem4j.gradle.Mystem4jExtension} for download
 * opt-ins, platform selection, checksums, and test integration. Use its task-backed
 * file providers to feed application packaging tasks. The task types expose individual
 * download, extraction, smoke-check, and runtime-properties stages for build customization.
 *
 * <p>Download and Yandex license acceptance default to disabled. The plugin manages
 * files and build tasks; applications still choose where to deploy the executable and
 * how to provide its path to the runtime client.
 *
 * @see io.github.ulviar.mystem4j.gradle.Mystem4jPlugin
 */
package io.github.ulviar.mystem4j.gradle;
