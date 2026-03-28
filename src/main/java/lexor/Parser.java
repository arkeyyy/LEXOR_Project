package lexor;

import lexor.nodes.*;

import java.util.ArrayList;
import java.util.List;

public class Parser {
    private final List<Token> tokens;
    private int pos = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    // helper functions ///////////////////////////////////////////////////////////////////////////////

    private Token current(){
        return tokens.get(pos);
    }

    private Token consume(){
        return tokens.get(pos++);
    }

    private Token peek(int offset) {
        int index = pos + offset;
        if (index >= tokens.size()) return tokens.get(tokens.size() - 1); // EOF
        return tokens.get(index);
    }

    private Token expect(TokenType type) {
        if (current().type != type) {
            throw new RuntimeException(
                    "Expected " + type + " but got '" + current().value + "' at line " + current().line
            );
        }
        return consume();
    }

    private boolean check(TokenType type) {
        return current().type == type;
    }

    private boolean match(TokenType type) {
        if (check(type)) { consume(); return true; }
        return false;
    }

    // Entry Point //////////////////////////////////////////////////////////////////////////

    public ProgramNode parse() {
        expect(TokenType.SCRIPT_AREA);
        expect(TokenType.START_SCRIPT);

        List<Node> declarations = new ArrayList<>();
        List<Node> statements   = new ArrayList<>();

        // declarations must come first
        while (check(TokenType.DECLARE)) {
            declarations.addAll(parseDeclaration());
        }

        // then executable statements
        while (!check(TokenType.END_SCRIPT) && !check(TokenType.EOF)) {
            statements.add(parseStatement());
        }

        expect(TokenType.END_SCRIPT);

        List<Node> all = new ArrayList<>();
        all.addAll(declarations);
        all.addAll(statements);

        return new ProgramNode(all);
    }

    // Declarations ///////////////////////////////////////////////////////////////////////////

    // DECLARE INT x, y, z=5
    private List<Node> parseDeclaration() {
        expect(TokenType.DECLARE);
        String type = consume().value; // INT, FLOAT, CHAR, BOOL

        List<Node> nodes = new ArrayList<>();

        do {
            String name = expect(TokenType.IDENTIFIER).value;
            Node value = null;
            if (match(TokenType.ASSIGN)) {
                value = parseExpression();
            }
            nodes.add(new DeclareNode(type, name, value));
        } while (match(TokenType.COMMA));

        return nodes;
    }

    // Statements //////////////////////////////////////////////////////////////////////////////////

    private Node parseStatement() {
        return switch (current().type) {
            case IDENTIFIER -> parseAssignment();
            case PRINT      -> parsePrint();
            case SCAN       -> parseScan();
            case IF         -> parseIf();
            case FOR        -> parseFor();
            case REPEAT_WHEN -> parseRepeat();
            default -> throw new RuntimeException(
                    "Unexpected token '" + current().value + "' at line " + current().line
            );
        };
    }

    // Assignment /////////////////////////////////////////////////////////////////////////////////////

    // x = 5   or   x = y = 4  (chained assignment)
    private Node parseAssignment() {
        String name = expect(TokenType.IDENTIFIER).value;
        expect(TokenType.ASSIGN);

        // peek if this is a chained assignment: x = y = 4
        if (check(TokenType.IDENTIFIER) && peek(1).type == TokenType.ASSIGN) {
            Node right = parseAssignment(); // recurse
            return new AssignNode(name, right);
        }

        Node value = parseExpression();
        return new AssignNode(name, value);
    }

    // Print /////////////////////////////////////////////////////////////////////////////////////////////

    // PRINT: x & t & z & $ & a_1 & [#] & "last"
    private Node parsePrint() {
        expect(TokenType.PRINT);
        expect(TokenType.COLON);

        List<Node> parts = new ArrayList<>();
        parts.add(parsePrintPart());

        while (match(TokenType.AMPERSAND)) {
            parts.add(parsePrintPart());
        }

        return new PrintNode(parts);
    }

    private Node parsePrintPart() {
        if (check(TokenType.DOLLAR)) {           // pang newline ang $ bai
            consume();
            return new LiteralNode("\n", "STRING");
        }

        if (check(TokenType.ESCAPE_CODE)) {      // escape codes ang naa sa sod sa "[" og "]" bai
            String code = consume().value;
            return new LiteralNode(resolveEscape(code), "STRING");
        }
        return parseExpression();
    }

    private String resolveEscape(String code) {
        return switch (code) {
            case "[" -> "[";
            case "#" -> "#";
            case "]" -> "]";
            default  -> code;
        };
    }

    // Scan /////////////////////////////////////////////////////////////////////////////////////////////

    // SCAN: x, y
    private Node parseScan() {
        expect(TokenType.SCAN);
        expect(TokenType.COLON);

        List<String> vars = new ArrayList<>();
        vars.add(expect(TokenType.IDENTIFIER).value);
        while (match(TokenType.COMMA)) {
            vars.add(expect(TokenType.IDENTIFIER).value);
        }

        return new ScanNode(vars);
    }

    // If ////////////////////////////////////////////////////////////////////////////////////////////////

