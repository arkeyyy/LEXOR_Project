package lexor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import lexor.nodes.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ComplexityLimitsTest {
    @TempDir Path directory;

    private static String source(String body) {
        return "SCRIPT AREA\nSTART SCRIPT\n" + body + "\nEND SCRIPT\n";
    }

    private static ProgramNode parse(String body) {
        return new Parser(new Lexer(source(body)).tokenize()).parse();
    }

    private static LexorException limit(LexorException.Phase phase, String message, Runnable action) {
        LexorException failure = assertThrows(LexorException.class, action::run);
        assertEquals(phase, failure.getPhase());
        assertTrue(failure.getLine() > 0, failure.getMessage());
        assertTrue(failure.getMessage().contains(message), failure.getMessage());
        return failure;
    }

    @Test
    void parenthesesAccept128AndReject129AtTheSourceLine() {
        assertDoesNotThrow(() -> parse("PRINT: " + "(".repeat(128) + "1" + ")".repeat(128)));
        LexorException failure = limit(LexorException.Phase.PARSER, "nesting exceeds limit of 128",
                () -> parse("PRINT: " + "(".repeat(129) + "1" + ")".repeat(129)));
        assertEquals(3, failure.getLine());
    }

    @ParameterizedTest
    @ValueSource(strings = {"+ ", "- ", "NOT "})
    void unaryOperatorsShareThe128Limit(String operator) {
        String value = operator.startsWith("NOT") ? "\"TRUE\"" : "1";
        assertDoesNotThrow(() -> parse("PRINT: " + operator.repeat(128) + value));
        limit(LexorException.Phase.PARSER, "nesting exceeds limit of 128",
                () -> parse("PRINT: " + operator.repeat(129) + value));
    }

    @Test
    void assignmentChainsAccept128AndReject129() {
        assertDoesNotThrow(() -> parse("DECLARE INT x\n" + "x = ".repeat(128) + "1"));
        limit(LexorException.Phase.PARSER, "nesting exceeds limit of 128",
                () -> parse("DECLARE INT x\n" + "x = ".repeat(129) + "1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"IF", "REPEAT"})
    void nestedControlBlocksAccept128AndReject129(String kind) {
        String opening = kind.equals("IF") ? "IF (\"TRUE\")\nSTART IF\n"
                : "REPEAT WHEN (\"FALSE\")\nSTART REPEAT\n";
        String closing = "END " + kind + "\n";
        ProgramNode program = parse(opening.repeat(128) + "PRINT: 42\n" + closing.repeat(128));
        SemanticAnalyzer analyzer = new SemanticAnalyzer();
        assertDoesNotThrow(() -> analyzer.analyze(program));
        assertDoesNotThrow(() -> new Interpreter(analyzer.getSymbolTable(),
                new ByteArrayInputStream(new byte[0]), new PrintStream(new ByteArrayOutputStream()))
                .execute(program));
        limit(LexorException.Phase.PARSER, "nesting exceeds limit of 128",
                () -> parse(opening.repeat(129) + "PRINT: 42\n" + closing.repeat(129)));
    }

    @Test
    void forHeadersAndNestedBodiesShareTheBudget() {
        String opening = "FOR (x = 0, x < 1, x++)\nSTART FOR\n";
        assertDoesNotThrow(() -> parse("DECLARE INT x\n" + opening.repeat(127)
                + "PRINT: 1\n" + "END FOR\n".repeat(127)));
        limit(LexorException.Phase.PARSER, "nesting exceeds limit of 128",
                () -> parse("DECLARE INT x\n" + opening.repeat(128)
                        + "PRINT: 1\n" + "END FOR\n".repeat(128)));
    }

    @Test
    void mixedNestingUsesOneBudgetAndReleasesItForSiblingStatements() {
        String opening = "DECLARE INT x\n" + "IF (\"TRUE\")\nSTART IF\n".repeat(64)
                + "x = " + "(".repeat(32);
        String closing = "1" + ")".repeat(32) + "\n" + "END IF\n".repeat(64);
        assertDoesNotThrow(() -> parse(opening + "+ ".repeat(31) + closing
                + "PRINT: " + "(".repeat(128) + "1" + ")".repeat(128)
                + "\nPRINT: " + "+ ".repeat(128) + "1"));
        limit(LexorException.Phase.PARSER, "nesting exceeds limit of 128",
                () -> parse(opening + "+ ".repeat(32) + closing));
    }

    @Test
    void flatExpressionAcceptsAstDepth256AndRejects257() {
        ProgramNode program = parse("PRINT: " + "1 + ".repeat(253) + "1");
        SemanticAnalyzer analyzer = new SemanticAnalyzer();
        analyzer.analyze(program);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new Interpreter(analyzer.getSymbolTable(), new ByteArrayInputStream(new byte[0]),
                new PrintStream(output)).execute(program);
        assertEquals("254", output.toString(StandardCharsets.UTF_8));
        assertEquals(3, limit(LexorException.Phase.PARSER, "AST depth exceeds limit of 256",
                () -> parse("PRINT: " + "1 + ".repeat(254) + "1")).getLine());
    }

    private static Node expression(int depth) {
        Node node = new LiteralNode(41, 1, ValueType.INT);
        for (int i = 1; i < depth; i++) node = new UnaryOpNode(41, "+", node);
        return node;
    }

    @Test
    void publicTypeInferenceCountsItsOwnRootAsDepthOne() {
        SemanticAnalyzer analyzer = new SemanticAnalyzer();
        assertEquals(ValueType.INT, analyzer.inferType(expression(256)));
        assertEquals(41, limit(LexorException.Phase.SEMANTIC, "AST depth exceeds limit of 256",
                () -> analyzer.inferType(expression(257))).getLine());
    }

    @Test
    void publicProgramConsumersValidateBeforeAnySideEffects() {
        ProgramNode accepted = new ProgramNode(1,
                List.of(new PrintNode(2, List.of(expression(254)))));
        SemanticAnalyzer analyzer = new SemanticAnalyzer();
        analyzer.analyze(accepted);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Interpreter interpreter = new Interpreter(analyzer.getSymbolTable(),
                new ByteArrayInputStream(new byte[0]), new PrintStream(output));
        interpreter.execute(accepted);
        assertEquals("1", output.toString(StandardCharsets.UTF_8));

        ProgramNode rejected = new ProgramNode(1, List.of(
                new PrintNode(2, List.of(new LiteralNode(2, "must not print", ValueType.STRING))),
                new PrintNode(3, List.of(expression(255)))));
        limit(LexorException.Phase.SEMANTIC, "AST depth exceeds limit of 256",
                () -> analyzer.analyze(rejected));
        limit(LexorException.Phase.RUNTIME, "AST depth exceeds limit of 256",
                () -> interpreter.execute(rejected));
        assertEquals("1", output.toString(StandardCharsets.UTF_8));
    }

    static Stream<Function<Node, Node>> recursiveChildren() {
        Node literal = new LiteralNode(1, true, ValueType.BOOL);
        return Stream.of(
                node -> new DeclareNode(1, ValueType.INT, "x", node),
                node -> new AssignNode(1, "x", node),
                node -> new BinaryOpNode(1, node, "+", literal),
                node -> new BinaryOpNode(1, literal, "+", node),
                node -> new IfNode(1, node, List.of(), List.of(), null),
                node -> new IfNode(1, literal, List.of(node), List.of(), null),
                node -> new IfNode(1, literal, List.of(), List.of(), List.of(node)),
                node -> new IfNode(1, literal, List.of(), List.of(
                        new IfNode(1, node, List.of(), List.of(), null)), null),
                node -> new ForNode(1, node, literal, literal, List.of()),
                node -> new ForNode(1, literal, node, literal, List.of()),
                node -> new ForNode(1, literal, literal, node, List.of()),
                node -> new ForNode(1, literal, literal, literal, List.of(node)),
                node -> new RepeatNode(1, node, List.of()),
                node -> new RepeatNode(1, literal, List.of(node)));
    }

    @ParameterizedTest
    @MethodSource("recursiveChildren")
    void depthValidationIncludesAllRecursiveBranches(Function<Node, Node> wrap) {
        Node root = wrap.apply(expression(256));
        limit(LexorException.Phase.SEMANTIC, "AST depth exceeds limit of 256",
                () -> new SemanticAnalyzer().inferType(root));
    }

    @ParameterizedTest
    @ValueSource(strings = {"parentheses", "unary", "assignment", "blocks", "flat"})
    void extremeSourcesReturnDiagnosticsThroughMain(String kind) throws Exception {
        String body = switch (kind) {
            case "parentheses" -> "PRINT: " + "(".repeat(10_000) + "1" + ")".repeat(10_000);
            case "unary" -> "PRINT: " + "+ ".repeat(10_000) + "1";
            case "assignment" -> "DECLARE INT x\n" + "x = ".repeat(10_000) + "1";
            case "blocks" -> "IF (\"TRUE\")\nSTART IF\n".repeat(10_000)
                    + "PRINT: 1\n" + "END IF\n".repeat(10_000);
            default -> "PRINT: " + "1 + ".repeat(10_000) + "1";
        };
        Path file = Files.writeString(directory.resolve("complex.lxr"), source(body));
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        assertEquals(1, Main.run(new String[] {file.toString()},
                new ByteArrayInputStream(new byte[0]), new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(error)));
        String diagnostic = error.toString(StandardCharsets.UTF_8);
        assertTrue(diagnostic.contains("SyntaxError at line "), diagnostic);
        assertTrue(diagnostic.contains("exceeds limit"), diagnostic);
        assertFalse(diagnostic.contains("StackOverflowError"), diagnostic);
    }
}
