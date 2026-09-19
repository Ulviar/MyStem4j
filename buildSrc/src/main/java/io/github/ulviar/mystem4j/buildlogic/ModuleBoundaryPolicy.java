package io.github.ulviar.mystem4j.buildlogic;

import java.lang.module.ModuleDescriptor;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.gradle.api.GradleException;

/** The allowed production graph; test dependencies deliberately do not participate. */
final class ModuleBoundaryPolicy {
    private static final String ROOT = "io.github.ulviar.mystem4j";
    private static final Pattern EDGE = Pattern.compile("\\s+(\\S+)\\s+->\\s+(\\S+)\\s+.*");
    private static final Map<String, Rule> RULES = Map.of(
            "runtime", new Rule(ROOT, ROOT, Set.of(), Set.of("icli")),
            "model", new Rule(ROOT + ".model", ROOT + ".model", Set.of(), Set.of("jackson")),
            "tokenization", new Rule(ROOT + ".tokenization", ROOT + ".tokenization", Set.of("model"), Set.of()),
            "lucene", new Rule(ROOT + ".lucene", ROOT + ".lucene",
                    Set.of("runtime", "tokenization", "lucene-core"), Set.of("model")),
            "kotlin", new Rule(ROOT + ".kotlin", ROOT + ".kotlin",
                    Set.of("runtime", "kotlin-stdlib"), Set.of()),
            "gradle-plugin", new Rule(ROOT + ".gradle.plugin", ROOT + ".gradle",
                    Set.of("gradle-api"), Set.of()));
    private static final Map<String, String> EXTERNAL_MODULES = Map.of(
            "icli", "com.github.ulviar.icli",
            "jackson", "com.fasterxml.jackson.core",
            "lucene-core", "org.apache.lucene.core",
            "kotlin-stdlib", "kotlin.stdlib");

    private ModuleBoundaryPolicy() {}

    static Rule rule(String component) {
        Rule rule = RULES.get(component);
        if (rule == null) {
            throw new GradleException("No module boundary policy for " + component);
        }
        return rule;
    }

    static void checkDependencies(String component, String jdepsOutput, boolean apiOnly) {
        Rule rule = rule(component);
        Set<String> allowed = new HashSet<>(rule.api());
        allowed.add(component);
        if (!apiOnly) {
            allowed.addAll(rule.implementation());
        }
        int edges = 0;
        for (String line : jdepsOutput.lines().toList()) {
            var match = EDGE.matcher(line);
            if (!match.matches()) {
                continue;
            }
            edges++;
            String source = match.group(1);
            String target = match.group(2);
            if (!component.equals(owner(source))) {
                throw new GradleException(component + " JAR contains a class owned by another layer: " + source);
            }
            String targetOwner = owner(target);
            if (forbiddenCapability(component, target)
                    || (!target.startsWith("java.") && !allowed.contains(targetOwner))) {
                throw new GradleException(component + (apiOnly ? " public API" : " implementation")
                        + " has a forbidden dependency: " + source + " -> " + target);
            }
            if (apiOnly && RULES.containsKey(targetOwner)
                    && !packageName(target).equals(rule(targetOwner).packageName())) {
                throw new GradleException(component + " public API exposes an unexported package: " + target);
            }
        }
        if (edges == 0) {
            throw new GradleException("No class dependencies inspected for " + component);
        }
    }

    static void checkDescriptor(String component, ModuleDescriptor descriptor) {
        Rule rule = rule(component);
        if (component.equals("gradle-plugin")) {
            if (!descriptor.isAutomatic() || !descriptor.name().equals(rule.moduleName())) {
                throw new GradleException("The Gradle plugin must keep its automatic module name " + rule.moduleName());
            }
            return;
        }
        if (!descriptor.name().equals(rule.moduleName()) || descriptor.isAutomatic() || descriptor.isOpen()) {
            throw new GradleException(component + " must have an explicit, closed module " + rule.moduleName());
        }
        Set<String> exports = new HashSet<>();
        for (ModuleDescriptor.Exports export : descriptor.exports()) {
            if (export.isQualified()) {
                throw new GradleException(component + " must not use qualified exports");
            }
            exports.add(export.source());
        }
        requireEqual(component + " exports", Set.of(rule.packageName()), exports);
        requireEqual(component + " opens", Set.of(), descriptor.opens());
        Set<String> requires = new HashSet<>();
        Set<String> transitive = new HashSet<>();
        for (ModuleDescriptor.Requires require : descriptor.requires()) {
            requires.add(require.name());
            if (require.modifiers().contains(ModuleDescriptor.Requires.Modifier.TRANSITIVE)) {
                transitive.add(require.name());
            }
            if (require.modifiers().contains(ModuleDescriptor.Requires.Modifier.STATIC)) {
                throw new GradleException(component + " has an unexpected optional module: " + require.name());
            }
        }
        Set<String> expectedRequires = new HashSet<>(Set.of("java.base"));
        rule.api().forEach(owner -> expectedRequires.add(moduleName(owner)));
        rule.implementation().forEach(owner -> expectedRequires.add(moduleName(owner)));
        requireEqual(component + " requires", expectedRequires, requires);
        requireEqual(component + " requires transitive",
                rule.api().stream().map(ModuleBoundaryPolicy::moduleName).collect(java.util.stream.Collectors.toSet()),
                transitive);
    }

