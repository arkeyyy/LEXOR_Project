package lexor;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class LexerTest {

    private List<Token> tokenize(String source) {
        return new Lexer(source).tokenize();
    }

    // ── Keyword Tests ─────────────────────────────────────────

    @Test
    void testScriptArea() {
        List<Token> tokens = tokenize("SCRIPT AREA");
        assertEquals(TokenType.SCRIPT_AREA, tokens.getFirst().type);
        assertEquals("SCRIPT AREA", tokens.getFirst().value);
    }

    @Test
    void testStartScript() {
        List<Token> tokens = tokenize("START SCRIPT");
        assertEquals(TokenType.START_SCRIPT, tokens.getFirst().type);
    }

    @Test
    void testEndScript() {
        List<Token> tokens = tokenize("END SCRIPT");
        assertEquals(TokenType.END_SCRIPT, tokens.getFirst().type);
    }

    @Test
    void testDeclareKeyword() {
        List<Token> tokens = tokenize("DECLARE");
        assertEquals(TokenType.DECLARE, tokens.getFirst().type);
    }

    // ── Literal Tests ─────────────────────────────────────────

    @Test
    void testIntLiteral() {
        List<Token> tokens = tokenize("42");
        assertEquals(TokenType.INT_LITERAL, tokens.getFirst().type);
        assertEquals("42", tokens.getFirst().value);
    }

    @Test
    void testFloatLiteral() {
        List<Token> tokens = tokenize("3.14");
        assertEquals(TokenType.FLOAT_LITERAL, tokens.getFirst().type);
        assertEquals("3.14", tokens.getFirst().value);
    }

    @Test
    void testCharLiteral() {
        List<Token> tokens = tokenize("'c'");
        assertEquals(TokenType.CHAR_LITERAL, tokens.getFirst().type);
        assertEquals("c", tokens.getFirst().value);
    }

    @Test
    void testBoolLiteralTrue() {
        List<Token> tokens = tokenize("\"TRUE\"");
        assertEquals(TokenType.BOOL_LITERAL, tokens.getFirst().type);
        assertEquals("TRUE", tokens.getFirst().value);
    }

    @Test
    void testBoolLiteralFalse() {
        List<Token> tokens = tokenize("\"FALSE\"");
        assertEquals(TokenType.BOOL_LITERAL, tokens.getFirst().type);
        assertEquals("FALSE", tokens.getFirst().value);
    }

    @Test
    void testStringLiteral() {
        List<Token> tokens = tokenize("\"hello\"");
        assertEquals(TokenType.STRING_LITERAL, tokens.getFirst().type);
        assertEquals("hello", tokens.getFirst().value);
    }

    // ── Operator Tests ────────────────────────────────────────

    @Test
    void testArithmeticOperators() {
        List<Token> tokens = tokenize("+ - * / %");
        assertEquals(TokenType.PLUS,     tokens.get(0).type);
        assertEquals(TokenType.MINUS,    tokens.get(1).type);
        assertEquals(TokenType.MULTIPLY, tokens.get(2).type);
        assertEquals(TokenType.DIVIDE,   tokens.get(3).type);
        assertEquals(TokenType.MODULO,   tokens.get(4).type);
    }

    @Test
    void testComparisonOperators() {
        List<Token> tokens = tokenize("> < >= <= == <>");
        assertEquals(TokenType.GREATER,    tokens.get(0).type);
        assertEquals(TokenType.LESS,       tokens.get(1).type);
        assertEquals(TokenType.GREATER_EQ, tokens.get(2).type);
        assertEquals(TokenType.LESS_EQ,    tokens.get(3).type);
        assertEquals(TokenType.EQUAL,      tokens.get(4).type);
        assertEquals(TokenType.NOT_EQUAL,  tokens.get(5).type);
    }

    @Test
    void testLogicalOperators() {
        List<Token> tokens = tokenize("AND OR NOT");
        assertEquals(TokenType.AND, tokens.get(0).type);
        assertEquals(TokenType.OR,  tokens.get(1).type);
        assertEquals(TokenType.NOT, tokens.get(2).type);
    }

    // ── Symbol Tests ──────────────────────────────────────────

    @Test
    void testSymbols() {
        List<Token> tokens = tokenize("& $ : ,");
        assertEquals(TokenType.AMPERSAND, tokens.get(0).type);
        assertEquals(TokenType.DOLLAR,    tokens.get(1).type);
        assertEquals(TokenType.COLON,     tokens.get(2).type);
        assertEquals(TokenType.COMMA,     tokens.get(3).type);
    }

    @Test
    void testEscapeCode() {
        List<Token> tokens = tokenize("[#]");
        assertEquals(TokenType.ESCAPE_CODE, tokens.getFirst().type);
        assertEquals("#", tokens.getFirst().value);
    }

    // ── Comment Tests ─────────────────────────────────────────

    @Test
    void testCommentIsSkipped() {
        List<Token> tokens = tokenize("%% this is a comment\nDECLARE");
        assertEquals(TokenType.NEWLINE, tokens.getFirst().type);
        assertEquals(TokenType.DECLARE, tokens.get(1).type);
    }

    // ── Identifier Tests ──────────────────────────────────────

    @Test
    void testIdentifier() {
        List<Token> tokens = tokenize("myVar");
        assertEquals(TokenType.IDENTIFIER, tokens.getFirst().type);
        assertEquals("myVar", tokens.getFirst().value);
    }

    @Test
    void testIdentifierWithUnderscore() {
        List<Token> tokens = tokenize("a_1");
        assertEquals(TokenType.IDENTIFIER, tokens.getFirst().type);
        assertEquals("a_1", tokens.getFirst().value);
    }

    // ── Line Number Tests ─────────────────────────────────────

    @Test
    void testLineNumberTracking() {
        List<Token> tokens = tokenize("DECLARE\nPRINT");
        assertEquals(1, tokens.get(0).line);
        assertEquals(TokenType.NEWLINE, tokens.get(1).type);
        assertEquals(1, tokens.get(1).line);
        assertEquals(2, tokens.get(2).line);
    }

    // ── EOF Tests ─────────────────────────────────────────────

    @Test
    void testEOFAlwaysLast() {
        List<Token> tokens = tokenize("DECLARE INT x");
        assertEquals(TokenType.EOF, tokens.getLast().type);
    }

    // ── Error Tests ───────────────────────────────────────────

    @Test
    void testUnknownCharacterThrows() {
        assertThrows(LexorException.class, () -> tokenize("@"));
    }

    @Test
    void testUnterminatedCharLiteralThrows() {
        assertThrows(LexorException.class, () -> tokenize("'ab'"));
    }
}
