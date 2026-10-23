package lexor;

import lexor.nodes.DeclareNode;
import lexor.nodes.ProgramNode;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

public class SemanticAnalyzerTest {

    private SemanticAnalyzer analyze(String source) {
        List<Token> tokens = new Lexer(source).tokenize();
        ProgramNode program = new Parser(tokens).parse();
        SemanticAnalyzer analyzer = new SemanticAnalyzer();
        analyzer.analyze(program);
        return analyzer;
    }

    private void assertSemanticError(String source, int line, String message) {
        LexorException error = assertThrows(LexorException.class, () -> analyze(source));
        assertEquals(LexorException.Phase.SEMANTIC, error.getPhase());
        assertEquals(line, error.getLine());
        assertTrue(error.getMessage().contains(message), error.getMessage());
    }

    private String program(String body) {
        return "SCRIPT AREA\nSTART SCRIPT\n" + body + "\nEND SCRIPT\n";
    }

    // ── Valid Programs ────────────────────────────────────────

    @Test
    void testValidDeclaration() {
        assertDoesNotThrow(() -> analyze("""
            SCRIPT AREA
            START SCRIPT
            DECLARE INT x
            END SCRIPT
            """));
    }

    @Test
    void testValidDeclarationWithInit() {
        assertDoesNotThrow(() -> analyze("""
            SCRIPT AREA
            START SCRIPT
            DECLARE INT x=5
            END SCRIPT
            """));
    }

    @Test
    void testIntToFloatPromotion() {
        assertDoesNotThrow(() -> analyze("""
            SCRIPT AREA
            START SCRIPT
            DECLARE FLOAT f=5
            END SCRIPT
            """));
    }

    @Test
    void testValidAssignment() {
        assertDoesNotThrow(() -> analyze("""
            SCRIPT AREA
            START SCRIPT
            DECLARE INT x
            x = 10
            END SCRIPT
            """));
    }

    @Test
    void testValidIfCondition() {
        assertDoesNotThrow(() -> analyze("""
            SCRIPT AREA
            START SCRIPT
            DECLARE BOOL b
            IF (b)
            START IF
            END IF
            END SCRIPT
            """));
    }

    // ── Type Mismatch Errors ──────────────────────────────────

    @Test
    void testTypeMismatchInDeclaration() {
        assertSemanticError("""
            SCRIPT AREA
            START SCRIPT
            DECLARE INT x="TRUE"
            END SCRIPT
            """, 3, "type mismatch");
    }

    @Test
    void testTypeMismatchInAssignment() {
        assertSemanticError("""
            SCRIPT AREA
            START SCRIPT
            DECLARE INT x
            x = "TRUE"
            END SCRIPT
            """, 4, "type mismatch");
    }

    @Test
    void testNonBoolIfCondition() {
        assertSemanticError("""
            SCRIPT AREA
            START SCRIPT
            DECLARE INT x
            IF (x)
            START IF
            END IF
            END SCRIPT
            """, 4, "IF condition must be BOOL");
    }

    // ── Undeclared Variable Errors ────────────────────────────

    @Test
    void testUndeclaredVariableInAssignment() {
        assertSemanticError("""
            SCRIPT AREA
            START SCRIPT
            x = 5
            END SCRIPT
            """, 3, "Undeclared variable 'x'");
    }

    @Test
    void testUndeclaredVariableInPrint() {
        assertSemanticError("""
            SCRIPT AREA
            START SCRIPT
            PRINT: x
            END SCRIPT
            """, 3, "Undeclared variable 'x'");
    }

    @Test
    void testUndeclaredVariableInScan() {
        assertSemanticError("""
            SCRIPT AREA
            START SCRIPT
            SCAN: x
            END SCRIPT
            """, 3, "Undeclared variable 'x'");
    }

    // ── Redeclaration Error ───────────────────────────────────

    @Test
    void testRedeclarationThrows() {
        assertSemanticError("""
            SCRIPT AREA
            START SCRIPT
            DECLARE INT x
            DECLARE INT x
            END SCRIPT
            """, 4, "already declared");
    }

