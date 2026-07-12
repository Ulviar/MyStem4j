package io.github.ulviar.mystem4j.buildlogic;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

public abstract class AgentInfrastructureCheckTask extends DefaultTask {
    private static final Pattern ACTIVE_FILE_NAME =
            Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*\\.md");
    private static final Pattern TRANSIENT_ROOT_ARTIFACT =
            Pattern.compile("(?i)(?:AUDIT|PLAN|HANDOFF)(?:[-_].*)?\\.md");
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[[^\\]]+]\\(([^)]+)\\)");
    private static final String HISTORICAL_WARNING =
            "> Historical baseline. This file records pre-0.1 implementation intent and is not";
    private static final List<String> ROOT_SECTIONS = List.of(
            "# MyStem4j Agent Guide",
            "## Start",
            "## Sources Of Truth",
            "## Context Protocol",
            "## Change Protocol",
            "## LLM Artifacts",
            "## Validation");
    private static final List<String> SCOPED_SECTIONS =
            List.of("## Scope", "## Read", "## Invariants", "## Validation");
    private static final List<String> ACTIVE_FIELDS = List.of(
            "Status: active",
            "Objective: ",
            "Scope: ",
            "Done when: ",
            "Base commit: ",
            "## Constraints",
            "## Decisions",
            "## Changed Files",
            "## Evidence",
            "## Open Issues",
            "## Next Action",
            "## Handoff");
    private static final Set<String> TEMPLATE_PLACEHOLDERS = Set.of(
            "<short objective>",
            "<one measurable objective>",
            "<modules and contracts that may change>",
            "<authoritative evidence required for completion>",
            "<commit inspected before work began>");

    private final ConfigurableFileCollection instructionFiles;
    private final ConfigurableFileCollection activeWorkFiles;
    private final ConfigurableFileCollection historicalFiles;
    private final ConfigurableFileCollection internalMarkdownFiles;
    private final ConfigurableFileCollection rootMarkdownFiles;

    @Inject
    public AgentInfrastructureCheckTask(ObjectFactory objects) {
        instructionFiles = objects.fileCollection();
        activeWorkFiles = objects.fileCollection();
        historicalFiles = objects.fileCollection();
        internalMarkdownFiles = objects.fileCollection();
        rootMarkdownFiles = objects.fileCollection();
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public ConfigurableFileCollection getInstructionFiles() {
        return instructionFiles;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public ConfigurableFileCollection getActiveWorkFiles() {
        return activeWorkFiles;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public ConfigurableFileCollection getHistoricalFiles() {
        return historicalFiles;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public ConfigurableFileCollection getInternalMarkdownFiles() {
        return internalMarkdownFiles;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public ConfigurableFileCollection getRootMarkdownFiles() {
        return rootMarkdownFiles;
    }

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getGitIgnoreFile();

    @Input
    public abstract ListProperty<String> getExpectedInstructionPaths();

    @Input
    public abstract ListProperty<String> getScopedInstructionPaths();

    @Input
    public abstract ListProperty<String> getExpectedHistoricalPaths();

    @Input
    public abstract Property<Integer> getMaxRootInstructionLines();

    @Input
    public abstract Property<Integer> getMaxScopedInstructionLines();

    @Input
    public abstract Property<Integer> getMaxActiveWorkLines();

    @Internal
    public abstract DirectoryProperty getProjectDirectory();

    @TaskAction
    public void checkInfrastructure() {
        File root = getProjectDirectory().get().getAsFile();
        ArrayList<String> violations = new ArrayList<>();

        checkExpectedFiles(root, getExpectedInstructionPaths().get(), "instruction", violations);
        checkExpectedFiles(root, getExpectedHistoricalPaths().get(), "historical", violations);
        checkRootInstructions(root, violations);
        checkScopedInstructions(root, violations);
        checkContextMap(root, violations);
        checkInternalIndex(root, violations);
        checkInternalDocumentReachability(root, violations);
        checkActiveWork(root, violations);
        checkHistoricalWarnings(root, violations);
        checkTransientRootArtifacts(root, violations);
        checkTransientOutputIsIgnored(violations);

        if (!violations.isEmpty()) {
            throw new GradleException("Agent infrastructure check failed:\n- " + String.join("\n- ", violations));
        }
    }

    private static void checkExpectedFiles(
            File root, List<String> expectedPaths, String kind, List<String> violations) {
        for (String path : expectedPaths) {
            if (!root.toPath().resolve(path).toFile().isFile()) {
                violations.add("Missing " + kind + " file " + path + ". Restore it or update the registered context map.");
            }
        }
    }

    private void checkRootInstructions(File root, List<String> violations) {
        File file = root.toPath().resolve("AGENTS.md").toFile();
        if (!file.isFile()) {
            return;
        }
        String text = read(file);
        requireMarkers("AGENTS.md", text, ROOT_SECTIONS, violations);
        checkLineLimit("AGENTS.md", text, getMaxRootInstructionLines().get(), violations);
    }

    private void checkScopedInstructions(File root, List<String> violations) {
        for (String path : getScopedInstructionPaths().get()) {
            File file = root.toPath().resolve(path).toFile();
            if (!file.isFile()) {
                continue;
            }
            String text = read(file);
            requireMarkers(path, text, SCOPED_SECTIONS, violations);
            checkLineLimit(path, text, getMaxScopedInstructionLines().get(), violations);
        }
    }

    private void checkContextMap(File root, List<String> violations) {
        File file = root.toPath().resolve("docs/internal/agent/context-map.md").toFile();
        if (!file.isFile()) {
            return;
        }
        String text = read(file);
        for (String scopedPath : getScopedInstructionPaths().get()) {
            String directory = scopedPath.substring(0, scopedPath.lastIndexOf('/'));
            String expectedLink = directory.equals("buildSrc")
                    ? "../../../buildSrc/AGENTS.md"
                    : "../../../" + directory + "/AGENTS.md";
            if (!text.contains(expectedLink)) {
                violations.add("Context map does not route to " + scopedPath + ". Add the scoped instruction link.");
            }
        }
    }

    private static void checkInternalIndex(File root, List<String> violations) {
        File file = root.toPath().resolve("docs/internal/README.md").toFile();
        if (!file.isFile()) {
            return;
        }
        requireMarkers(
                "docs/internal/README.md",
                read(file),
                List.of("agent/README.md", "decisions/README.md", "agent-work/active/README.md", "history/README.md"),
                violations);
    }

    private void checkInternalDocumentReachability(File root, List<String> violations) {
        Map<String, File> currentDocuments = new HashMap<>();
        for (File file : internalMarkdownFiles.getFiles()) {
            String path = relative(root, file);
            if (isDynamicActiveWork(path) || isArchivedDetail(path)) {
                continue;
            }
            currentDocuments.put(path, file);
        }

        String index = "docs/internal/README.md";
        if (!currentDocuments.containsKey(index)) {
            return;
        }

        Set<String> reachable = new HashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>();
        pending.add(index);
        while (!pending.isEmpty()) {
            String path = pending.removeFirst();
            if (!reachable.add(path)) {
                continue;
            }
            File source = currentDocuments.get(path);
            Matcher matcher = MARKDOWN_LINK.matcher(read(source));
            while (matcher.find()) {
                String target = matcher.group(1).split("#", 2)[0];
                if (target.isBlank()
                        || target.startsWith("http://")
                        || target.startsWith("https://")
                        || target.startsWith("mailto:")) {
                    continue;
                }
                String resolved = relative(
                        root, source.getParentFile().toPath().resolve(target).normalize().toFile());
                if (currentDocuments.containsKey(resolved) && !reachable.contains(resolved)) {
                    pending.addLast(resolved);
                }
            }
        }

        currentDocuments.keySet().stream()
                .filter(path -> !reachable.contains(path))
                .sorted()
                .forEach(path -> violations.add(path
                        + " is not reachable from docs/internal/README.md. Link it from the owning current index or reclassify it."));
    }

    private static boolean isDynamicActiveWork(String path) {
        return path.startsWith("docs/internal/agent-work/active/") && !path.endsWith("/README.md");
    }

    private static boolean isArchivedDetail(String path) {
        return path.startsWith("docs/internal/history/") && !path.equals("docs/internal/history/README.md");
    }

    private void checkActiveWork(File root, List<String> violations) {
        for (File file : activeWorkFiles.getFiles().stream().sorted().toList()) {
            if (file.getName().equals("README.md")) {
                continue;
            }
            String path = relative(root, file);
            if (!ACTIVE_FILE_NAME.matcher(file.getName()).matches()) {
                violations.add(path + " must use a lowercase kebab-case Markdown filename.");
            }
            String text = read(file);
            if (!text.startsWith("# Active Work: ")) {
                violations.add(path + " must start with '# Active Work: '. Use the active-work template.");
            }
            requireMarkers(path, text, ACTIVE_FIELDS, violations);
            checkLineLimit(path, text, getMaxActiveWorkLines().get(), violations);
            if (text.contains("Status: complete")) {
                violations.add(path + " is complete. Promote durable outcomes and delete the active-work file.");
            }
            for (String placeholder : TEMPLATE_PLACEHOLDERS) {
                if (text.contains(placeholder)) {
                    violations.add(path + " still contains template placeholder " + placeholder + ". Replace it.");
                }
            }
            String baseCommit = valueAfter(text, "Base commit: ");
            if (baseCommit != null && !baseCommit.matches("[0-9a-f]{7,40}")) {
                violations.add(path + " has invalid Base commit. Record the inspected Git commit hash.");
            }
        }
    }

    private void checkHistoricalWarnings(File root, List<String> violations) {
        for (File file : historicalFiles.getFiles().stream().sorted().toList()) {
            if (!read(file).contains(HISTORICAL_WARNING)) {
                violations.add(relative(root, file)
                        + " lacks the historical-baseline warning. Do not leave retired intent looking current.");
            }
        }
    }

    private void checkTransientRootArtifacts(File root, List<String> violations) {
        for (File file : rootMarkdownFiles.getFiles().stream().sorted().toList()) {
            if (file.getParentFile().equals(root) && TRANSIENT_ROOT_ARTIFACT.matcher(file.getName()).matches()) {
                violations.add(file.getName()
                        + " looks like transient agent output. Move raw output to build/agent and promote findings to owners.");
            }
        }
    }

    private void checkTransientOutputIsIgnored(List<String> violations) {
        String gitIgnore = read(getGitIgnoreFile().get().getAsFile());
        if (!gitIgnore.lines().anyMatch(line -> line.equals("build/") || line.equals("**/build/"))) {
            violations.add(".gitignore must ignore build output so build/agent artifacts cannot be committed accidentally.");
        }
    }

    private static void requireMarkers(
            String path, String text, List<String> markers, List<String> violations) {
        for (String marker : markers) {
            if (!text.contains(marker)) {
                violations.add(path + " is missing '" + marker + "'. Restore the required structure.");
            }
        }
    }

    private static void checkLineLimit(String path, String text, int limit, List<String> violations) {
        long lines = text.lines().count();
        if (lines > limit) {
            violations.add(path + " has " + lines + " lines; limit is " + limit
                    + ". Move detail to a linked, scoped document.");
        }
    }

    private static String valueAfter(String text, String prefix) {
        return text.lines()
                .filter(line -> line.startsWith(prefix))
                .map(line -> line.substring(prefix.length()).trim())
                .findFirst()
                .orElse(null);
    }

    private static String read(File file) {
        try {
            return Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new GradleException("Failed to read " + file, error);
        }
    }

    private static String relative(File root, File file) {
        return root.toPath()
                .relativize(file.toPath())
                .toString()
                .replace(File.separatorChar, '/');
    }
}
