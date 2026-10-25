package lexor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TerminalSessionTest {
    @TempDir Path directory;

    private record Result(int status, String output, String error) {}

    private Path script(String body) throws IOException {
        Path file = directory.resolve("program.lxr");
        Files.writeString(file, "SCRIPT AREA\nSTART SCRIPT\n" + body + "\nEND SCRIPT\n");
        return file;
    }

    private Result run(InputStream input, String... args) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        int status = TerminalSession.run(args, input,
                new PrintStream(output, true, StandardCharsets.UTF_8),
                new PrintStream(error, true, StandardCharsets.UTF_8));
        return new Result(status, output.toString(StandardCharsets.UTF_8),
                error.toString(StandardCharsets.UTF_8));
    }

    private ByteArrayInputStream input(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }

    private String completion(int status) {
        return System.lineSeparator() + "LEXOR finished with exit code " + status + "."
                + System.lineSeparator() + "Press Enter to close...";
    }

    @Test
    void printsStatusOnItsOwnLineAndConsumesOnlyTheClosingLine() throws IOException {
        ByteArrayInputStream input = input("\r\nremaining");
        Result result = run(input, script("PRINT: 42").toString());
        assertEquals(0, result.status());
        assertEquals("42" + completion(0), result.output());
        assertEquals("", result.error());
        assertEquals("remaining", new String(input.readAllBytes(), StandardCharsets.UTF_8));
    }

    @Test
    void scanDoesNotBufferAwayTheClosingEnterOrFollowingInput() throws IOException {
        ByteArrayInputStream input = input("41\r\n\r\nremaining");
        Result result = run(input, script("DECLARE INT x\nSCAN: x\nPRINT: ++x").toString());
        assertEquals(0, result.status(), result.error());
        assertEquals("42" + completion(0), result.output());
        assertEquals("remaining", new String(input.readAllBytes(), StandardCharsets.UTF_8));
    }

    @Test
    void repeatedScanPreservesSpacesUnicodeAndLineBoundaries() throws IOException {
        ByteArrayInputStream input = input("7\n \n\u4e2d\n\nremaining");
        Result result = run(input, script("""
                DECLARE INT x
                DECLARE CHAR space, letter
                SCAN: x
                SCAN: space
                SCAN: letter
                PRINT: x & "|" & space & "|" & letter
                """).toString());
        assertEquals(0, result.status(), result.error());
        assertEquals("7| |\u4e2d" + completion(0), result.output());
        assertEquals("remaining", new String(input.readAllBytes(), StandardCharsets.UTF_8));
    }

    @Test
    void runtimeErrorAndDebugDiagnosticsRemainVisibleAndKeepStatus() throws IOException {
        Result result = run(input("\n"), "--debug", script("PRINT: 1 % 0").toString());
        assertEquals(1, result.status());
        assertEquals(completion(1), result.output());
        assertTrue(result.error().contains("RuntimeError"), result.error());
        assertTrue(result.error().contains("Semantic analysis passed."), result.error());
    }

    @Test
    void unreadableScriptStillShowsTheCompletionPrompt() {
        Result result = run(input("\n"), directory.resolve("missing.lxr").toString());
        assertEquals(2, result.status());
        assertEquals(completion(2), result.output());
        assertTrue(result.error().contains("Cannot read script"));
    }

    @Test
    void endOfInputDoesNotHangAfterTheScript() throws IOException {
        Result result = run(input(""), script("PRINT: 42").toString());
        assertEquals(0, result.status());
        assertEquals("42" + completion(0), result.output());
    }

    @Test
    void promptReadFailureKeepsScriptStatusAndReportsTheFailure() throws IOException {
        InputStream broken = new InputStream() {
            @Override public int read() throws IOException { throw new IOException("disconnected"); }
        };
        Result result = run(broken, script("PRINT: 42").toString());
        assertEquals(0, result.status());
        assertEquals("42" + completion(0), result.output());
        assertTrue(result.error().contains("Cannot wait for Enter: disconnected"));
    }

    @Test
    void waitsForEnterAfterDisplayingAndFlushingThePrompt() throws Exception {
        CountDownLatch prompt = new CountDownLatch(1);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream stream = new PrintStream(output, true, StandardCharsets.UTF_8) {
            @Override public void flush() {
                super.flush();
                if (output.toString(StandardCharsets.UTF_8).endsWith("Press Enter to close...")) {
                    prompt.countDown();
                }
            }
        };
        try (PipedInputStream input = new PipedInputStream();
                PipedOutputStream typing = new PipedOutputStream(input)) {
            String file = script("PRINT: 42").toString();
            FutureTask<Integer> task = new FutureTask<>(() -> TerminalSession.run(
                    new String[] {file}, input, stream, stream));
            Thread thread = Thread.ofVirtual().start(task);
            try {
                assertTrue(prompt.await(5, TimeUnit.SECONDS));
                typing.write('x');
                typing.flush();
                assertThrows(TimeoutException.class, () -> task.get(100, TimeUnit.MILLISECONDS));
                typing.write('\n');
                typing.flush();
                assertEquals(0, task.get(5, TimeUnit.SECONDS));
            } finally {
                thread.interrupt();
            }
        }
    }

    @Test
    void leavesAllCallerStreamsOpen() throws IOException {
        boolean[] closed = new boolean[3];
        InputStream input = new ByteArrayInputStream("1\n\n".getBytes(StandardCharsets.UTF_8)) {
            @Override public void close() { closed[0] = true; }
        };
        PrintStream output = new PrintStream(new ByteArrayOutputStream()) {
            @Override public void close() { closed[1] = true; }
        };
        PrintStream error = new PrintStream(new ByteArrayOutputStream()) {
            @Override public void close() { closed[2] = true; }
        };
        int status = TerminalSession.run(new String[] {
                script("DECLARE INT x\nSCAN: x\nPRINT: x").toString()}, input, output, error);
        assertEquals(0, status);
        assertArrayEquals(new boolean[3], closed);
    }

    @Test
    void propertiesTransportPreservesUnicodeAndShellCharacters() throws IOException {
        String path = "C:\\folder with spaces\\\u4e2d caf\u00e9 %PATH% ! & # program.lxr";
        Properties properties = new Properties();
        properties.setProperty("args.count", "2");
        properties.setProperty("arg.0", "--debug");
        properties.setProperty("arg.1", path);
        Path file = directory.resolve("arguments.properties");
        try (var output = Files.newOutputStream(file)) {
            properties.store(output, null);
        }
        assertArrayEquals(new String[] {"--debug", path}, TerminalSession.readArguments(file));
    }

    @Test
    void entrypointUsesTransportAndRecordsItsPidAndFinalStatus() throws Exception {
        Path file = script("PRINT: \"\u4e2d caf\u00e9\"");
        Path arguments = directory.resolve("arguments.properties");
        Properties properties = new Properties();
        properties.setProperty("args.count", "1");
        properties.setProperty("arg.0", file.toString());
        try (var output = Files.newOutputStream(arguments)) {
            properties.store(output, null);
        }
        ProcessBuilder builder = javaProcess(TerminalSession.class);
        builder.environment().put("LEXOR_SESSION_DIRECTORY", directory.toString());
        builder.environment().put("LEXOR_SESSION_ARGUMENTS_FILE", arguments.toString());
        setLauncher(builder, ProcessHandle.current());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        try {
            process.getOutputStream().write('\n');
            process.getOutputStream().close();
            assertTrue(process.waitFor(10, TimeUnit.SECONDS));
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(0, process.exitValue(), output);
            assertEquals("\u4e2d caf\u00e9" + completion(0), output);
            assertEquals(Long.toString(process.pid()), Files.readString(directory.resolve("session.started")));
            assertEquals("0", Files.readString(directory.resolve("session.exit")));
        } finally {
            process.destroyForcibly();
        }
    }

    @ParameterizedTest
    @CsvSource({"0,false", "1,false", "2,false", "0,true", "1,true", "2,true"})
    void completedStatusSurvivesEnterAndTerminationWhileWaiting(int status, boolean terminate)
            throws Exception {
        Path file = status == 2 ? directory.resolve("missing.lxr")
                : script(status == 1 ? "PRINT: 1 / 0" : "PRINT: 42");
        ProcessBuilder builder = javaProcess(TerminalSession.class, file.toString());
        builder.environment().put("LEXOR_SESSION_DIRECTORY", directory.toString());
        setLauncher(builder, ProcessHandle.current());
        Process process = builder.redirectErrorStream(true).start();
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
                while (!Files.exists(directory.resolve(SessionProtocol.COMPLETED))) {
                    assertTrue(process.isAlive(), "Session exited without publishing its result");
                    Thread.sleep(10);
                }
            });
            assertEquals(Integer.toString(status), Files.readString(directory.resolve(SessionProtocol.COMPLETED)));
            assertFalse(Files.exists(directory.resolve(SessionProtocol.COMPLETION_TEMP)));
            assertTrue(process.isAlive(), "Session should be waiting for Enter");
            if (terminate) process.destroyForcibly();
            else {
                process.getOutputStream().write('\n');
                process.getOutputStream().flush();
            }
            assertTrue(process.waitFor(10, TimeUnit.SECONDS));
            if (!terminate) assertEquals(status, process.exitValue());
            assertEquals(status, SessionProtocol.completionStatus(directory, process.exitValue()));
        } finally {
            process.destroyForcibly();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void publishesBeforeDisplayingCompletion(int status) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream stream = new PrintStream(output, true, StandardCharsets.UTF_8) {
            @Override public void println(String message) {
                if (message.startsWith("LEXOR finished")) {
                    try {
                        assertEquals(status, SessionProtocol.completionStatus(directory, -1));
                    } catch (IOException e) {
                        fail(e);
                    }
                }
                super.println(message);
            }
        };
        assertEquals(status, TerminalSession.complete(status, directory, input("\n"), stream, stream));
        assertEquals(completion(status), output.toString(StandardCharsets.UTF_8));
    }

    @Test
    void publicationFailureIsAnInfrastructureErrorWithoutAnUnrecordedWait() throws IOException {
        Path blocked = Files.createDirectory(directory.resolve(SessionProtocol.COMPLETED));
        Files.writeString(blocked.resolve("unrelated"), "keep");
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        InputStream unread = new InputStream() {
            @Override public int read() {
                fail("An unrecorded completion must not wait for Enter");
                return -1;
            }
        };
        PrintStream stream = new PrintStream(error, true, StandardCharsets.UTF_8);
        assertEquals(2, TerminalSession.complete(0, directory, unread, stream, stream));
        assertTrue(error.toString(StandardCharsets.UTF_8).contains("Cannot save terminal exit status:"));
        assertFalse(error.toString(StandardCharsets.UTF_8).contains("Press Enter"));
        assertEquals("keep", Files.readString(blocked.resolve("unrelated")));
    }

    @Test
    void stopsAnInfiniteScriptWhenItsLauncherIsForciblyStopped() throws Exception {
        Process launcher = javaProcess(WaitingLauncher.class).start();
        Process session = null;
        try {
            String file = script("""
                    PRINT: "running" & $
                    REPEAT WHEN ("TRUE")
                    START REPEAT
                    END REPEAT
                    """).toString();
            ProcessBuilder builder = javaProcess(TerminalSession.class, file);
            setLauncher(builder, launcher.toHandle());
            session = builder.redirectErrorStream(true).start();
            BufferedReader output = new BufferedReader(
                    new InputStreamReader(session.getInputStream(), StandardCharsets.UTF_8));
            FutureTask<String> firstLine = new FutureTask<>(output::readLine);
            Thread.ofVirtual().start(firstLine);
            assertEquals("running", firstLine.get(5, TimeUnit.SECONDS));
            assertTrue(session.isAlive());
            launcher.destroyForcibly();
            assertTrue(launcher.waitFor(5, TimeUnit.SECONDS));
            assertTrue(session.waitFor(5, TimeUnit.SECONDS), "The session outlived its launcher.");
            assertEquals(2, session.exitValue());
        } finally {
            launcher.destroyForcibly();
            if (session != null) session.destroyForcibly();
        }
    }

    @Test
    void rejectsAReusedLauncherPidWithoutWaitingForEnter() throws Exception {
        ProcessBuilder builder = javaProcess(TerminalSession.class,
                script("PRINT: \"should not execute\"").toString());
        setLauncher(builder, ProcessHandle.current());
        builder.environment().put("LEXOR_LAUNCHER_START", "1970-01-01T00:00:00Z");
        Process session = builder.redirectErrorStream(true).start();
        try {
            assertTrue(session.waitFor(5, TimeUnit.SECONDS));
            assertEquals(2, session.exitValue());
            assertEquals("LEXOR launcher has exited." + System.lineSeparator(),
                    new String(session.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        } finally {
            session.destroyForcibly();
        }
    }

    private ProcessBuilder javaProcess(Class<?> mainClass, String... args) {
        String executable = System.getProperty("os.name").startsWith("Windows")
                ? "java.exe" : "java";
        ArrayList<String> command = new ArrayList<>(Arrays.asList(
                Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-cp", System.getProperty("java.class.path"), mainClass.getName()));
        command.addAll(Arrays.asList(args));
        ProcessBuilder builder = new ProcessBuilder(command);
        for (String key : new String[] {"LEXOR_SESSION_DIRECTORY", "LEXOR_SESSION_ARGUMENTS_FILE",
                "LEXOR_LAUNCHER_PID", "LEXOR_LAUNCHER_START"}) {
            builder.environment().remove(key);
        }
        return builder;
    }

    private void setLauncher(ProcessBuilder builder, ProcessHandle launcher) {
        builder.environment().put("LEXOR_LAUNCHER_PID", Long.toString(launcher.pid()));
        builder.environment().put("LEXOR_LAUNCHER_START",
                launcher.info().startInstant().orElseThrow().toString());
    }

    public static class WaitingLauncher {
        public static void main(String[] args) throws InterruptedException {
            Thread.sleep(TimeUnit.MINUTES.toMillis(1));
        }
    }

    @Test
    void rejectsCorruptArgumentTransport() throws IOException {
        Path file = directory.resolve("arguments.properties");
        for (String content : new String[] {"", "args.count=no", "args.count=-1",
                "args.count=1000000000", "args.count=2\narg.0=--debug"}) {
            Files.writeString(file, content);
            assertThrows(IOException.class, () -> TerminalSession.readArguments(file));
        }
    }
}
