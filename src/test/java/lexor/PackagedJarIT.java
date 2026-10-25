package lexor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises the installed entrypoint, manifest and OS streams after package. */
class PackagedJarIT {
    @TempDir Path directory;

    private record Result(int status, String output, String error) {}

    private Path script(String body) throws Exception {
        Path file = directory.resolve("script with spaces.lxr");
        return Files.writeString(file, "SCRIPT AREA\nSTART SCRIPT\n" + body + "\nEND SCRIPT\n");
    }

    private Result run(boolean headless, String input, String... args) throws Exception {
        Path jar = Path.of(System.getProperty("lexor.jar")).toAbsolutePath();
        assertTrue(Files.isRegularFile(jar), "Packaged JAR missing: run Maven verify");
        String java = Path.of(System.getProperty("java.home"), "bin",
                File.separatorChar == '\\' ? "java.exe" : "java").toString();
        List<String> command = new ArrayList<>(List.of(java,
                "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                "-Djava.awt.headless=" + headless, "-jar", jar.toString()));
        command.addAll(List.of(args));
        Path output = directory.resolve("stdout.txt");
        Path error = directory.resolve("stderr.txt");
        Process process = new ProcessBuilder(command).directory(directory.toFile())
                .redirectOutput(output.toFile()).redirectError(error.toFile()).start();
        try {
            process.getOutputStream().write(input.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().close();
            assertTrue(process.waitFor(15, TimeUnit.SECONDS), "Packaged CLI timed out");
            return new Result(process.exitValue(), Files.readString(output), Files.readString(error));
        } finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void consoleRunsWithoutWindowsEvenWhenGraphicsAreEnabled() throws Exception {
        Result result = run(false, "", "--console", script("PRINT: 6 * 7").toString());
        assertEquals(new Result(0, "42", ""), result);
    }

    @Test
    void debugWritesDiagnosticsToStderr() throws Exception {
        Result result = run(false, "", "--console", "--debug", script("PRINT: 42").toString());
        assertEquals(0, result.status());
        assertEquals("42", result.output());
        assertTrue(result.error().contains("Semantic analysis passed."), result.error());
        assertTrue(result.error().contains("PRINT"), result.error());
    }

    @Test
    void helpNeverNeedsAWindowOrScript() throws Exception {
        Result result = run(false, "", "--help");
        assertEquals(0, result.status());
        assertTrue(result.output().startsWith("Usage:"), result.output());
        assertEquals("", result.error());
    }

    @Test
    void redirectedInputPreservesRepeatedLinesSpacesAndUnicode() throws Exception {
        Result result = run(false, "41\r\n \r\n\u4e2d\r\n", "--console", script("""
                DECLARE INT x
                DECLARE CHAR space, letter
                SCAN: x
                SCAN: space
                SCAN: letter
                PRINT: ++x & "|" & space & "|" & letter
                """).toString());
        assertEquals(new Result(0, "42| |\u4e2d", ""), result);
    }

    @Test
    void headlessRunsWithoutConsoleFlag() throws Exception {
        Result result = run(true, "", script("PRINT: 42").toString());
        assertEquals(new Result(0, "42", ""), result);
    }

    @Test
    void headlessMissingScriptReturnsUsage() throws Exception {
        Result result = run(true, "");
        assertEquals(2, result.status());
        assertEquals("", result.output());
        assertTrue(result.error().startsWith("Usage:"));
    }

    @Test
    void runtimeFailurePreservesOutputAndReturnsOne() throws Exception {
        Result result = run(false, "", "--console", script("PRINT: 42\nPRINT: 1 / 0").toString());
        assertEquals(1, result.status());
        assertEquals("42", result.output());
        assertTrue(result.error().contains("RuntimeError"), result.error());
        assertTrue(result.error().contains("line 4"), result.error());
    }

    @Test
    void missingSourceReturnsTwo() throws Exception {
        Result result = run(false, "", "--console", directory.resolve("missing.lxr").toString());
        assertEquals(2, result.status());
        assertEquals("", result.output());
        assertTrue(result.error().contains("Cannot read script"), result.error());
    }
}
