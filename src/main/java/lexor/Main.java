package lexor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import lexor.nodes.Node;
import lexor.nodes.ProgramNode;

public class Main {
    public static void main(String[] args) {

//         if (args.length == 0) {
//             System.err.println("Usage: java -jar lexor.jar <file.lxr>");
//             System.exit(1);
//         }
//
//         String code;
//         try {
//             code = Files.readString(Path.of(args[0]));
//         } catch (IOException e) {
//             System.err.println("Error: Could not read file '" + args[0] + "'.");
//             System.exit(1);
//             return;
//         }

        // sample code to test if the Phases work adhfiubwasgowruie
       String source = """
                   SCRIPT AREA
                       START SCRIPT
                             DECLARE INT x, y, z=5
                             DECLARE CHAR a_1='n'
                             DECLARE BOOL t = "TRUE"
                             DECLARE FLOAT f = 6.7
                             DECLARE INT end = 10, i = 1
                             f = f + 6
                             x=y=4
                             a_1='c'
                             
                             PRINT: x & t & z & $ & a_1
                             PRINT: f

                             IF (6 < 7)
                             START IF
                                 PRINT: "IF BLOCK"
                             END IF
                             ELSE IF (4 >= 9)
                             START IF
                                 PRINT: "ELSE IF BLOCK (4 >= 9)"
                             END IF
                             ELSE IF (4 <= 4)
                             START IF
                                 PRINT: "ELSE IF BLOCK (4 <= 4)"
                             END IF
                             ELSE
                             START IF
                                 PRINT: "ELSE BLOCK"
                             END IF
                             
                             PRINT: $
                             REPEAT WHEN (i <= end)
                                START REPEAT
                                    PRINT: i & $
                                    i = i + 1
                                END REPEAT
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
            System.out.println("Semantic analysis passed. No errors found\n");

            // Interpreter
            Interpreter interpreter = new Interpreter(analyzer.getSymbolTable());
            interpreter.execute(program);

        } catch (Exception e) {
            System.err.println(e.getMessage());
            // e.printStackTrace();
        }
    }
}