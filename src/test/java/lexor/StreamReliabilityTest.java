package lexor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import lexor.nodes.ProgramNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class StreamReliabilityTest {
    @TempDir Path directory;

    private static String source(String body) {
        return "SCRIPT AREA\nSTART SCRIPT\n" + body + "\nEND SCRIPT\n";
    }

    private Path script(String body) throws IOException {
        return Files.writeString(directory.resolve("script.lxr"), source(body));
    }

    private static PrintStream output(OutputStream output) {
        return new PrintStream(output, false, StandardCharsets.UTF_8);
    }

    private static InputStream failingInput(String partial) {
        byte[] bytes = partial.getBytes(StandardCharsets.UTF_8);
        return new InputStream() {
            int position;
            @Override public int read() throws IOException {
                if (position == bytes.length) throw new IOException("input disconnected");
                return bytes[position++] & 0xff;
            }
        };
    }

    static Stream<String> incompleteLines() {
        // Also cross the reader buffer boundary so Scanner has already buffered digits.
        return Stream.of("", "42", "0".repeat(9000) + "42");
    }

    @ParameterizedTest
    @MethodSource("incompleteLines")
    void scanRejectsImmediateAndPartialReadFailures(String partial) {
        ProgramNode program = new Parser(new Lexer(source("DECLARE INT x=7\nSCAN: x")).tokenize()).parse();
        SemanticAnalyzer analyzer = new SemanticAnalyzer();
        analyzer.analyze(program);
        Interpreter interpreter = new Interpreter(analyzer.getSymbolTable(), failingInput(partial),
                output(new ByteArrayOutputStream()));
        LexorException error = assertThrows(LexorException.class, () -> interpreter.execute(program));
        assertEquals(LexorException.Phase.RUNTIME, error.getPhase());
        assertEquals(4, error.getLine());
        assertTrue(error.getMessage().contains("Cannot read SCAN input: input disconnected"));
        assertEquals(7, analyzer.getSymbolTable().getValue("x"));
    }

    @Test
    void scanFailureLeavesEveryTargetUnchanged() {
        ProgramNode program = new Parser(new Lexer(source("DECLARE INT a=7,b=8\nSCAN: a,b")).tokenize()).parse();
        SemanticAnalyzer analyzer = new SemanticAnalyzer();
        analyzer.analyze(program);
        Interpreter interpreter = new Interpreter(analyzer.getSymbolTable(), failingInput("42,99"),
                output(new ByteArrayOutputStream()));
        assertThrows(LexorException.class, () -> interpreter.execute(program));
        assertEquals(7, analyzer.getSymbolTable().getValue("a"));
        assertEquals(8, analyzer.getSymbolTable().getValue("b"));
    }

    @Test
    void completedLineIsAcceptedBeforeFailureOnTheNextScan() {
        ProgramNode program = new Parser(new Lexer(source(
                "DECLARE INT a=7,b=8\nSCAN: a\nSCAN: b")).tokenize()).parse();
        SemanticAnalyzer analyzer = new SemanticAnalyzer();
        analyzer.analyze(program);
        Interpreter interpreter = new Interpreter(analyzer.getSymbolTable(), failingInput("42\r\n99"),
                output(new ByteArrayOutputStream()));
        LexorException error = assertThrows(LexorException.class, () -> interpreter.execute(program));
        assertEquals(5, error.getLine());
        assertEquals(42, analyzer.getSymbolTable().getValue("a"));
        assertEquals(8, analyzer.getSymbolTable().getValue("b"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failedPrintWriteOrFlushProducesRuntimeDiagnosticAndExitOne(boolean failOnFlush) throws IOException {
        OutputStream broken = new OutputStream() {
            @Override public void write(int value) throws IOException {
                if (!failOnFlush) throw new IOException("write failed");
            }
            @Override public void flush() throws IOException {
                if (failOnFlush) throw new IOException("flush failed");
            }
        };
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        int status = Main.run(new String[] {script("PRINT: 42").toString()},
                InputStream.nullInputStream(), output(broken), output(errors));
        assertEquals(1, status);
        assertTrue(errors.toString(StandardCharsets.UTF_8).contains(
                "RuntimeError at line 3: Cannot write PRINT output."));
    }

    @Test
    void scanIoFailureReturnsOneThroughMain() throws IOException {
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        int status = Main.run(new String[] {script("DECLARE INT x\nSCAN: x").toString()},
                failingInput("42"), output(new ByteArrayOutputStream()), output(errors));
        assertEquals(1, status);
        assertTrue(errors.toString(StandardCharsets.UTF_8).contains("RuntimeError at line 4"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\n", "\r\n"})
    void consecutiveRunsShareInputWithoutConsumingEachOthersLines(String newline) throws IOException {
        InputStream input = new ByteArrayInputStream(("41" + newline + "99" + newline + "remaining")
                .getBytes(StandardCharsets.UTF_8));
        String file = script("DECLARE INT x\nSCAN: x\nPRINT: x").toString();
        for (String expected : new String[] {"41", "99"}) {
            ByteArrayOutputStream text = new ByteArrayOutputStream();
            ByteArrayOutputStream errors = new ByteArrayOutputStream();
            assertEquals(0, Main.run(new String[] {file}, input, output(text), output(errors)),
                    errors.toString(StandardCharsets.UTF_8));
            assertEquals(expected, text.toString(StandardCharsets.UTF_8));
        }
        assertEquals("remaining", new String(input.readAllBytes(), StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @ValueSource(strings = {"success", "runtime", "syntax", "semantic", "file", "usage", "help"})
    void everyReturnFlushesBothStreamsAndKeepsCallerStreamsOpen(String path) throws IOException {
        class Sink extends ByteArrayOutputStream {
            boolean closed;
            @Override public void close() { closed = true; }
        }
        Sink text = new Sink();
        Sink errors = new Sink();
        PrintStream out = output(new BufferedOutputStream(text));
        PrintStream err = output(new BufferedOutputStream(errors));
        out.print("pending output|");
        err.print("pending diagnostic|");
        boolean[] inputClosed = {false};
        InputStream in = new ByteArrayInputStream(new byte[0]) {
            @Override public void close() { inputClosed[0] = true; }
        };
        String body = switch (path) {
            case "runtime" -> "PRINT: 1/0";
            case "syntax" -> "PRINT:";
            case "semantic" -> "PRINT: unknown";
            default -> "";
        };
        String[] args = switch (path) {
            case "file" -> new String[] {directory.resolve("missing.lxr").toString()};
            case "usage" -> new String[0];
            case "help" -> new String[] {"--help"};
            default -> new String[] {script(body).toString()};
        };
        int expected = switch (path) {
            case "runtime", "syntax", "semantic" -> 1;
            case "file", "usage" -> 2;
            default -> 0;
        };
        assertEquals(expected, Main.run(args, in, out, err));
        assertTrue(text.toString(StandardCharsets.UTF_8).startsWith("pending output|"));
        assertTrue(errors.toString(StandardCharsets.UTF_8).startsWith("pending diagnostic|"));
        if (expected != 0) assertTrue(errors.size() > "pending diagnostic|".length());
        assertFalse(text.closed);
        assertFalse(errors.closed);
        assertFalse(inputClosed[0]);
    }
}