    private Node parseIf() {
        expect(TokenType.IF);
        expect(TokenType.LPAREN);
        Node condition = parseExpression();
        expect(TokenType.RPAREN);
        expect(TokenType.START_IF);

        List<Node> thenBranch = new ArrayList<>();
        while (!check(TokenType.END_IF) && !check(TokenType.ELSE) && !check(TokenType.ELSE_IF) && !check(TokenType.EOF)) {
            thenBranch.add(parseStatement());
        }
        expect(TokenType.END_IF);

        List<IfNode> elseIfBranches = new ArrayList<>();
        List<Node>   elseBranch     = null;

        while (check(TokenType.ELSE_IF)) {
            consume();
            expect(TokenType.LPAREN);
            Node elseIfCond = parseExpression();
            expect(TokenType.RPAREN);
            expect(TokenType.START_IF);

            List<Node> elseIfBody = new ArrayList<>();
            while (!check(TokenType.END_IF) && !check(TokenType.ELSE) && !check(TokenType.ELSE_IF) && !check(TokenType.EOF)) {
                elseIfBody.add(parseStatement());
            }
            expect(TokenType.END_IF);
            elseIfBranches.add(new IfNode(elseIfCond, elseIfBody, new ArrayList<>(), null));
        }

        if (match(TokenType.ELSE)) {
            expect(TokenType.START_IF);
            elseBranch = new ArrayList<>();
            while (!check(TokenType.END_IF) && !check(TokenType.EOF)) {
                elseBranch.add(parseStatement());
            }
            expect(TokenType.END_IF);
        }

        return new IfNode(condition, thenBranch, elseIfBranches, elseBranch);
    }

    // For loop /////////////////////////////////////////////////////////////////////////////////////

    //                                     FOR (init, cond, update)
    private Node parseFor() {
        expect(TokenType.FOR);
        expect(TokenType.LPAREN);
        Node init = parseAssignment();
        expect(TokenType.COMMA);
        Node condition = parseExpression();
        expect(TokenType.COMMA);
        Node update = parseAssignment();
        expect(TokenType.RPAREN);
        expect(TokenType.START_FOR);

        List<Node> body = new ArrayList<>();
        while (!check(TokenType.END_FOR) && !check(TokenType.EOF)) {
            body.add(parseStatement());
        }
        expect(TokenType.END_FOR);

        return new ForNode(init, condition, update, body);
    }

    // Repeat ///////////////////////////////////////////////////////////////////////////////////////////

    //                                     REPEAT WHEN (<BOOL expression> or condition)
    private Node parseRepeat() {
        expect(TokenType.REPEAT_WHEN);
        expect(TokenType.LPAREN);
        Node condition = parseExpression();
        expect(TokenType.RPAREN);
        expect(TokenType.START_REPEAT);

        List<Node> body = new ArrayList<>();
        while (!check(TokenType.END_REPEAT) && !check(TokenType.EOF)) {
            body.add(parseStatement());
        }
        expect(TokenType.END_REPEAT);

        return new RepeatNode(condition, body);
    }

    // Expressions (with precedence) ///////////////////////////////////////////////////////////////////////

    private Node parseExpression() {
        return parseOr();
    }

    private Node parseOr() {
        Node left = parseAnd();
        while (check(TokenType.OR)) {
            String op = consume().value;
            left = new BinaryOpNode(left, op, parseAnd());
        }
        return left;
    }

    private Node parseAnd() {
        Node left = parseEquality();
        while (check(TokenType.AND)) {
            String op = consume().value;
            left = new BinaryOpNode(left, op, parseEquality());
        }
        return left;
    }

    private Node parseEquality() {
        Node left = parseComparison();
        while (check(TokenType.EQUAL) || check(TokenType.NOT_EQUAL)) {
            String op = consume().value;
            left = new BinaryOpNode(left, op, parseComparison());
        }
        return left;
    }

    private Node parseComparison() {
        Node left = parseAddSub();
        while (check(TokenType.GREATER) || check(TokenType.LESS) ||
                check(TokenType.GREATER_EQ) || check(TokenType.LESS_EQ)) {
            String op = consume().value;
            left = new BinaryOpNode(left, op, parseAddSub());
        }
        return left;
    }

    private Node parseAddSub() {
        Node left = parseMulDiv();
        while (check(TokenType.PLUS) || check(TokenType.MINUS)) {
            String op = consume().value;
            left = new BinaryOpNode(left, op, parseMulDiv());
        }
        return left;
    }

    private Node parseMulDiv() {
        Node left = parseUnary();
        while (check(TokenType.MULTIPLY) || check(TokenType.DIVIDE) || check(TokenType.MODULO)) {
            String op = consume().value;
            left = new BinaryOpNode(left, op, parseUnary());
        }
        return left;
    }

    private Node parseUnary() {
        if (check(TokenType.MINUS) || check(TokenType.PLUS)) {
            String op = consume().value;
            return new UnaryOpNode(op, parseUnary());
        }
        if (check(TokenType.NOT)) {
            consume();
            return new UnaryOpNode("NOT", parseUnary());
        }
        return parsePrimary();
    }

    private Node parsePrimary() {
        Token t = current();
        switch (t.type) {
            case INT_LITERAL    -> { consume(); return new LiteralNode(Integer.parseInt(t.value),   "INT");    }
            case FLOAT_LITERAL  -> { consume(); return new LiteralNode(Float.parseFloat(t.value),   "FLOAT");  }
            case CHAR_LITERAL   -> { consume(); return new LiteralNode(t.value.charAt(0),           "CHAR");   }
            case BOOL_LITERAL   -> { consume(); return new LiteralNode(t.value.equals("TRUE"),      "BOOL");   }
            case STRING_LITERAL -> { consume(); return new LiteralNode(t.value,                     "STRING"); }
            case IDENTIFIER     -> { consume(); return new VariableNode(t.value); }
            case LPAREN -> {
                consume();
                Node expr = parseExpression();
                expect(TokenType.RPAREN);
                return expr;
            }
            default -> throw new RuntimeException(
                    "Unexpected token '" + t.value + "' at line " + t.line
            );
        }
    }
}
