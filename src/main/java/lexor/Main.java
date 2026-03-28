package lexor;
import lexor.nodes.Node;
import lexor.nodes.ProgramNode;

import java.util.List;

public class Main {
    public static void main(String[] args) {

        // sample code to test if the Phases work adhfiubwasgowruie
        String source = """
                SCRIPT AREA
                START SCRIPT
                DECLARE INT x, y, z=5
                DECLARE CHAR a_1='n'
                PRINT: x & z & $ & a_1
                END SCRIPT
                """;

        // Lexer
        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.tokenize();

        for (Token t : tokens) {
            System.out.println(t);
        }
        System.out.println();

        // Parser
        Parser parser = new Parser(tokens);
        ProgramNode program = parser.parse();

        System.out.println("Parsed " + program.statements.size() + " statements successfully!");
        for (Node node : program.statements) {
            System.out.println(node.getClass().getSimpleName());
        }
    }
}