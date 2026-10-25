package lexor;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;

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
                SessionProtocol.recordStarted(directory);
            }
            if (!watchLauncher()) {
                err.println("LEXOR launcher has exited.");
                System.exit(2);
                return;
            }
            String argumentsFile = System.getenv("LEXOR_SESSION_ARGUMENTS_FILE");
            String[] scriptArgs = argumentsFile == null ? args : readArguments(Path.of(argumentsFile));
            status = Main.run(scriptArgs, System.in, out, err);
        } catch (IOException | RuntimeException e) {
            err.println("Cannot start terminal session: " + e.getMessage());
            status = 2;
        }
        System.exit(complete(status, directory, System.in, out, err));
    }

    /** Persist completion before any prompt can invite the user to close the console. */
    static int complete(int status, Path directory, InputStream in, PrintStream out, PrintStream err) {
        if (directory != null) {
            try {
                SessionProtocol.recordCompletion(directory, status);
            } catch (IOException | RuntimeException e) {
                err.println("Cannot save terminal exit status: " + e.getMessage());
                err.flush();
                out.flush();
                // Do not wait for closure with an unrecorded successful result.
                return 2;
            }
        }
        return finish(status, in, out, err);
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
        return SessionProtocol.readArguments(file);
    }

    /** Leaves the caller's streams open and preserves the interpreter's exit code. */
    static int run(String[] args, InputStream in, PrintStream out, PrintStream err) {
        int status = Main.run(args, in, out, err);
        return complete(status, null, in, out, err);
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
        } finally {
            err.flush();
            out.flush();
        }
        return status;
    }
}