    static void checkClasspath(String component, Set<String> actualModules, boolean apiOnly) {
        Set<String> expected = new HashSet<>();
        collectModules(component, apiOnly, expected);
        // Kotlin's own metadata exposes these compile-time annotations.
        if (expected.contains("kotlin.stdlib")) {
            expected.add("annotations");
        }
        requireEqual(component + (apiOnly ? " consumer compile classpath" : " consumer runtime classpath"),
                expected, actualModules);
    }

    private static void collectModules(String owner, boolean apiOnly, Set<String> modules) {
        if (!modules.add(moduleName(owner))) {
            return;
        }
        Rule rule = RULES.get(owner);
        if (rule != null) {
            rule.api().forEach(dependency -> collectModules(dependency, apiOnly, modules));
            if (!apiOnly) {
                rule.implementation().forEach(dependency -> collectModules(dependency, false, modules));
            }
        }
    }

    private static String moduleName(String owner) {
        return RULES.containsKey(owner) ? rule(owner).moduleName() : EXTERNAL_MODULES.get(owner);
    }

    private static String owner(String className) {
        // Longest package prefix wins: runtime's package is the parent of the other modules.
        String owner = RULES.entrySet().stream()
                .filter(entry -> className.startsWith(entry.getValue().packageName() + "."))
                .max(java.util.Comparator.comparingInt(entry -> entry.getValue().packageName().length()))
                .map(Map.Entry::getKey).orElse("");
        if (!owner.isEmpty()) {
            return owner;
        }
        if (className.startsWith("com.github.ulviar.icli.")) return "icli";
        if (className.startsWith("com.fasterxml.jackson.core.")) return "jackson";
        if (className.startsWith("org.apache.lucene.")) return "lucene-core";
        if (className.startsWith("kotlin.") || className.startsWith("org.jetbrains.annotations.")) return "kotlin-stdlib";
        if (className.startsWith("org.gradle.") || className.startsWith("javax.inject.")) return "gradle-api";
        return "";
    }

    private static String packageName(String className) {
        return className.substring(0, className.lastIndexOf('.'));
    }

    private static boolean forbiddenCapability(String component, String target) {
        if (component.equals("gradle-plugin")) {
            return false;
        }
        boolean network = (target.startsWith("java.net.")
                && !Set.of("java.net.URI", "java.net.URISyntaxException", "java.net.IDN",
                        "java.net.URLEncoder", "java.net.URLDecoder").contains(target))
                || target.matches("java\\.nio\\.channels\\..*(Socket|Datagram).*");
        boolean process = target.startsWith("java.lang.Process") || target.equals("java.lang.Runtime");
        boolean fileAccess = target.equals("java.nio.file.Files")
                || target.startsWith("java.nio.channels.File")
                || target.matches("java\\.io\\.(File(InputStream|OutputStream|Reader|Writer)|RandomAccessFile)");
        return network || (!component.equals("runtime") && (process || fileAccess));
    }

    private static void requireEqual(String label, Set<?> expected, Set<?> actual) {
        if (!expected.equals(actual)) {
            Set<String> added = new TreeSet<>();
            actual.stream().filter(value -> !expected.contains(value)).map(Object::toString).forEach(added::add);
            Set<String> missing = new TreeSet<>();
            expected.stream().filter(value -> !actual.contains(value)).map(Object::toString).forEach(missing::add);
            throw new GradleException(label + " violates the module boundary: unexpected=" + added + ", missing=" + missing);
        }
    }

    record Rule(String moduleName, String packageName, Set<String> api, Set<String> implementation) {}
}
