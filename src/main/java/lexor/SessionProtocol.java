package lexor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Properties;

/** Files owned by one terminal session and its UTF-8/ASCII-safe transport. */
final class SessionProtocol {
    static final String LAUNCH = "launch.cmd";
    static final String SESSION = "session.cmd";
    static final String JAVA_ARGUMENTS = "java.args";
    static final String CLASS_PATH = "classpath.jar";
    static final String ARGUMENTS = "session.properties";
    static final String LOG = "launch.log";
    static final String STARTED = "session.started";
    static final String COMPLETED = "session.exit";
    static final String COMPLETION_TEMP = "session.exit.tmp";
    static final List<String> OWNED_FILES = List.of(LAUNCH, SESSION, JAVA_ARGUMENTS,
            CLASS_PATH, ARGUMENTS, LOG, STARTED, COMPLETED, COMPLETION_TEMP);

    private SessionProtocol() {}

    static void writeArguments(Path directory, Path workingDirectory, String[] args) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("args.count", Integer.toString(args.length));
        for (int i = 0; i < args.length; i++) {
            String argument = i == args.length - 1
                    ? workingDirectory.resolve(args[i]).normalize().toString() : args[i];
            properties.setProperty("arg." + i, argument);
        }
        try (OutputStream output = Files.newOutputStream(directory.resolve(ARGUMENTS))) {
            properties.store(output, "LEXOR session arguments");
        }
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
        if (count < 1 || count > 2) throw new IOException("Invalid session argument count.");
        String[] args = new String[count];
        for (int i = 0; i < count; i++) {
            args[i] = properties.getProperty("arg." + i);
            if (args[i] == null) throw new IOException("Missing session argument " + i + ".");
        }
        return args;
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

    static void recordStarted(Path directory) throws IOException {
        Files.writeString(directory.resolve(STARTED), Long.toString(ProcessHandle.current().pid()),
                StandardCharsets.US_ASCII);
    }

    static void recordCompletion(Path directory, int status) throws IOException {
        Path temporary = directory.resolve(COMPLETION_TEMP);
        Files.writeString(temporary, Integer.toString(status), StandardCharsets.US_ASCII);
        // Readers see either a complete status or no status, including during window closure.
        Files.move(temporary, directory.resolve(COMPLETED),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    static int completionStatus(Path directory, int interruptedStatus) throws IOException {
        Path completion = directory.resolve(COMPLETED);
        if (!Files.exists(completion)) {
            if (interruptedStatus == 2) {
                throw new IOException("Terminal session ended without publishing its completion status (exit code 2).");
            }
            return interruptedStatus;
        }
        try {
            int status = Integer.parseInt(Files.readString(completion, StandardCharsets.US_ASCII).strip());
            if (status < 0 || status > 2) throw new NumberFormatException("Out of range");
            return status;
        } catch (NumberFormatException e) {
            throw new IOException("Invalid terminal completion status", e);
        }
    }
}
