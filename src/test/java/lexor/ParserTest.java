package lexor;

import lexor.nodes.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class ParserTest {

    private ProgramNode parse(String source) {
        List<Token> tokens = new Lexer(source).tokenize();
        return new Parser(tokens).parse();
    }

    // ── Program Structure ─────────────────────────────────────

    @Test
    void testEmptyProgram() {
        ProgramNode program = parse("""
            SCRIPT AREA
            START SCRIPT
            END SCRIPT
            """);
        assertTrue(program.statements.isEmpty());
    }

    // ── Declaration Tests ─────────────────────────────────────

    @Test
    void testSingleDeclaration() {
        ProgramNode program = parse("""
            SCRIPT AREA
            START SCRIPT
            DECLARE INT x
            END SCRIPT
            """);
        assertEquals(1, program.statements.size());
        assertInstanceOf(DeclareNode.class, program.statements.getFirst());

        DeclareNode node = (DeclareNode) program.statements.getFirst();
        assertEquals(ValueType.INT, node.type);
        assertEquals("x",   node.name);
        assertNull(node.value);
    }

    @Test
    void testDeclarationWithInitialValue() {
        ProgramNode program = parse("""
            SCRIPT AREA
            START SCRIPT
            DECLARE INT x=5
            END SCRIPT
            """);
        DeclareNode node = (DeclareNode) program.statements.getFirst();
        assertNotNull(node.value);
        assertInstanceOf(LiteralNode.class, node.value);

        LiteralNode literal = (LiteralNode) node.value;
        assertEquals(5,     literal.value);
        assertEquals(ValueType.INT, literal.type);
    }

    @Test
    void testMultipleDeclarations() {
        ProgramNode program = parse("""
            SCRIPT AREA
            START SCRIPT
            DECLARE INT x, y, z
            END SCRIPT
            """);
        assertEquals(3, program.statements.size());
    }

    // ── Assignment Tests ──────────────────────────────────────

    @Test
    void testSimpleAssignment() {
        ProgramNode program = parse("""
            SCRIPT AREA
            START SCRIPT
            DECLARE INT x
            x = 5
            END SCRIPT
            """);
        AssignNode node = (AssignNode) program.statements.get(1);
        assertEquals("x", node.name);
        assertInstanceOf(LiteralNode.class, node.value);
    }

    @Test
    void testChainedAssignment() {
        ProgramNode program = parse("""
            SCRIPT AREA
            START SCRIPT
            DECLARE INT x, y
            x = y = 4
            END SCRIPT
            """);
        AssignNode outer = (AssignNode) program.statements.get(2);
        assertEquals("x", outer.name);
        assertInstanceOf(AssignNode.class, outer.value);

        AssignNode inner = (AssignNode) outer.value;
        assertEquals("y", inner.name);
    }

    // ── Print Tests ───────────────────────────────────────────

    @Test
    void testPrintSinglePart() {
        ProgramNode program = parse("""
            SCRIPT AREA
            START SCRIPT
            DECLARE INT x
            PRINT: x
            END SCRIPT
            """);
        PrintNode node = (PrintNode) program.statements.get(1);
        assertEquals(1, node.parts.size());
        assertInstanceOf(VariableNode.class, node.parts.getFirst());
    }

    @Test
    void testPrintMultipleParts() {
        ProgramNode program = parse("""
            SCRIPT AREA
            START SCRIPT
            DECLARE INT x, y
            PRINT: x & y
            END SCRIPT
            """);
        PrintNode node = (PrintNode) program.statements.get(2);
        assertEquals(2, node.parts.size());
    }

    // ── If Tests ──────────────────────────────────────────────

    @Test
    void testIfStatement() {
        ProgramNode program = parse("""
            SCRIPT AREA
            START SCRIPT
            DECLARE BOOL b
            IF (b)
            START IF
            END IF
            END SCRIPT
            """);
        assertInstanceOf(IfNode.class, program.statements.get(1));
    }

    @Test
    void testIfElseStatement() {
        ProgramNode program = parse("""
            SCRIPT AREA
            START SCRIPT
            DECLARE BOOL b
            IF (b)
            START IF
            END IF
            ELSE
            START IF
            END IF
            END SCRIPT
            """);
        IfNode node = (IfNode) program.statements.get(1);
        assertNotNull(node.elseBranch);
    }

    // ── Loop Tests ────────────────────────────────────────────

    @Test
    void testForLoop() {
        ProgramNode program = parse("""
            SCRIPT AREA
            START SCRIPT
            DECLARE INT i
            FOR (i=0, i<10, i=i+1)
            START FOR
            END FOR
            END SCRIPT
            """);
        assertInstanceOf(ForNode.class, program.statements.get(1));
    }

    @Test
    void testRepeatWhen() {
        ProgramNode program = parse("""
            SCRIPT AREA
            START SCRIPT
            DECLARE BOOL b
            REPEAT WHEN (b)
            START REPEAT
            END REPEAT
            END SCRIPT
            """);
        assertInstanceOf(RepeatNode.class, program.statements.get(1));
    }

    // ── Error Tests ───────────────────────────────────────────

    @Test
    void testMissingEndScriptThrows() {
        assertThrows(LexorException.class, () -> parse("""
            SCRIPT AREA
            START SCRIPT
            DECLARE INT x
            """));
    }
}
