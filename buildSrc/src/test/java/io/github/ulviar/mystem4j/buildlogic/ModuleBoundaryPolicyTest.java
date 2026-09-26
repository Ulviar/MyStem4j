package io.github.ulviar.mystem4j.buildlogic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import javax.tools.ToolProvider;
import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModuleBoundaryPolicyTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void parserImplementationCanUseJacksonWithoutExposingIt() throws IOException {
        Path classes = compile(Map.of(
                "com/fasterxml/jackson/core/JsonFactory.java",
                "package com.fasterxml.jackson.core; public class JsonFactory {}",
                "io/github/ulviar/mystem4j/model/Parser.java",
                """
                package io.github.ulviar.mystem4j.model;
                public class Parser {
                    public String parse() { return new com.fasterxml.jackson.core.JsonFactory().toString(); }
                }
                """));
        removeFixtureDependency(classes, "com");
        assertDoesNotThrow(() -> inspect("model", classes, false));
        assertDoesNotThrow(() -> inspect("model", classes, true));
    }

    @Test
    void jacksonInPublicGenericSignatureIsRejected() throws IOException {
        Path classes = compile(Map.of(
                "com/fasterxml/jackson/core/JsonFactory.java",
                "package com.fasterxml.jackson.core; public class JsonFactory {}",
                "io/github/ulviar/mystem4j/model/Parser.java",
                """
                package io.github.ulviar.mystem4j.model;
                public class Parser {
                    public java.util.List<com.fasterxml.jackson.core.JsonFactory> factories() { return null; }
                }
                """));
        removeFixtureDependency(classes, "com");
        assertDoesNotThrow(() -> inspect("model", classes, false));
        assertRejected("Jackson API", "com.fasterxml.jackson.core.JsonFactory", () -> inspect("model", classes, true));
    }

    @Test
    void fullyQualifiedBackwardDependencyIsRejected() throws IOException {
        Path classes = compile(Map.of(
                "io/github/ulviar/mystem4j/Mystem.java",
                "package io.github.ulviar.mystem4j; public class Mystem {}",
                "io/github/ulviar/mystem4j/model/Parser.java",
                """
                package io.github.ulviar.mystem4j.model;
                public class Parser {
                    public String parse() { return new io.github.ulviar.mystem4j.Mystem().toString(); }
                }
                """));
        Files.delete(classes.resolve("io/github/ulviar/mystem4j/Mystem.class"));
        assertRejected("reverse edge", " -> io.github.ulviar.mystem4j.Mystem",
                () -> inspect("model", classes, false));
    }

    @Test
    void processesInPureModelAreRejected() throws IOException {
        Path classes = compile(Map.of("io/github/ulviar/mystem4j/model/Parser.java", """
                package io.github.ulviar.mystem4j.model;
                public class Parser {
                    public Object parse() { return new ProcessBuilder("mystem"); }
                }
                """));
        assertRejected("process", "java.lang.ProcessBuilder", () -> inspect("model", classes, false));
    }

    @Test
    void runtimeNetworkingIsRejected() throws IOException {
        Path classes = compile(Map.of("io/github/ulviar/mystem4j/Downloader.java", """
                package io.github.ulviar.mystem4j;
                public class Downloader {
                    public Object connection(java.net.URL url) throws java.io.IOException {
                        return url.openConnection();
                    }
                }
                """));
        assertRejected("network", "java.net.", () -> inspect("runtime", classes, false));
    }

    @Test
    void valueOnlyUrisAndInternationalHostNamesRemainAllowedInTokenization() throws IOException {
        Path classes = compile(Map.of("io/github/ulviar/mystem4j/tokenization/Urls.java", """
                package io.github.ulviar.mystem4j.tokenization;
                public class Urls {
                    public String host(String text) { return java.net.URI.create(text).getHost(); }
                    public String asciiHost(String host) {
                        return java.net.IDN.toASCII(host, java.net.IDN.USE_STD3_ASCII_RULES);
                    }
                }
                """));
        assertDoesNotThrow(() -> inspect("tokenization", classes, false));
    }

    @Test
    void leakedImplementationAndUnusedRuntimeDependenciesAreRejected() {
        assertDoesNotThrow(() -> ModuleBoundaryPolicy.checkClasspath(
                "runtime", Set.of("io.github.ulviar.mystem4j"), true));
        assertDoesNotThrow(() -> ModuleBoundaryPolicy.checkClasspath(
                "runtime", Set.of("io.github.ulviar.mystem4j", "io.github.ulviar.procwright"), false));
        assertRejected("compile leak", "io.github.ulviar.procwright", () -> ModuleBoundaryPolicy.checkClasspath(
                "runtime", Set.of("io.github.ulviar.mystem4j", "io.github.ulviar.procwright"), true));
        assertRejected("unused runtime edge", "io.github.ulviar.mystem4j.model",
                () -> ModuleBoundaryPolicy.checkClasspath("runtime",
                        Set.of("io.github.ulviar.mystem4j", "io.github.ulviar.procwright", "io.github.ulviar.mystem4j.model"),
                        false));
    }

    @Test
    void transitiveImplementationDependencyAndExtraExportsAreRejected() {
        assertRejected("transitive parser", "requires transitive", () -> ModuleBoundaryPolicy.checkDescriptor(
                "model", ModuleDescriptor.newModule("io.github.ulviar.mystem4j.model")
                        .requires(Set.of(ModuleDescriptor.Requires.Modifier.TRANSITIVE), "com.fasterxml.jackson.core")
                        .exports("io.github.ulviar.mystem4j.model").build()));
        assertRejected("export", "exports", () -> ModuleBoundaryPolicy.checkDescriptor(
                "model", ModuleDescriptor.newModule("io.github.ulviar.mystem4j.model")
                        .requires("com.fasterxml.jackson.core")
                        .exports("io.github.ulviar.mystem4j.model")
                        .exports("io.github.ulviar.mystem4j.model.internal").build()));
    }

    @Test
    void httpModulesCanNetworkButCannotStartProcessesOrExposeTransportImplementation() throws IOException {
        Path classes = compile(Map.of("io/github/ulviar/mystem4j/http/Client.java", """
                package io.github.ulviar.mystem4j.http;
                public class Client {
                    public void request() throws Exception {
                        java.net.http.HttpClient.newHttpClient().close();
                        java.nio.file.Files.createTempFile("response", ".tmp");
                    }
                }
                """));
        assertDoesNotThrow(() -> inspect("http-client", classes, false));
        assertDoesNotThrow(() -> inspect("http-client", classes, true));
        String process = "   io.github.ulviar.mystem4j.http.Client -> java.lang.ProcessBuilder java.base";
        assertRejected("remote process creation", "java.lang.ProcessBuilder",
                () -> ModuleBoundaryPolicy.checkDependencies("http-client", process, false));
        String serverProcess = "   io.github.ulviar.mystem4j.server.Server -> java.lang.ProcessBuilder java.base";
        assertRejected("server process creation", "java.lang.ProcessBuilder",
                () -> ModuleBoundaryPolicy.checkDependencies("http-server", serverProcess, false));
        String leakedServer = "   io.github.ulviar.mystem4j.server.Server -> com.sun.net.httpserver.HttpServer jdk.httpserver";
        assertDoesNotThrow(() -> ModuleBoundaryPolicy.checkDependencies("http-server", leakedServer, false));
        assertRejected("HTTP server API", "com.sun.net.httpserver.HttpServer",
                () -> ModuleBoundaryPolicy.checkDependencies("http-server", leakedServer, true));
    }

    private Path compile(Map<String, String> sources) throws IOException {
        Path classes = temporaryDirectory.resolve("classes");
        Files.createDirectories(classes);
        ArrayList<String> arguments = new ArrayList<>(java.util.List.of("-d", classes.toString()));
        for (var source : sources.entrySet()) {
            Path file = temporaryDirectory.resolve("src").resolve(source.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue());
            arguments.add(file.toString());
        }
        ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
        int status = ToolProvider.getSystemJavaCompiler().run(
                null, diagnostics, diagnostics, arguments.toArray(String[]::new));
        if (status != 0) {
            throw new AssertionError(diagnostics.toString(StandardCharsets.UTF_8));
        }
        return classes;
    }

    private static void removeFixtureDependency(Path classes, String packageRoot) throws IOException {
        try (var paths = Files.walk(classes.resolve(packageRoot))) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private static void inspect(String component, Path classes, boolean apiOnly) {
        ModuleBoundaryPolicy.checkDependencies(
                component, ModuleBoundaryCheckTask.dependencies(classes, apiOnly), apiOnly);
    }

    private static void assertRejected(String label, String diagnostic, org.junit.jupiter.api.function.Executable action) {
        GradleException error = assertThrows(GradleException.class, action, label);
        assertTrue(error.getMessage().contains(diagnostic), error::getMessage);
    }
}
