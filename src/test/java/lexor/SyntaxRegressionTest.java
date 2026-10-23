package lexor;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import lexor.nodes.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SyntaxRegressionTest {
    private ProgramNode parse(String source) {
        return new Parser(new Lexer(source).tokenize()).parse();
    }

    private ProgramNode body(String source) {
        return parse("SCRIPT AREA\nSTART SCRIPT\n" + source + "\nEND SCRIPT");
    }

    private LexorException syntaxError(String source) {
        LexorException error = assertThrows(LexorException.class, () -> body(source));
        assertEquals(LexorException.Phase.PARSER, error.getPhase());
        assertTrue(error.getLine() > 0, error.getMessage());
        return error;
    }

    @ParameterizedTest
    @CsvSource({
            "SCRIPT AREA,SCRIPT_AREA", "START SCRIPT,START_SCRIPT", "END SCRIPT,END_SCRIPT",
            "START IF,START_IF", "END IF,END_IF", "ELSE IF,ELSE_IF",
            "START FOR,START_FOR", "END FOR,END_FOR", "REPEAT WHEN,REPEAT_WHEN",
            "START REPEAT,START_REPEAT", "END REPEAT,END_REPEAT"
    })
    void compoundKeywordsAllowHorizontalWhitespace(String source, String type) {
        List<Token> tokens = new Lexer(source.replace(" ", " \t  ")).tokenize();
        assertEquals(TokenType.valueOf(type), tokens.getFirst().type);
        assertEquals(source, tokens.getFirst().value);
        assertEquals(2, tokens.size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"SCRIPT AREA1", "END SCRIPT1", "START IF_value",
            "REPEAT WHEN_", "ELSE IF1"})
    void compoundKeywordsRequireCompleteIdentifiers(String source) {
        List<Token> tokens = new Lexer(source).tokenize();
        String[] words = source.split(" ");
        assertEquals(TokenType.valueOf(words[0]), tokens.getFirst().type);
        assertEquals(TokenType.IDENTIFIER, tokens.get(1).type);
        assertEquals(words[1], tokens.get(1).value);
        assertEquals(3, tokens.size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"SCRIPT", "AREA", "START", "END", "REPEAT", "WHEN",
            "DECLARE", "PRINT", "SCAN", "IF", "ELSE", "FOR", "AND", "OR", "NOT",
            "INT", "FLOAT", "CHAR", "BOOL"})
    void reservedWordsCannotNameVariables(String name) {
        assertEquals(3, syntaxError("DECLARE INT " + name + "=1").getLine());
    }

    @Test
    void identifiersRemainCaseSensitiveAndAllowKeywordPrefixes() {
        ProgramNode program = body("DECLARE INT Script, script, AREA1, IF_value, _value, a_1");
        assertEquals(6, program.statements.size());
        assertEquals("Script", ((DeclareNode) program.statements.get(0)).name);
        assertEquals("script", ((DeclareNode) program.statements.get(1)).name);
    }

    @ParameterizedTest
    @ValueSource(strings = {"'\n'", "'\r'", "\"\n\"", "\"\r\"", "[\n]", "[\r]",
            "''", "'ab'", "\"unterminated", "[]", "[abc]", "]"})
    void malformedLiteralsProduceLexicalErrors(String source) {
        LexorException error = assertThrows(LexorException.class, () -> new Lexer(source).tokenize());
        assertEquals(LexorException.Phase.LEXER, error.getPhase());
        assertEquals(1, error.getLine());
    }

    @Test
    void escapeCodesPreserveTheirEnclosedSymbol() {
        List<Token> tokens = new Lexer("[[] []] [#] [$] [&]").tokenize();
        assertEquals(List.of("[", "]", "#", "$", "&"),
                tokens.subList(0, 5).stream().map(token -> token.value).toList());
        assertTrue(tokens.subList(0, 5).stream().allMatch(token -> token.type == TokenType.ESCAPE_CODE));
    }

    @Test
    void allLineEndingsProduceOneNewlineAndCorrectPositions() {
        List<Token> tokens = new Lexer("a\nb\r\nc\rd").tokenize();
        for (int index = 0; index < 4; index++) {
            assertEquals(TokenType.IDENTIFIER, tokens.get(index * 2).type);
            assertEquals(index + 1, tokens.get(index * 2).line);
            if (index < 3) {
                assertEquals(TokenType.NEWLINE, tokens.get(index * 2 + 1).type);
                assertEquals(index + 1, tokens.get(index * 2 + 1).line);
            }
        }
        assertEquals(4, tokens.getLast().line);
    }

    @Test
    void commentsDoNotConsumeLineEndingsOrQuotedPercentSigns() {
        List<Token> tokens = new Lexer("PRINT: \"%% literal\" & '%' %% comment\rPRINT: 1").tokenize();
        assertEquals("%% literal", tokens.get(2).value);
        assertEquals("%", tokens.get(4).value);
        assertEquals(TokenType.NEWLINE, tokens.get(5).type);
        assertEquals(TokenType.PRINT, tokens.get(6).type);
        assertEquals(2, tokens.get(6).line);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\n", "\r\n", "\r"})
    void acceptsBlankLinesCommentsAndEofAfterEndScript(String newline) {
        String source = "\n%% before\nSCRIPT\tAREA %% header\n\nSTART SCRIPT\n"
                + "DECLARE INT x=1 %% declaration\n\nPRINT: x\nEND SCRIPT %% end";
        ProgramNode program = parse(source.replace("\n", newline));
        assertEquals(3, program.line);
        assertEquals(2, program.statements.size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PRINT: 1 PRINT: 2", "DECLARE INT x=1 x=2",
            "DECLARE INT x=1 +\n2", "PRINT: (1 +\n2)", "PRINT:\n1",
            "DECLARE INT a=1,\nb=2", "IF (\"TRUE\") START IF\nEND IF",
            "FOR (i=0, i<1,\ni++)\nSTART FOR\nEND FOR",
            "PRINT: 1\nDECLARE INT late",
            "IF (\"TRUE\")\nSTART IF\nDECLARE INT local\nEND IF"})
    void enforcesStatementAndDeclarationBoundaries(String source) {
        syntaxError(source);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SCRIPT AREA START SCRIPT\nEND SCRIPT",
            "SCRIPT AREA\nSTART SCRIPT END SCRIPT", "SCRIPT\nAREA\nSTART SCRIPT\nEND SCRIPT",
            "SCRIPT AREA\nSTART SCRIPT\nEND SCRIPT PRINT: 1",
            "SCRIPT AREA\nSTART SCRIPT\nEND SCRIPT\nPRINT: 1"})
    void enforcesHeaderAndFinalMarkerBoundaries(String source) {
        LexorException error = assertThrows(LexorException.class, () -> parse(source));
        assertEquals(LexorException.Phase.PARSER, error.getPhase());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DECLARE BANANA x", "DECLARE 123 x", "DECLARE PRINT x",
            "DECLARE", "DECLARE INT", "DECLARE FLOAT =", "DECLARE INT x,"})
    void rejectsInvalidAndIncompleteDeclarations(String declaration) {
        syntaxError(declaration);
    }

    @Test
    void declarationsSupportExactlyTheFourLanguageTypes() {
        ProgramNode program = body("DECLARE INT i\nDECLARE FLOAT f\nDECLARE CHAR c\nDECLARE BOOL b");
        assertEquals(List.of(ValueType.INT, ValueType.FLOAT, ValueType.CHAR, ValueType.BOOL),
                program.statements.stream().map(node -> ((DeclareNode) node).type).toList());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-2147483648", "-0002147483648", "-0000000000000000002147483648"})
    void acceptsMinimumIntWithLeadingZeros(String literal) {
        DeclareNode declaration = (DeclareNode) body("DECLARE INT x=" + literal).statements.getFirst();
        LiteralNode value = assertInstanceOf(LiteralNode.class, declaration.value);
        assertEquals(Integer.MIN_VALUE, value.value);
        assertEquals(ValueType.INT, value.type);
        assertEquals(3, value.line);
    }

    @Test
    void acceptsMaximumIntAndLargestFiniteFloat() {
        ProgramNode program = body("DECLARE INT x=2147483647\n"
                + "DECLARE FLOAT f=340282346638528859811704183484516925440.0");
        assertEquals(Integer.MAX_VALUE, ((LiteralNode) ((DeclareNode) program.statements.get(0)).value).value);
        assertEquals(Float.MAX_VALUE, ((LiteralNode) ((DeclareNode) program.statements.get(1)).value).value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2147483648", "+2147483648", "-2147483649", "-(2147483648)",
            "999999999999999999999999999999999999999999999999999999"})
    void rejectsOutOfRangeIntWithoutJavaExceptions(String literal) {
        LexorException error = syntaxError("DECLARE INT x=" + literal);
        assertEquals(3, error.getLine());
        assertTrue(error.getMessage().contains("INT literal out of range"));
    }

    @Test
    void rejectsNonFiniteFloatLiteral() {
        LexorException error = syntaxError("DECLARE FLOAT f=" + "9".repeat(100) + ".0");
        assertEquals(3, error.getLine());
        assertTrue(error.getMessage().contains("finite"));
    }

    @Test
    void everyExpressionAndPrintPartHasItsSourceLine() {
        ProgramNode program = body("DECLARE INT x\n\n%% comment\nPRINT: -(1+x) & $ & [#]");
        PrintNode print = (PrintNode) program.statements.get(1);
        assertEquals(6, print.line);
        UnaryOpNode unary = assertInstanceOf(UnaryOpNode.class, print.parts.getFirst());
        BinaryOpNode binary = assertInstanceOf(BinaryOpNode.class, unary.operand);
        for (Node node : List.of(unary, binary, binary.left, binary.right,
                print.parts.get(1), print.parts.get(2))) {
            assertEquals(6, node.line);
        }
        assertEquals(1, program.line);
        assertEquals(3, program.statements.getFirst().line);
    }

    @Test
    void preservesOperatorPrecedenceAndAssociativity() {
        PrintNode print = (PrintNode) body(
                "PRINT: 1 + 2 * 3 < 8 AND NOT \"FALSE\" OR \"FALSE\"").statements.getFirst();
        BinaryOpNode or = assertInstanceOf(BinaryOpNode.class, print.parts.getFirst());
        assertEquals("OR", or.operator);
        BinaryOpNode and = assertInstanceOf(BinaryOpNode.class, or.left);
        assertEquals("AND", and.operator);
        assertEquals("NOT", ((UnaryOpNode) and.right).operator);
        BinaryOpNode comparison = (BinaryOpNode) and.left;
        assertEquals("<", comparison.operator);
        BinaryOpNode addition = (BinaryOpNode) comparison.left;
        assertEquals("+", addition.operator);
        assertEquals("*", ((BinaryOpNode) addition.right).operator);

        PrintNode subtraction = (PrintNode) body("PRINT: 9-3-1").statements.getFirst();
        BinaryOpNode outer = (BinaryOpNode) subtraction.parts.getFirst();
        assertEquals("-", ((BinaryOpNode) outer.left).operator);
        assertEquals(1, ((LiteralNode) outer.right).value);
    }

    @Test
    void parsesAlternativesNestedLoopsAndIncrementUpdates() {
        ProgramNode program = body("""
                DECLARE INT i
                IF (i==0)
                START IF
                FOR (i=0, i<2, ++i)
                START FOR
                REPEAT WHEN (i<1)
                START REPEAT
                i++
                END REPEAT
                END FOR
                END IF
                ELSE IF (i==1)
                START IF
                --i
                END IF
                ELSE
                START IF
                i--
                END IF
                """.stripTrailing());
        IfNode conditional = (IfNode) program.statements.get(1);
        assertEquals(1, conditional.elseIfBranches.size());
        assertNotNull(conditional.elseBranch);
        ForNode loop = assertInstanceOf(ForNode.class, conditional.thenBranch.getFirst());
        assertTrue(((IncDecNode) loop.update).isPrefix);
        RepeatNode repeat = assertInstanceOf(RepeatNode.class, loop.body.getFirst());
        assertFalse(((IncDecNode) repeat.body.getFirst()).isPrefix);
    }

    @Test
    void everyTruncatedNestedProgramProducesALanguageError() {
        String source = """
                SCRIPT AREA
                START SCRIPT
                DECLARE INT i=0
                IF (i==0)
                START IF
                FOR (i=0, i<1, i++)
                START FOR
                PRINT: "hello" & [#]
                END FOR
                END IF
                END SCRIPT""";
        assertDoesNotThrow(() -> parse(source));
        for (int length = 0; length < source.length(); length++) {
            String prefix = source.substring(0, length);
            LexorException error = assertThrows(LexorException.class, () -> parse(prefix),
                    "Truncated source length " + length);
            assertTrue(error.getLine() > 0, error.getMessage());
        }
    }
}
