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
            DECLARE BOOL t = "TRUE"
            DECLARE FLOAT f = 6.7
            f = f + 6
            x=y=4
            a_1='c'
            
            PRINT: x & t & z & $ & a_1
            PRINT: f
            
            IF (6 < 7)
            START IF
                PRINT: "HI EVERYONES"
            END IF
            ELSE IF (4 >= 9)
            START IF
                PRINT: "ROCKS CLIMBING TA"
            END IF
            ELSE IF (4 <= 4)
            START IF
                PRINT: "NICE WEATHER INIT?"
            END IF
            ELSE
            START IF
                PRINT: "SUSPENDS KLASE OI GALENOGS"
            END IF
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
        System.out.println();

        // Semantic Analysis
        SemanticAnalyzer analyzer = new SemanticAnalyzer();
        analyzer.analyze(program);
        System.out.println("Semantic analysis passed. No errors found\n");

        // Interpreter
        Interpreter interpreter = new Interpreter(analyzer.getSymbolTable());
        interpreter.execute(program);
    }
}