package io.github.ulviar.mystem4j.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MystemExtractTaskTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void extractsExecutableFromZipArchive() throws IOException {
        Path archive = temporaryDirectory.resolve("mystem.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("mystem/bin/mystem"));
            zip.write("fake mystem".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        Project project = ProjectBuilder.builder().build();
        MystemExtractTask task = project.getTasks().register("mystemExtract", MystemExtractTask.class).get();
        Path executable = temporaryDirectory.resolve("prepared/mystem");
        task.getArchiveFile().set(archive.toFile());
        task.getArchiveType().set("zip");
        task.getExecutableName().set("mystem");
        task.getExecutableFile().set(executable.toFile());

        task.extract();

        assertTrue(Files.exists(executable));
        assertEquals("fake mystem", Files.readString(executable, StandardCharsets.UTF_8));
    }

    @Test
    void extractsExecutableFromTarGzArchive() throws IOException {
        Path archive = temporaryDirectory.resolve("mystem.tar.gz");
        writeTarGz(archive, "mystem/bin/mystem", "fake mystem".getBytes(StandardCharsets.UTF_8));

        Project project = ProjectBuilder.builder().build();
        MystemExtractTask task = project.getTasks().register("mystemExtractTarGz", MystemExtractTask.class).get();
        Path executable = temporaryDirectory.resolve("prepared/mystem");
        task.getArchiveFile().set(archive.toFile());
        task.getArchiveType().set("tar.gz");
        task.getExecutableName().set("mystem");
        task.getExecutableFile().set(executable.toFile());

        task.extract();

        assertTrue(Files.exists(executable));
        assertEquals("fake mystem", Files.readString(executable, StandardCharsets.UTF_8));
    }

    private static void writeTarGz(Path archive, String name, byte[] content) throws IOException {
        try (GZIPOutputStream output = new GZIPOutputStream(Files.newOutputStream(archive))) {
            byte[] header = new byte[512];
            writeAscii(header, 0, 100, name);
            writeOctal(header, 100, 8, 0755);
            writeOctal(header, 108, 8, 0);
            writeOctal(header, 116, 8, 0);
            writeOctal(header, 124, 12, content.length);
            writeOctal(header, 136, 12, 0);
            Arrays.fill(header, 148, 156, (byte) ' ');
            header[156] = '0';
            writeAscii(header, 257, 6, "ustar");
            writeAscii(header, 263, 2, "00");
            writeChecksum(header);

            output.write(header);
            output.write(content);
            int padding = (int) ((512 - (content.length % 512L)) % 512);
            output.write(new byte[padding]);
            output.write(new byte[1024]);
        }
    }

    private static void writeAscii(byte[] header, int offset, int length, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, header, offset, Math.min(bytes.length, length));
    }

    private static void writeOctal(byte[] header, int offset, int length, long value) {
        String octal = Long.toOctalString(value);
        String padded = "0".repeat(length - octal.length() - 1) + octal;
        writeAscii(header, offset, length - 1, padded);
        header[offset + length - 1] = 0;
    }

    private static void writeChecksum(byte[] header) {
        long checksum = 0;
        for (byte value : header) {
            checksum += value & 0xFF;
        }
        String octal = Long.toOctalString(checksum);
        String padded = "0".repeat(6 - octal.length()) + octal;
        writeAscii(header, 148, 6, padded);
        header[154] = 0;
        header[155] = (byte) ' ';
    }
}
