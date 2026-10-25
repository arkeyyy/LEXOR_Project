package lexor;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.awt.GraphicsEnvironment;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import lexor.nodes.ProgramNode;

public final class Main {
    private static final String USAGE =
            "Usage: java -jar Lexor_Project-1.0-SNAPSHOT.jar [--console] [--debug] [script.lxr]"
                    + System.lineSeparator()
                    + "       --help    Show this help without opening a window."
                    + System.lineSeparator()
                    + "A script is required with --console and on non-Windows/headless systems.";

    private Main() {}

    public static void main(String[] args) {
        boolean windowed = System.getProperty("os.name", "").startsWith("Windows")
                && !GraphicsEnvironment.isHeadless();
        System.exit(dispatch(args, System.in, System.out, System.err,
                windowed, new WindowsTerminalLauncher()));
    }

    interface WindowSupport {
        Path chooseScript() throws IOException, InterruptedException;
        int launch(String[] args) throws IOException, InterruptedException;
        void showError(String message);
    }

    /** Dispatch is separate from execution so help, cancellation and CLI mode need no UI. */
    static int dispatch(String[] args, InputStream in, PrintStream out, PrintStream err,
            boolean windowed, WindowSupport windows) {
        Options options = Options.parse(args);
        if (options == null || options.help() || options.console() || !windowed) {
            return run(args, in, out, err);
        }
        try {
            String script = options.script();
            if (script == null) {
                Path selection = windows.chooseScript();
                if (selection == null) return 0;
                script = selection.toString();
            }
            return windows.launch(options.executionArguments(script));
        } catch (IOException | RuntimeException e) {
            return launchFailure(e, err, windows);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return launchFailure(e, err, windows);
        }
    }

    private static int launchFailure(Exception failure, PrintStream err, WindowSupport windows) {
        String message = "Cannot open LEXOR terminal: " + failure.getMessage();
        err.println(message);
        windows.showError(message);
        return 2;
    }

    private record Options(boolean console, boolean debug, boolean help, String script) {
        static Options parse(String[] args) {
            if (args.length == 1 && args[0].equals("--help")) {
                return new Options(false, false, true, null);
            }
            boolean console = false;
            boolean debug = false;
            String script = null;
            for (String arg : args) {
                if (script != null) return null;
                if (arg.equals("--console") && !console) console = true;
                else if (arg.equals("--debug") && !debug) debug = true;
                else if (arg.startsWith("--")) return null;
                else script = arg;
            }
            return new Options(console, debug, false, script);
        }

        String[] executionArguments(String file) {
            List<String> result = new ArrayList<>();
            if (debug) result.add("--debug");
            result.add(file);
            return result.toArray(String[]::new);
        }
    }

    /** Executes one script without closing the caller's streams or terminating the JVM. */
    public static int run(String[] args, InputStream in, PrintStream out, PrintStream err) {
        try {
            return runScript(args, in, out, err);
        } finally {
            out.flush();
            err.flush();
        }
    }

    private static int runScript(String[] args, InputStream in, PrintStream out, PrintStream err) {
        Options options = Options.parse(args);
        if (options != null && options.help()) {
            out.println(USAGE);
            return 0;
        }
        if (options == null || options.script() == null) {
            err.println(USAGE);
            return 2;
        }

        boolean debug = options.debug();
        String file = options.script();
        try {
            String source = Files.readString(Path.of(file), StandardCharsets.UTF_8);
            List<Token> tokens = new Lexer(source).tokenize();
            if (debug) tokens.forEach(err::println);

            ProgramNode program = new Parser(tokens).parse();
            if (debug) err.println("Parsed " + program.statements.size() + " statements.");

            SemanticAnalyzer analyzer = new SemanticAnalyzer();
            analyzer.analyze(program);
            if (debug) err.println("Semantic analysis passed.");

            new Interpreter(analyzer.getSymbolTable(), in, out).execute(program);
            return 0;
        } catch (LexorException e) {
            err.println(e.getMessage());
            if (debug) e.printStackTrace(err);
            return 1;
        } catch (IOException | InvalidPathException e) {
            err.println("Cannot read script '" + file + "': " + e.getMessage());
            if (debug) e.printStackTrace(err);
            return 2;
        } catch (RuntimeException e) {
            err.println("Internal interpreter error: " + e.getMessage());
            if (debug) e.printStackTrace(err);
            return 1;
        }
    }
}