    // ── Symbol Table Tests ────────────────────────────────────

    @Test
    void testSymbolTableHasCorrectType() {
        SemanticAnalyzer analyzer = analyze("""
            SCRIPT AREA
            START SCRIPT
            DECLARE INT x
            END SCRIPT
            """);
        assertEquals(ValueType.INT, analyzer.getSymbolTable().getType("x"));
    }

    @Test
    void testSymbolTableHasAllDeclaredVariables() {
        SemanticAnalyzer analyzer = analyze("""
            SCRIPT AREA
            START SCRIPT
            DECLARE INT x, y, z
            END SCRIPT
            """);
        assertTrue(analyzer.getSymbolTable().isDeclared("x"));
        assertTrue(analyzer.getSymbolTable().isDeclared("y"));
        assertTrue(analyzer.getSymbolTable().isDeclared("z"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing + 1", "-(missing * 2)", "NOT missing", "++missing", "missing++"})
    void validatesNestedPrintReferences(String expression) {
        assertSemanticError(program("PRINT: " + expression), 3, "Undeclared variable 'missing'");
    }

    @Test
    void rejectsInvalidUnaryAndIncrementExpressionsInPrint() {
        assertSemanticError(program("PRINT: NOT 1"), 3, "NOT requires a BOOL");
        assertSemanticError(program("PRINT: -\"TRUE\""), 3, "Unary +/- requires INT or FLOAT");
        assertSemanticError(program("DECLARE BOOL b\nPRINT: b++"), 4,
                "Increment/decrement requires INT or FLOAT");
        assertSemanticError(program("DECLARE CHAR c='a'\nPRINT: ++c"), 4,
                "Increment/decrement requires INT or FLOAT");
    }

    static Stream<Arguments> invalidArithmetic() {
        return Stream.of("+", "-", "*", "/", "%").flatMap(operator ->
                Stream.of("1", "1.5").flatMap(number ->
                        Stream.of("\"TRUE\"", "'a'", "\"text\"").flatMap(other -> Stream.of(
                                Arguments.of(number + " " + operator + " " + other),
                                Arguments.of(other + " " + operator + " " + number)))));
    }

    @ParameterizedTest
    @MethodSource("invalidArithmetic")
    void rejectsNonnumericArithmeticRegardlessOfPromotion(String expression) {
        assertSemanticError(program("PRINT: " + expression), 3, "requires numeric operands");
    }

    @Test
    void rejectsFloatArithmeticWithBooleanInInitializer() {
        assertSemanticError(program("DECLARE FLOAT f=1.0+\"TRUE\""), 3, "requires numeric operands");
    }

    @ParameterizedTest
    @ValueSource(strings = {"+", "-", "*", "/", "%"})
    void preservesArithmeticInference(String operator) {
        String body = "DECLARE INT i=4 " + operator + " 2\n"
                + "DECLARE FLOAT f=4 " + operator + " 2.0\n"
                + "DECLARE FLOAT g=4.0 " + operator + " 2";
        ProgramNode parsed = new Parser(new Lexer(program(body)).tokenize()).parse();
        SemanticAnalyzer analyzer = new SemanticAnalyzer();
        analyzer.analyze(parsed);
        assertEquals(ValueType.INT, analyzer.inferType(((DeclareNode) parsed.statements.get(0)).value));
        assertEquals(ValueType.FLOAT, analyzer.inferType(((DeclareNode) parsed.statements.get(1)).value));
        assertEquals(ValueType.FLOAT, analyzer.inferType(((DeclareNode) parsed.statements.get(2)).value));
    }

    @ParameterizedTest
    @ValueSource(strings = {">", "<", ">=", "<=", "==", "<>"})
    void acceptsMixedNumericComparisonsInBothOrders(String operator) {
        assertDoesNotThrow(() -> analyze(program("DECLARE BOOL a=1 " + operator + " 1.5\n"
                + "DECLARE BOOL b=1.5 " + operator + " 1")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"==", "<>"})
    void acceptsOnlyMatchingBoolAndCharEquality(String operator) {
        assertDoesNotThrow(() -> analyze(program("DECLARE BOOL a=\"TRUE\" " + operator + " \"FALSE\"\n"
                + "DECLARE BOOL b='a' " + operator + " 'b'")));
        assertSemanticError(program("PRINT: 'a' " + operator + " \"TRUE\""), 3, "Cannot compare");
    }

    @ParameterizedTest
    @ValueSource(strings = {">", "<", ">=", "<="})
    void rejectsBoolAndCharOrdering(String operator) {
        assertSemanticError(program("PRINT: \"TRUE\" " + operator + " \"FALSE\""), 3, "Cannot compare");
        assertSemanticError(program("PRINT: 'a' " + operator + " 'b'"), 3, "Cannot compare");
    }

    @ParameterizedTest
    @ValueSource(strings = {">", "<", ">=", "<=", "==", "<>"})
    void rejectsStringComparisons(String operator) {
        assertSemanticError(program("PRINT: \"a\" " + operator + " \"a\""), 3, "Cannot compare");
    }

    @Test
    void validatesBothSidesOfShortCircuitOperators() {
        assertSemanticError(program("PRINT: \"FALSE\" AND missing"), 3, "Undeclared variable 'missing'");
        assertSemanticError(program("PRINT: \"TRUE\" OR 1"), 3, "AND/OR requires BOOL operands");
        assertSemanticError(program("PRINT: \"FALSE\" AND (1.0+\"TRUE\"==0)"), 3,
                "requires numeric operands");
        assertDoesNotThrow(() -> analyze(program("PRINT: \"FALSE\" AND (1/0==0)")));
    }

    @Test
    void reportsNestedAssignmentReferencesAtTheirSourceLine() {
        assertSemanticError(program("DECLARE INT x\nx=missing+1"), 4, "Undeclared variable 'missing'");
    }

    @Test
    void supportsRightToLeftAssignmentTypesWithoutNarrowing() {
        assertDoesNotThrow(() -> analyze(program("DECLARE FLOAT f\nDECLARE INT i\nf=i=3")));
        assertSemanticError(program("DECLARE FLOAT f\nDECLARE INT i\ni=f=3"), 5, "type mismatch");
    }

    @Test
    void requiresBooleanConditionsInEveryControlStructure() {
        assertSemanticError(program("DECLARE INT i\nFOR (i=0, 1, i++)\nSTART FOR\nEND FOR"), 4,
                "FOR condition must be BOOL");
        assertSemanticError(program("REPEAT WHEN (1)\nSTART REPEAT\nEND REPEAT"), 3,
                "REPEAT WHEN condition must be BOOL");
        assertSemanticError(program("IF (\"FALSE\")\nSTART IF\nEND IF\nELSE IF (1)\nSTART IF\nEND IF"),
                6, "IF condition must be BOOL");
    }

    @Test
    void analyzerStartsWithFreshSymbolsForEachProgram() {
        SemanticAnalyzer analyzer = analyze(program("DECLARE INT x"));
        SymbolTable first = analyzer.getSymbolTable();
        analyzer.analyze(new Parser(new Lexer(program("DECLARE FLOAT x\nDECLARE BOOL y")).tokenize()).parse());
        assertNotSame(first, analyzer.getSymbolTable());
        assertEquals(ValueType.INT, first.getType("x"));
        assertEquals(ValueType.FLOAT, analyzer.getSymbolTable().getType("x"));
        assertFalse(first.isDeclared("y"));
        assertTrue(analyzer.getSymbolTable().isDeclared("y"));
    }

    @Test
    void declarationDefaultsAreTypedAndAnalysisDoesNotExecuteInitializers() {
        SymbolTable symbols = analyze(program("DECLARE INT i=9\nDECLARE FLOAT f=8\n"
                + "DECLARE CHAR c='x'\nDECLARE BOOL b=\"TRUE\"")).getSymbolTable();
        assertEquals(0, symbols.getValue("i"));
        assertEquals(0.0f, symbols.getValue("f"));
        assertEquals('\0', symbols.getValue("c"));
        assertEquals(false, symbols.getValue("b"));
    }
}
