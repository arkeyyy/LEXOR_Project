package lexor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WindowsTerminalLauncherTest {
    @TempDir Path directory;

    private static Path javaHome() {
        return Path.of(System.getProperty("java.home"));
    }

    private static String javaExecutable() {
        return javaHome().resolve("bin/" + (File.separatorChar == '\\' ? "java.exe" : "java"))
                .toString();
    }

    private static ProcessBuilder javaProcess(Class<?> entrypoint, String... args) {
        List<String> command = new ArrayList<>(List.of(javaExecutable(), "-cp",
                System.getProperty("java.class.path"), entrypoint.getName()));
        command.addAll(List.of(args));
        return new ProcessBuilder(command).redirectErrorStream(true);
    }

    private static String complete(ProcessBuilder builder) throws Exception {
        Process process = builder.start();
        try {
            assertTrue(process.waitFor(15, TimeUnit.SECONDS), "Java probe did not finish");
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(0, process.exitValue(), output);
            return output;
        } finally {
            process.destroyForcibly();
        }
    }

    @Test
    void argumentFileRoundTripsThroughTheJavaLauncher() throws Exception {
        List<String> values = List.of("", "a space", "C:\\folder with spaces\\", "a\"b'c",
                "100% ! & ^ | < >", "#comment", "@literal", "line\nreturn\rtab\tform\f");
        List<String> arguments = new ArrayList<>(List.of("--disable-@files", "-cp",
                System.getProperty("java.class.path"), ArgumentProbe.class.getName()));
        arguments.addAll(values);
        Path argumentFile = directory.resolve("java.args");
        Files.writeString(argumentFile, WindowsTerminalLauncher.argumentFile(arguments),
                StandardCharsets.US_ASCII);
        String output = complete(new ProcessBuilder(javaExecutable(), "@" + argumentFile)
                .redirectErrorStream(true));
        List<String> decoded = output.lines().map(line -> new String(Base64.getDecoder().decode(line),
                StandardCharsets.UTF_8)).toList();
        assertEquals(values, decoded);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void manifestLoadsClassesAndJarsFromUnicodeAndShellMetacharacterPaths(boolean packaged)
            throws Exception {
        Path working = Files.createDirectory(directory.resolve("working \u4e2d\u00e9 %PATH% ! &"));
        Path classes = Files.createDirectory(directory.resolve("classes \u4e2d\u00e9 %PATH% ! &"));
        String resource = ArgumentProbe.class.getName().replace('.', '/') + ".class";
        byte[] bytecode;
        try (InputStream input = ArgumentProbe.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            bytecode = input.readAllBytes();
        }
        Path classPath;
        if (packaged) {
            classPath = classes.resolve("application \u4e2d\u00e9 %PATH% ! &.jar");
            try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(classPath))) {
                jar.putNextEntry(new JarEntry(resource));
                jar.write(bytecode);
                jar.closeEntry();
            }
        } else {
            classPath = classes;
            Path classFile = classes.resolve(resource);
            Files.createDirectories(classFile.getParent());
            Files.write(classFile, bytecode);
        }
        Path launch = Files.createDirectory(directory.resolve("session"));
        try (var files = WindowsTerminalLauncher.prepare(launch, working, javaHome(),
                classPath.toString(), new String[] {"script \u4e2d %PATH% ! &.lxr"})) {
            try (JarFile jar = new JarFile(launch.resolve("classpath.jar").toFile())) {
                assertEquals(classPath.toUri().toASCIIString(),
                        jar.getManifest().getMainAttributes().getValue("Class-Path"));
            }
            String bootstrap = WindowsTerminalLauncher.asciiLaunchPath(
                    launch.resolve("classpath.jar"), working);
            Files.writeString(launch.resolve("java.args"), WindowsTerminalLauncher.argumentFile(
                    List.of("-cp", bootstrap, ArgumentProbe.class.getName(), "loaded")),
                    StandardCharsets.US_ASCII);
            String output = complete(new ProcessBuilder(javaExecutable(),
                    "@" + WindowsTerminalLauncher.asciiLaunchPath(launch.resolve("java.args"), working))
                    .directory(working.toFile()).redirectErrorStream(true));
            assertEquals("loaded", new String(Base64.getDecoder().decode(output.strip()),
                    StandardCharsets.UTF_8));
        }
        assertFalse(Files.exists(launch));
        assertTrue(Files.exists(classPath));
    }

    @Test
    void transportsScriptPathsAsPropertiesInsteadOfShellText() throws Exception {
        Path working = Files.createDirectory(directory.resolve("working \u4e2d %PATH% ! &"));
        Path launch = Files.createDirectory(directory.resolve("session"));
        String script = "script \u4e2d\u00e9 %PATH% ! &.lxr";
        try (var files = WindowsTerminalLauncher.prepare(launch, working, javaHome(),
                System.getProperty("java.class.path"), new String[] {"--debug", script})) {
            assertArrayEquals(new String[] {"--debug", working.resolve(script).toString()},
                    TerminalSession.readArguments(launch.resolve("session.properties")));
            assertTrue(Files.readString(launch.resolve("java.args")).chars().allMatch(c -> c < 128));
            for (String name : List.of("launch.cmd", "session.cmd")) {
                String contents = Files.readString(launch.resolve(name));
                assertFalse(contents.contains(script));
                assertFalse(contents.contains(working.toString()));
                assertTrue(contents.contains("DisableDelayedExpansion"));
            }
            assertTrue(Files.readString(launch.resolve("launch.cmd")).contains("/wait"));
            assertTrue(Files.readString(launch.resolve("session.cmd")).contains("chcp 65001"));
            if (File.separatorChar == '\\') {
                ProcessBuilder builder = files.processBuilder();
                assertEquals(launch.toFile(), builder.directory());
                assertEquals(working.toString(), builder.environment().get("LEXOR_WORKING_DIRECTORY"));
                assertEquals(javaHome().resolve("bin/java.exe").toString(),
                        builder.environment().get("LEXOR_JAVA"));
                assertTrue(builder.command().contains("/d"));
                assertTrue(builder.command().contains("/v:off"));
            }
        }
    }

    @Test
    void resolvesRelativeEmptyAndWildcardClasspathEntries() throws Exception {
        Path libraries = Files.createDirectory(directory.resolve("libraries"));
        Path first = Files.createFile(libraries.resolve("a.jar"));
        Path second = Files.createFile(libraries.resolve("b.JAR"));
        Files.createFile(libraries.resolve("ignore.txt"));
        String classPath = "classes" + File.pathSeparator + File.pathSeparator
                + "libraries/*" + File.pathSeparator + directory.resolve("absolute.jar");
        assertEquals(List.of(directory.resolve("classes"), directory, first, second,
                directory.resolve("absolute.jar")),
                WindowsTerminalLauncher.absoluteClassPath(classPath, directory));
    }

    @Test
    void usesRelativeAsciiPathWhenTheWorkingDirectoryContainsUnicode() throws Exception {
        Path working = Files.createDirectory(directory.resolve("working \u4e2d"));
        Path launch = working.resolve(".lexor-session-123/java.args");
        assertEquals(Path.of(".lexor-session-123/java.args").toString(),
                WindowsTerminalLauncher.asciiLaunchPath(launch, working));
        assertNull(WindowsTerminalLauncher.asciiLaunchPath(
                directory.resolve("unrelated \u4e2d/java.args"), working));
    }

    @Test
    void removesKnownFilesAfterNormalCompletionAndAllowsRepeatedCleanup() throws Exception {
        Path launch = Files.createDirectory(directory.resolve("session"));
        var files = WindowsTerminalLauncher.prepare(launch, directory, javaHome(),
                System.getProperty("java.class.path"), new String[] {"program.lxr"});
        for (String name : List.of("launch.log", "session.started", "session.exit")) {
            Files.writeString(launch.resolve(name), "0");
        }
        files.close();
        files.close();
        assertFalse(Files.exists(launch));
    }

    @Test
    void preparationFailureCleansPartialFiles() throws Exception {
        Path launch = Files.createDirectory(directory.resolve("session"));
        assertThrows(RuntimeException.class, () -> WindowsTerminalLauncher.prepare(launch, directory,
                javaHome(), System.getProperty("java.class.path"), new String[] {"invalid\0path"}));
        assertFalse(Files.exists(launch));
    }

    @Test
    void cleanupNeverDeletesAnUnownedFile() throws Exception {
        Path launch = Files.createDirectory(directory.resolve("session"));
        var files = WindowsTerminalLauncher.prepare(launch, directory, javaHome(),
                System.getProperty("java.class.path"), new String[] {"program.lxr"});
        Path unrelated = Files.writeString(launch.resolve("keep.lxr"), "keep");
        assertThrows(IOException.class, files::close);
        assertEquals("keep", Files.readString(unrelated));
        assertFalse(Files.exists(launch.resolve("java.args")));
    }

    @Test
    void closedOwnerRefusesToLaunchAndFailedStartCanBeClosed() {
        var owner = new WindowsTerminalLauncher.OwnedProcess();
        assertThrows(IOException.class,
                () -> owner.start(new ProcessBuilder(directory.resolve("missing-executable").toString())));
        owner.close();
        assertThrows(IOException.class, () -> owner.start(javaProcess(SleepingProbe.class)));
        assertNull(owner.process());
    }

    @Test
    void ownedLauncherReceivesEndOfInputWithoutWaitingForUserInput() throws Exception {
        try (var owner = new WindowsTerminalLauncher.OwnedProcess()) {
            owner.start(javaProcess(EndOfInputProbe.class));
            assertTrue(owner.process().waitFor(10, TimeUnit.SECONDS),
                    "Launcher remained blocked waiting for input");
            assertEquals(0, owner.process().exitValue());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cleanupStopsOwnedDescendantsAndPreservesUnrelatedProcesses(boolean interrupted) throws Exception {
        Path childPidFile = directory.resolve("child.pid");
        Process unrelated = javaProcess(SleepingProbe.class).start();
        ProcessHandle child = null;
        var owner = new WindowsTerminalLauncher.OwnedProcess();
        try {
            owner.start(javaProcess(ParentProbe.class, childPidFile.toString()));
            assertTimeoutPreemptively(Duration.ofSeconds(15), () -> {
                while (!Files.exists(childPidFile) || Files.size(childPidFile) == 0) {
                    assertTrue(owner.process().isAlive(), "Parent probe exited before spawning its child");
                    Thread.sleep(20);
                }
            });
            child = ProcessHandle.of(Long.parseLong(Files.readString(childPidFile))).orElseThrow();
            owner.capture();
            if (interrupted) Thread.currentThread().interrupt();
            owner.close();
            owner.close();
            assertEquals(interrupted, Thread.currentThread().isInterrupted());
            assertFalse(owner.process().isAlive());
            assertFalse(child.isAlive());
            assertTrue(unrelated.isAlive());
        } finally {
            Thread.interrupted();
            owner.close();
            if (child != null) child.destroyForcibly();
            unrelated.destroyForcibly();
            unrelated.waitFor(10, TimeUnit.SECONDS);
        }
    }

    public static final class ArgumentProbe {
        public static void main(String[] args) {
            for (String arg : args) {
                System.out.println(Base64.getEncoder().encodeToString(arg.getBytes(StandardCharsets.UTF_8)));
            }
        }
    }

    public static final class SleepingProbe {
        public static void main(String[] args) throws InterruptedException {
            Thread.sleep(60_000);
        }
    }

    public static final class EndOfInputProbe {
        public static void main(String[] args) throws IOException {
            System.exit(System.in.read() == -1 ? 0 : 1);
        }
    }

    public static final class ParentProbe {
        public static void main(String[] args) throws Exception {
            Process child = javaProcess(SleepingProbe.class).start();
            Files.writeString(Path.of(args[0]), Long.toString(child.pid()));
            child.waitFor();
        }
    }
}
