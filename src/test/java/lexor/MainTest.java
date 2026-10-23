package lexor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MainTest {
    @TempDir Path directory;

    private record Result(int status, String output, String error) {}

    private Result run(String input, String... args) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        int status = Main.run(args,
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(output, true, StandardCharsets.UTF_8),
                new PrintStream(error, true, StandardCharsets.UTF_8));
        return new Result(status, output.toString(StandardCharsets.UTF_8),
                error.toString(StandardCharsets.UTF_8));
    }

    private Path script(String body) throws IOException {
        Path folder = Files.createDirectories(directory.resolve("folder with spaces"));
        Path file = folder.resolve("program with spaces.lxr");
        Files.writeString(file, "SCRIPT AREA\nSTART SCRIPT\n" + body + "\nEND SCRIPT\n",
                StandardCharsets.UTF_8);
        return file;
    }

    static Stream<Arguments> examples() {
        return Stream.of(
                Arguments.of("examples/specification-sample.lxr", "", "4TRUE5\nc#last"),
                Arguments.of("examples/specification-arithmetic.lxr", "", "[-60]"),
                Arguments.of("examples/specification-logic.lxr", "", "TRUE"),
                Arguments.of("examples/inc-dec.lxr", "",
                        "pre-increment a: 6\nc = ++a:         6\n\n"
                                + "post-increment b: 6\nd = b++:          5\n\n"
                                + "standalone ++a: 7\nstandalone b--: 5\n"),
                Arguments.of("myprogram.lxr", "3\n", "Enter a number:  *\n*3*\n *\n"));
    }

    @ParameterizedTest
    @MethodSource("examples")
    void executesExamplesExactly(String file, String input, String expected) {
        Result result = run(input, file);
        assertEquals(0, result.status(), result.error());
        assertEquals(expected, result.output());
        assertEquals("", result.error());
    }

    @Test
    void readsUtf8FileWithSpacesAndKeepsOutputClean() throws IOException {
        Result result = run("", script("PRINT: \"Maayong adlaw, café!\"").toString());
        assertEquals(0, result.status(), result.error());
        assertEquals("Maayong adlaw, café!", result.output());
        assertEquals("", result.error());
    }

    @Test
    void debugTraceGoesOnlyToStderr() throws IOException {
        Result result = run("", "--debug", script("PRINT: 42").toString());
        assertEquals(0, result.status());
        assertEquals("42", result.output());
        assertTrue(result.error().contains("Parsed 1 statements."));
        assertTrue(result.error().contains("Semantic analysis passed."));
    }

    @Test
    void providesInputAndFreshStateForEachRun() throws IOException {
        Path file = script("DECLARE INT x\nSCAN: x\nPRINT: ++x");
        for (int attempt = 0; attempt < 2; attempt++) {
            Result result = run("41\n", file.toString());
            assertEquals(0, result.status(), result.error());
            assertEquals("42", result.output());
        }
    }

    @Test
    void rejectsSemanticErrorsBeforePrintingAnything() throws IOException {
        Result result = run("", script("PRINT: \"before\"\nPRINT: missing + 1").toString());
        assertEquals(1, result.status());
        assertEquals("", result.output());
        assertTrue(result.error().contains("SemanticError at line 4"), result.error());
        assertFalse(result.error().contains("at lexor."));
    }

    @Test
    void reportsRuntimeErrorsWithExitOneAndSourceLine() throws IOException {
        Result result = run("", script("PRINT: 1 % 0").toString());
        assertEquals(1, result.status());
        assertEquals("", result.output());
        assertTrue(result.error().contains("RuntimeError at line 3"), result.error());
        assertFalse(result.error().contains("ArithmeticException"));
    }

    @Test
    void reportsSyntaxErrorsWithoutStackTrace() throws IOException {
        Result result = run("", script("DECLARE BANANA x").toString());
        assertEquals(1, result.status());
        assertEquals("", result.output());
        assertTrue(result.error().contains("SyntaxError at line 3"), result.error());
        assertFalse(result.error().contains("at lexor."));
    }

    @Test
    void reportsMissingFilesWithExitTwo() {
        Result result = run("", directory.resolve("missing.lxr").toString());
        assertEquals(2, result.status());
        assertEquals("", result.output());
        assertTrue(result.error().contains("Cannot read script"));
    }

    @Test
    void reportsInvalidPathsWithExitTwo() {
        Result result = run("", "invalid\0path");
        assertEquals(2, result.status());
        assertTrue(result.error().contains("Cannot read script"));
    }

    @Test
    void helpSucceedsAndInvalidArgumentsReturnTwo() {
        Result help = run("", "--help");
        assertEquals(0, help.status());
        assertTrue(help.output().startsWith("Usage:"));
        assertEquals("", help.error());
        for (String[] args : new String[][] {
                {}, {"--debug"}, {"--unknown"}, {"a", "b"}, {"--debug", "a", "b"}}) {
            Result result = run("", args);
            assertEquals(2, result.status());
            assertEquals("", result.output());
            assertTrue(result.error().startsWith("Usage:"));
        }
    }
}
