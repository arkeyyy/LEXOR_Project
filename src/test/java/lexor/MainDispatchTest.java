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

class MainDispatchTest {
    @TempDir Path directory;

    private record Result(int status, String output, String error) {}

    private static final class Windows implements Main.WindowSupport {
        Path selection;
        int selections;
        int launches;
        int status;
        String[] arguments;
        String error;
        IOException failure;
        boolean interrupt;

        @Override
        public Path chooseScript() {
            selections++;
            return selection;
        }

        @Override
        public int launch(String[] args) throws IOException, InterruptedException {
            launches++;
            arguments = args;
            if (failure != null) throw failure;
            if (interrupt) throw new InterruptedException("Launch interrupted");
            return status;
        }

        @Override
        public void showError(String message) {
            error = message;
        }

        void assertUntouched() {
            assertEquals(0, selections);
            assertEquals(0, launches);
            assertNull(error);
        }
    }

    private Result dispatch(Windows windows, boolean windowed, String input, String... args) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        int status = Main.dispatch(args,
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(output, true, StandardCharsets.UTF_8),
                new PrintStream(error, true, StandardCharsets.UTF_8), windowed, windows);
        return new Result(status, output.toString(StandardCharsets.UTF_8),
                error.toString(StandardCharsets.UTF_8));
    }

    private Path script(String body) throws IOException {
        Path file = directory.resolve("program with spaces \u4e2d %PATH% ! &.lxr");
        Files.writeString(file, "SCRIPT AREA\nSTART SCRIPT\n" + body + "\nEND SCRIPT\n",
                StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void selectsAndLaunchesOneScriptWhenNoArgumentsAreProvided() throws IOException {
        Windows windows = new Windows();
        windows.selection = script("PRINT: 42");
        Result result = dispatch(windows, true, "");
        assertEquals(new Result(0, "", ""), result);
        assertEquals(1, windows.selections);
        assertEquals(1, windows.launches);
        assertArrayEquals(new String[] {windows.selection.toString()}, windows.arguments);
    }

    @Test
    void preservesDebugWhenSelectingAScript() throws IOException {
        Windows windows = new Windows();
        windows.selection = script("PRINT: 42");
        Result result = dispatch(windows, true, "", "--debug");
        assertEquals(0, result.status());
        assertEquals(1, windows.selections);
        assertArrayEquals(new String[] {"--debug", windows.selection.toString()}, windows.arguments);
    }

    @Test
    void cancellingThePickerExitsSuccessfullyWithoutLaunching() {
        for (String[] args : new String[][] {{}, {"--debug"}}) {
            Windows windows = new Windows();
            Result result = dispatch(windows, true, "", args);
            assertEquals(new Result(0, "", ""), result);
            assertEquals(1, windows.selections);
            assertEquals(0, windows.launches);
            assertNull(windows.error);
        }
    }

    @Test
    void suppliedScriptSkipsPickerAndPreservesExitStatus() {
        for (int status : new int[] {0, 1, 2, 130}) {
            Windows windows = new Windows();
            windows.status = status;
            Result result = dispatch(windows, true, "", "--debug", "myprogram.lxr");
            assertEquals(new Result(status, "", ""), result);
            assertEquals(0, windows.selections);
            assertEquals(1, windows.launches);
            assertArrayEquals(new String[] {"--debug", "myprogram.lxr"}, windows.arguments);
        }
    }

    @Test
    void helpNeverOpensAnyWindow() {
        Windows windows = new Windows();
        Result result = dispatch(windows, true, "", "--help");
        assertEquals(0, result.status());
        assertTrue(result.output().startsWith("Usage:"));
        assertTrue(result.output().contains("--console"));
        assertEquals("", result.error());
        windows.assertUntouched();
    }

    @Test
    void invalidArgumentsNeverOpenAnyWindow() {
        for (String[] args : new String[][] {
                {"--unknown"}, {"a", "b"}, {"--debug", "a", "b"},
                {"--console", "--console", "a"}, {"--debug", "--debug", "a"},
                {"--console"}, {"--console", "--debug"}, {"--help", "a"}}) {
            Windows windows = new Windows();
            Result result = dispatch(windows, true, "", args);
            assertEquals(2, result.status());
            assertEquals("", result.output());
            assertTrue(result.error().startsWith("Usage:"));
            windows.assertUntouched();
        }
    }

    @Test
    void launchFailureShowsDiagnosticAndReturnsTwo() {
        Windows windows = new Windows();
        windows.failure = new IOException("Java executable unavailable");
        Result result = dispatch(windows, true, "", "myprogram.lxr");
        assertEquals(2, result.status());
        assertEquals("", result.output());
        assertEquals("Cannot open LEXOR terminal: Java executable unavailable", windows.error);
        assertEquals(windows.error + System.lineSeparator(), result.error());
    }

    @Test
    void interruptedLaunchPreservesInterruptAndReportsFailure() {
        Windows windows = new Windows();
        windows.interrupt = true;
        try {
            Result result = dispatch(windows, true, "", "myprogram.lxr");
            assertEquals(2, result.status());
            assertTrue(Thread.currentThread().isInterrupted());
            assertTrue(windows.error.contains("Launch interrupted"));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void consoleUsesRedirectedInputAndWritesOnlyScriptOutput() throws IOException {
        Windows windows = new Windows();
        Path file = script("DECLARE INT x\nPRINT: \"Enter: \"\nSCAN: x\nPRINT: ++x");
        Result result = dispatch(windows, true, "41\n", "--console", file.toString());
        assertEquals(new Result(0, "Enter: 42", ""), result);
        windows.assertUntouched();
    }

    @Test
    void consoleAcceptsEitherOrderOfConsoleAndDebugFlags() throws IOException {
        Path file = script("PRINT: 42");
        for (String[] args : new String[][] {
                {"--console", "--debug", file.toString()},
                {"--debug", "--console", file.toString()}}) {
            Windows windows = new Windows();
            Result result = dispatch(windows, true, "", args);
            assertEquals(0, result.status());
            assertEquals("42", result.output());
            assertTrue(result.error().contains("Semantic analysis passed."));
            windows.assertUntouched();
        }
    }

    static Stream<Arguments> consoleFailures() {
        return Stream.of(
                Arguments.of("PRINT: 1 % 0", 1, "RuntimeError at line 3"),
                Arguments.of("PRINT: missing", 1, "SemanticError at line 3"),
                Arguments.of("DECLARE BANANA x", 1, "SyntaxError at line 3"));
    }

    @ParameterizedTest
    @MethodSource("consoleFailures")
    void consoleRetainsLanguageErrorStatusesAndDiagnostics(String body, int expectedStatus,
            String expectedError) throws IOException {
        Windows windows = new Windows();
        Result result = dispatch(windows, true, "", "--console", script(body).toString());
        assertEquals(expectedStatus, result.status());
        assertEquals("", result.output());
        assertTrue(result.error().startsWith(expectedError), result.error());
        assertFalse(result.error().contains("Press Enter"));
        windows.assertUntouched();
    }

    @Test
    void consoleRetainsFileErrorStatus() {
        Windows windows = new Windows();
        Result result = dispatch(windows, true, "", "--console",
                directory.resolve("missing.lxr").toString());
        assertEquals(2, result.status());
        assertEquals("", result.output());
        assertTrue(result.error().startsWith("Cannot read script"));
        windows.assertUntouched();
    }

    @Test
    void nonWindowsAndHeadlessExecutionRequireAScript() {
        for (String[] args : new String[][] {{}, {"--debug"}}) {
            Windows windows = new Windows();
            Result result = dispatch(windows, false, "", args);
            assertEquals(2, result.status());
            assertEquals("", result.output());
            assertTrue(result.error().startsWith("Usage:"));
            windows.assertUntouched();
        }
    }

    @Test
    void nonWindowsAndHeadlessExecutionUseExistingStreams() throws IOException {
        Windows windows = new Windows();
        Result result = dispatch(windows, false, "", script("PRINT: 42").toString());
        assertEquals(new Result(0, "42", ""), result);
        windows.assertUntouched();
    }
}
