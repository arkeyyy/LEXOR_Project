package lexor;
import java.util.List;

import lexor.nodes.Node;
import lexor.nodes.ProgramNode;

public class Main {
    public static void main(String[] args) {

        // sample code to test if the Phases work
        String source = """
                SCRIPT AREA
                        START SCRIPT
                            DECLARE INT a, b, c, d
                
                            a = 5
                            b = 5
                
                            c = ++a
                            PRINT: "pre-increment a: " & a & $
                            PRINT: "c = ++a:         " & c & $ & $
                
                            d = b++
                            PRINT: "post-increment b: " & b & $
                            PRINT: "d = b++:          " & d & $ & $
                
                            ++a
                            b--
                            PRINT: "standalone ++a: " & a & $
                            PRINT: "standalone b--: " & b & $
                        END SCRIPT
        """;

        try {
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
            System.out.println();

            // Semantic Analysis
            SemanticAnalyzer analyzer = new SemanticAnalyzer();
            analyzer.analyze(program);
            System.out.println("Semantic analysis passed. No errors found\n\n\n");

            // Interpreter
            Interpreter interpreter = new Interpreter(analyzer.getSymbolTable());
            interpreter.execute(program);

        } catch (Exception e) {
            System.err.println(e.getMessage());
            // e.printStackTrace();
        }
    }
}