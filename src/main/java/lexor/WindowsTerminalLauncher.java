package lexor;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Owns one native console and its temporary launch files. No script path becomes shell code. */
final class WindowsTerminalLauncher implements Main.WindowSupport {
    private static final String LAUNCH_SCRIPT = """
            @echo off
            setlocal DisableDelayedExpansion
            start "LEXOR" /wait "%LEXOR_CMD%" /d /s /v:off /c session.cmd
            exit /b %errorlevel%
            """;
    private static final String SESSION_SCRIPT = """
            @echo off
            setlocal DisableDelayedExpansion
            chcp 65001 >nul
            cd /d "%LEXOR_WORKING_DIRECTORY%"
            if errorlevel 1 exit /b 2
            "%LEXOR_JAVA%" "@%LEXOR_ARGUMENT_FILE%"
            exit /b %errorlevel%
            """;

    @Override
    public Path chooseScript() throws IOException, InterruptedException {
        Path[] selection = new Path[1];
        onEventThread(() -> {
            JFileChooser picker = new JFileChooser(Path.of("").toAbsolutePath().toFile());
            picker.setDialogTitle("Open LEXOR script");
            picker.setFileFilter(new FileNameExtensionFilter("LEXOR scripts (*.lxr)", "lxr"));
            picker.setAcceptAllFileFilterUsed(false);
            if (picker.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                selection[0] = picker.getSelectedFile().toPath();
            }
        });
        return selection[0];
    }

    @Override
    public void showError(String message) {
        // An interrupted launch must still display its error before Main exits.
        boolean interrupted = Thread.interrupted();
        try {
            onEventThread(() -> JOptionPane.showMessageDialog(null, message,
                    "LEXOR launch failed", JOptionPane.ERROR_MESSAGE));
        } catch (IOException | InterruptedException e) {
            System.err.println(message);
            if (e instanceof InterruptedException) interrupted = true;
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private static void onEventThread(Runnable action) throws IOException, InterruptedException {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(action);
            } catch (InvocationTargetException e) {
                throw new IOException("Cannot display the file dialog", e.getCause());
            }
        }
    }

