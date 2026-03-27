package org.lexor;
import java.util.List;

public class Main {
    public static void main(String[] args) {

        // sample code to test if Lexer works adhfiubwasgowruie
        String source = """
                SCRIPT AREA
                START SCRIPT
                DECLARE INT x, y, z=5
                DECLARE CHAR a_1='n'
                PRINT: x & z & $ & a_1
                END SCRIPT
                """;

        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.tokenize();

        for (Token t : tokens) {
            System.out.println(t);
        }
    }
}