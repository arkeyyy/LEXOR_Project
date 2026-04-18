package lexor;

import lexor.nodes.ProgramNode;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class SemanticAnalyzerTest {

    private SemanticAnalyzer analyze(String source) {
        List<Token> tokens = new Lexer(source).tokenize();
        ProgramNode program = new Parser(tokens).parse();
        SemanticAnalyzer analyzer = new SemanticAnalyzer();
        analyzer.analyze(program);
        return analyzer;
    }

    private void assertSemanticError(String source) {
        assertThrows(LexorException.class, () -> analyze(source));
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
            """);
    }

    @Test
    void testTypeMismatchInAssignment() {
        assertSemanticError("""
            SCRIPT AREA
            START SCRIPT
            DECLARE INT x
            x = "TRUE"
            END SCRIPT
            """);
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
            """);
    }

    // ── Undeclared Variable Errors ────────────────────────────

    @Test
    void testUndeclaredVariableInAssignment() {
        assertSemanticError("""
            SCRIPT AREA
            START SCRIPT
            x = 5
            END SCRIPT
            """);
    }

    @Test
    void testUndeclaredVariableInPrint() {
        assertSemanticError("""
            SCRIPT AREA
            START SCRIPT
            PRINT: x
            END SCRIPT
            """);
    }

    @Test
    void testUndeclaredVariableInScan() {
        assertSemanticError("""
            SCRIPT AREA
            START SCRIPT
            SCAN: x
            END SCRIPT
            """);
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
            """);
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
        assertEquals("INT", analyzer.getSymbolTable().getType("x"));
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
}