    @Override
    public int launch(String[] args) throws IOException, InterruptedException {
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        Path directory = Files.createTempDirectory("lexor-terminal-");
        // Windows Java launchers can use an ANSI code page even after chcp 65001.
        // Keep launcher arguments ASCII; manifest URLs and Properties carry Unicode.
        if (asciiLaunchPath(directory, workingDirectory) == null) {
            Files.delete(directory);
            directory = Files.createTempDirectory(workingDirectory, ".lexor-session-");
        }
        try (LaunchFiles files = prepare(directory, workingDirectory,
                Path.of(System.getProperty("java.home")), System.getProperty("java.class.path"), args)) {
            OwnedProcess owner = new OwnedProcess();
            Thread shutdown = new Thread(() -> {
                owner.close();
                files.closeQuietly();
            }, "lexor-terminal-shutdown");
            Runtime.getRuntime().addShutdownHook(shutdown);
            try {
                ProcessBuilder builder = files.processBuilder();
                owner.start(builder);
                while (!owner.process().waitFor(500, TimeUnit.MILLISECONDS)) owner.capture();
                int status = owner.process().exitValue();
                if (!Files.exists(directory.resolve("session.started"))) {
                    String diagnostics = Files.readString(directory.resolve("launch.log")).strip();
                    throw new IOException("Java could not start (exit code " + status + ")."
                            + (diagnostics.isEmpty() ? "" : System.lineSeparator() + diagnostics));
                }
                Path completion = directory.resolve("session.exit");
                if (Files.exists(completion)) {
                    try {
                        return Integer.parseInt(Files.readString(completion).strip());
                    } catch (NumberFormatException e) {
                        throw new IOException("Invalid terminal completion status", e);
                    }
                }
                // Closing the console interrupts Java before it can record completion.
                return status;
            } finally {
                owner.close();
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdown);
                } catch (IllegalStateException ignored) {
                    // Shutdown is already running the hook, which owns the same resources.
                }
            }
        }
    }

    static LaunchFiles prepare(Path directory, Path workingDirectory, Path javaHome,
            String classPath, String[] args) throws IOException {
        LaunchFiles files = new LaunchFiles(directory, workingDirectory, javaHome);
        try {
            Properties arguments = new Properties();
            arguments.setProperty("args.count", Integer.toString(args.length));
            for (int i = 0; i < args.length; i++) {
                String argument = args[i];
                if (i == args.length - 1) {
                    argument = workingDirectory.resolve(argument).normalize().toString();
                }
                arguments.setProperty("arg." + i, argument);
            }
            try (OutputStream output = Files.newOutputStream(directory.resolve("session.properties"))) {
                arguments.store(output, "LEXOR session arguments");
            }
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            List<String> locations = new ArrayList<>();
            for (Path entry : absoluteClassPath(classPath, workingDirectory)) {
                locations.add(entry.toUri().toASCIIString());
            }
            manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH, String.join(" ", locations));
            try (JarOutputStream output = new JarOutputStream(
                    Files.newOutputStream(directory.resolve("classpath.jar")), manifest)) {
                // A manifest-only JAR avoids the native launcher's lossy Unicode conversion.
            }
            String bootstrap = asciiLaunchPath(directory.resolve("classpath.jar"), workingDirectory);
            if (bootstrap == null) throw new IOException("Cannot create an ASCII-safe launcher path");
            Files.writeString(directory.resolve("java.args"), argumentFile(List.of(
                    "-Dfile.encoding=UTF-8", "-Dstdin.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
                    "-Dstderr.encoding=UTF-8", "--disable-@files", "-cp", bootstrap,
                    TerminalSession.class.getName())), StandardCharsets.US_ASCII);
            Files.writeString(directory.resolve("launch.cmd"), LAUNCH_SCRIPT, StandardCharsets.US_ASCII);
            Files.writeString(directory.resolve("session.cmd"), SESSION_SCRIPT, StandardCharsets.US_ASCII);
            return files;
        } catch (IOException | RuntimeException e) {
            files.closeQuietly();
            throw e;
        }
    }

    static List<Path> absoluteClassPath(String classPath, Path workingDirectory) throws IOException {
        List<Path> entries = new ArrayList<>();
        for (String entry : classPath.split(Pattern.quote(File.pathSeparator), -1)) {
            if (entry.equals("*") || entry.endsWith("/*") || entry.endsWith("\\*")) {
                String parent = entry.length() == 1 ? "." : entry.substring(0, entry.length() - 1);
                Path folder = workingDirectory.resolve(parent).normalize();
                if (Files.isDirectory(folder)) {
                    try (var jars = Files.list(folder)) {
                        jars.filter(path -> path.getFileName().toString().toLowerCase(java.util.Locale.ROOT)
                                .endsWith(".jar")).sorted().forEach(entries::add);
                    }
                }
            } else {
                entries.add(workingDirectory.resolve(entry).normalize());
            }
        }
        return entries;
    }

    static String argumentFile(List<String> arguments) {
        StringBuilder result = new StringBuilder();
        for (String argument : arguments) {
            result.append('"').append(argument.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
                    .replace("\f", "\\f")).append("\"\n");
        }
        return result.toString();
    }

    static String asciiLaunchPath(Path path, Path workingDirectory) {
        if (StandardCharsets.US_ASCII.newEncoder().canEncode(path.toString())) return path.toString();
        if (path.getRoot().equals(workingDirectory.getRoot())) {
            String relative = workingDirectory.relativize(path).toString();
            if (StandardCharsets.US_ASCII.newEncoder().canEncode(relative)) return relative;
        }
        return null;
    }

    static final class LaunchFiles implements AutoCloseable {
        private final Path directory;
        private final Path workingDirectory;
        private final Path javaHome;

        LaunchFiles(Path directory, Path workingDirectory, Path javaHome) {
            this.directory = directory;
            this.workingDirectory = workingDirectory;
            this.javaHome = javaHome;
        }

        ProcessBuilder processBuilder() throws IOException {
            Path java = javaHome.resolve("bin/java.exe");
            if (!Files.isRegularFile(java)) throw new IOException("Java executable not found: " + java);
            String systemRoot = System.getenv("SystemRoot");
            if (systemRoot == null) throw new IOException("Windows SystemRoot is not set");
            Path cmd = Path.of(systemRoot, "System32", "cmd.exe");
            ProcessBuilder builder = new ProcessBuilder(cmd.toString(), "/d", "/s", "/v:off", "/c", "launch.cmd")
                    .directory(directory.toFile()).redirectErrorStream(true)
                    .redirectOutput(directory.resolve("launch.log").toFile());
            Map<String, String> environment = builder.environment();
            environment.put("LEXOR_CMD", cmd.toString());
            environment.put("LEXOR_JAVA", java.toString());
            environment.put("LEXOR_WORKING_DIRECTORY", workingDirectory.toString());
            environment.put("LEXOR_ARGUMENT_FILE", asciiLaunchPath(directory.resolve("java.args"), workingDirectory));
            environment.put("LEXOR_SESSION_ARGUMENTS_FILE", directory.resolve("session.properties").toString());
            environment.put("LEXOR_SESSION_DIRECTORY", directory.toString());
            ProcessHandle launcher = ProcessHandle.current();
            environment.put("LEXOR_LAUNCHER_PID", Long.toString(launcher.pid()));
            environment.put("LEXOR_LAUNCHER_START", launcher.info().startInstant().orElseThrow().toString());
            return builder;
        }

        @Override
        public synchronized void close() throws IOException {
            // Delete only this launch's known files, never a script or classpath entry.
            for (String name : List.of("launch.cmd", "session.cmd", "java.args", "classpath.jar",
                    "session.properties", "launch.log", "session.started", "session.exit")) {
                Files.deleteIfExists(directory.resolve(name));
            }
            Files.deleteIfExists(directory);
        }

        void closeQuietly() {
            try {
                close();
            } catch (IOException ignored) {
                // Best effort during shutdown; normal completion reports cleanup failures.
            }
        }
    }

    /** Retain handles even if a console process is reparented while the window is closing. */
    static final class OwnedProcess implements AutoCloseable {
        private Process process;
        private boolean closed;
        private final Map<Long, ProcessHandle> descendants = new LinkedHashMap<>();

        synchronized void start(ProcessBuilder builder) throws IOException {
            if (closed) throw new IOException("Terminal launch was cancelled during shutdown");
            process = builder.start();
            // Only the new console accepts input. In particular, the waiting cmd must
            // not block on a "Terminate batch job (Y/N)?" prompt when that console closes.
            process.getOutputStream().close();
            capture();
        }

        synchronized Process process() {
            return process;
        }

        synchronized void capture() {
            if (process != null) process.descendants().forEach(handle -> descendants.put(handle.pid(), handle));
        }

        @Override
        public synchronized void close() {
            closed = true;
            if (process == null) return;
            boolean interrupted = Thread.interrupted();
            try {
                capture();
                // Stop the spawning shell first, then any children captured before it exited.
                process.destroyForcibly();
                List<ProcessHandle> children = new ArrayList<>(descendants.values());
                for (ProcessHandle child : children) {
                    child.descendants().forEach(handle -> descendants.put(handle.pid(), handle));
                }
                descendants.values().forEach(ProcessHandle::destroyForcibly);

                // Windows may keep the command files open until every shell has exited.
                // A shared deadline bounds shutdown even when several processes are owned.
                List<ProcessHandle> owned = new ArrayList<>(descendants.values());
                owned.add(process.toHandle());
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                for (ProcessHandle handle : owned) {
                    var exited = handle.onExit();
                    while (!exited.isDone()) {
                        long remaining = deadline - System.nanoTime();
                        if (remaining <= 0) return;
                        try {
                            exited.get(remaining, TimeUnit.NANOSECONDS);
                        } catch (InterruptedException e) {
                            // Finish cleanup even when an interrupted launch brought us here.
                            interrupted = true;
                        } catch (TimeoutException e) {
                            return;
                        } catch (ExecutionException e) {
                            break;
                        }
                    }
                }
            } finally {
                if (interrupted) Thread.currentThread().interrupt();
            }
        }
    }
}
