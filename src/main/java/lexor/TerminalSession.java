package lexor;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.Properties;

/** Runs inside the newly opened console, without dispatching another window. */
public final class TerminalSession {
    private TerminalSession() {}

    public static void main(String[] args) {
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out),
                true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(new FileOutputStream(FileDescriptor.err),
                true, StandardCharsets.UTF_8);
        Path directory = null;
        int status;
        try {
            String sessionDirectory = System.getenv("LEXOR_SESSION_DIRECTORY");
            if (sessionDirectory != null) {
                directory = Path.of(sessionDirectory);
                Files.writeString(directory.resolve("session.started"),
                        Long.toString(ProcessHandle.current().pid()), StandardCharsets.US_ASCII);
            }
            if (!watchLauncher()) {
                err.println("LEXOR launcher has exited.");
                System.exit(2);
                return;
            }
            String argumentsFile = System.getenv("LEXOR_SESSION_ARGUMENTS_FILE");
            String[] scriptArgs = argumentsFile == null ? args : readArguments(Path.of(argumentsFile));
            status = run(scriptArgs, System.in, out, err);
        } catch (IOException | RuntimeException e) {
            err.println("Cannot start terminal session: " + e.getMessage());
            status = finish(2, System.in, out, err);
        }
        if (directory != null) {
            try {
                Files.writeString(directory.resolve("session.exit"), Integer.toString(status),
                        StandardCharsets.US_ASCII);
            } catch (IOException e) {
                err.println("Cannot save terminal exit status: " + e.getMessage());
                status = 2;
            }
        }
        System.exit(status);
    }

    private static boolean watchLauncher() {
        String launcherPid = System.getenv("LEXOR_LAUNCHER_PID");
        if (launcherPid == null) return true;
        Instant expectedStart = Instant.parse(System.getenv("LEXOR_LAUNCHER_START"));
        ProcessHandle launcher = ProcessHandle.of(Long.parseLong(launcherPid))
                .filter(process -> process.info().startInstant().filter(expectedStart::equals).isPresent())
                .orElse(null);
        if (launcher == null) return false;
        Thread.ofPlatform().daemon().name("lexor-launcher-watch").start(() -> {
            launcher.onExit().join();
            System.exit(2);
        });
        return true;
    }

    static String[] readArguments(Path file) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        }
        int count;
        try {
            count = Integer.parseInt(properties.getProperty("args.count", ""));
        } catch (NumberFormatException e) {
            throw new IOException("Invalid session argument count.", e);
        }
        // A session accepts a script path and optional --debug; reject corrupt transport files.
        if (count < 1 || count > 2) throw new IOException("Invalid session argument count.");
        String[] args = new String[count];
        for (int i = 0; i < count; i++) {
            args[i] = properties.getProperty("arg." + i);
            if (args[i] == null) throw new IOException("Missing session argument " + i + ".");
        }
        return args;
    }

    /** Leaves the caller's streams open and preserves the interpreter's exit code. */
    static int run(String[] args, InputStream in, PrintStream out, PrintStream err) {
        InputStream lines = new LineInputStream(in);
        int status = Main.run(args, lines, out, err);
        return finish(status, lines, out, err);
    }

    private static int finish(int status, InputStream in, PrintStream out, PrintStream err) {
        err.flush();
        out.println();
        out.println("LEXOR finished with exit code " + status + ".");
        out.print("Press Enter to close...");
        out.flush();
        try {
            while (true) {
                int next = in.read();
                if (next == '\n' || next == -1) break;
            }
        } catch (IOException e) {
            err.println("Cannot wait for Enter: " + e.getMessage());
        }
        return status;
    }

    /**
     * Scanner owns a reader buffer. Limit each fill to one line so pasted SCAN values
     * and the closing Enter remain available to their respective consumers.
     */
    private static final class LineInputStream extends InputStream {
        private final InputStream input;

        private LineInputStream(InputStream input) {
            this.input = Objects.requireNonNull(input);
        }

        @Override
        public int read() throws IOException {
            return input.read();
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            if (length == 0) return 0;
            int count = 0;
            while (count < length) {
                int next = input.read();
                if (next == -1) return count == 0 ? -1 : count;
                bytes[offset + count++] = (byte) next;
                if (next == '\n') break;
            }
            return count;
        }

        // Keep InputStream.available() == 0: InputStreamReader must not prefetch another line.
        // Keep InputStream.close() a no-op: this wrapper never owns the caller's stream.
    }
}
