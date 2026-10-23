package lexor;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import lexor.nodes.ProgramNode;

public final class Main {
    private static final String USAGE =
            "Usage: java -jar Lexor_Project-1.0-SNAPSHOT.jar [--debug] <script.lxr>";

    private Main() {}

    public static void main(String[] args) {
        System.exit(run(args, System.in, System.out, System.err));
    }

    /** Executes one script without closing the caller's streams or terminating the JVM. */
    public static int run(String[] args, InputStream in, PrintStream out, PrintStream err) {
        if (args.length == 1 && args[0].equals("--help")) {
            out.println(USAGE);
            return 0;
        }
        boolean debug = args.length == 2 && args[0].equals("--debug");
        if ((!debug && args.length != 1)
                || args[args.length - 1].startsWith("--")) {
            err.println(USAGE);
            return 2;
        }

        String file = args[args.length - 1];
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
            out.flush();
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
