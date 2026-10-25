package lexor;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import lexor.nodes.LiteralNode;
import lexor.nodes.PrintNode;
import lexor.nodes.ProgramNode;
import org.junit.jupiter.api.Test;

class BomTest {
    @Test
    void leadingBomPreservesSourceLinesAndLiteralContents() {
        List<Token> tokens = new Lexer("\uFEFFSCRIPT AREA\r\nSTART SCRIPT\r\n"
                + "PRINT: \"\uFEFFtext\" & '\uFEFF'\r\nEND SCRIPT\r\n").tokenize();
        assertEquals(TokenType.SCRIPT_AREA, tokens.getFirst().type);
        assertEquals(1, tokens.getFirst().line);
        ProgramNode program = new Parser(tokens).parse();
        PrintNode print = (PrintNode) program.statements.getFirst();
        assertEquals(3, print.line);
        assertEquals("\uFEFFtext", ((LiteralNode) print.parts.get(0)).value);
        assertEquals('\uFEFF', ((LiteralNode) print.parts.get(1)).value);
    }

    @Test
    void consumesOnlyOneBomAndOnlyAtOffsetZero() {
        for (String prefix : List.of("\uFEFF\uFEFF", " \uFEFF", "\n\uFEFF")) {
            LexorException failure = assertThrows(LexorException.class,
                    () -> new Lexer(prefix + "SCRIPT AREA").tokenize());
            assertEquals(LexorException.Phase.LEXER, failure.getPhase());
            assertEquals(prefix.startsWith("\n") ? 2 : 1, failure.getLine());
        }
    }

    @Test
    void bomOnlySourceProducesEofOnFirstLine() {
        Lexer lexer = new Lexer("\uFEFF");
        List<Token> tokens = lexer.tokenize();
        assertEquals(1, tokens.size());
        assertEquals(TokenType.EOF, tokens.getFirst().type);
        assertEquals(1, tokens.getFirst().line);
        assertEquals(tokens, lexer.tokenize());
    }
}
