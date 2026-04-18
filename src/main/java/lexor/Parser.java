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
            throw new LexorException(LexorException.Phase.PARSER, current().line, "Expected " + type + " but got '" + current().value + "'."
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
        Token t = expect(TokenType.DECLARE);
        String type = consume().value;

        List<Node> nodes = new ArrayList<>();

        do {
            String name = expect(TokenType.IDENTIFIER).value;
            Node value = null;
            if (match(TokenType.ASSIGN)) {
                value = parseExpression();
            }
            DeclareNode node = new DeclareNode(type, name, value);
            node.line = t.line;
            nodes.add(node);
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
            default -> throw new LexorException(
                    LexorException.Phase.PARSER, current().line, "Unexpected token '" + current().value + "'."
            );
        };
    }

    // Assignment /////////////////////////////////////////////////////////////////////////////////////

    // x = 5   or   x = y = 4  (chained assignment)
    private Node parseAssignment() {
        Token t = expect(TokenType.IDENTIFIER);
        expect(TokenType.ASSIGN);

        AssignNode node;

        if (check(TokenType.IDENTIFIER) && peek(1).type == TokenType.ASSIGN) {
            Node right = parseAssignment();
            node = new AssignNode(t.value, right);
        } else {
            Node value = parseExpression();
            node = new AssignNode(t.value, value);
        }

        node.line = t.line;
        return node;
    }

    // Print /////////////////////////////////////////////////////////////////////////////////////////////

    // PRINT: x & t & z & $ & a_1 & [#] & "last"
    private Node parsePrint() {
        Token t = expect(TokenType.PRINT);
        expect(TokenType.COLON);

        List<Node> parts = new ArrayList<>();
        parts.add(parsePrintPart());

        while (match(TokenType.AMPERSAND)) {
            parts.add(parsePrintPart());
        }

        PrintNode node = new PrintNode(parts);
        node.line = t.line;
        return node;
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
        Token t = expect(TokenType.SCAN);
        expect(TokenType.COLON);

        List<String> vars = new ArrayList<>();
        vars.add(expect(TokenType.IDENTIFIER).value);
        while (match(TokenType.COMMA)) {
            vars.add(expect(TokenType.IDENTIFIER).value);
        }

        ScanNode node = new ScanNode(vars);
        node.line = t.line;
        return node;
    }

    // If ////////////////////////////////////////////////////////////////////////////////////////////////

    private Node parseIf() {
        Token t = expect(TokenType.IF);
        expect(TokenType.LPAREN);
        Node condition = parseExpression();
        expect(TokenType.RPAREN);
        expect(TokenType.START_IF);

        List<Node> thenBranch = new ArrayList<>();
        while (!check(TokenType.END_IF) && !check(TokenType.ELSE)
                && !check(TokenType.ELSE_IF) && !check(TokenType.EOF)) {
            thenBranch.add(parseStatement());
        }
        expect(TokenType.END_IF);

        List<IfNode> elseIfBranches = new ArrayList<>();
        List<Node>   elseBranch     = null;

        while (check(TokenType.ELSE_IF)) {
            Token elseIfToken = consume();
            expect(TokenType.LPAREN);
            Node elseIfCond = parseExpression();
            expect(TokenType.RPAREN);
            expect(TokenType.START_IF);

            List<Node> elseIfBody = new ArrayList<>();
            while (!check(TokenType.END_IF) && !check(TokenType.ELSE)
                    && !check(TokenType.ELSE_IF) && !check(TokenType.EOF)) {
                elseIfBody.add(parseStatement());
            }
            expect(TokenType.END_IF);

            IfNode elseIfNode = new IfNode(elseIfCond, elseIfBody, new ArrayList<>(), null);
            elseIfNode.line = elseIfToken.line;
            elseIfBranches.add(elseIfNode);
        }

        if (match(TokenType.ELSE)) {
            expect(TokenType.START_IF);
            elseBranch = new ArrayList<>();
            while (!check(TokenType.END_IF) && !check(TokenType.EOF)) {
                elseBranch.add(parseStatement());
            }
            expect(TokenType.END_IF);
        }

        IfNode node = new IfNode(condition, thenBranch, elseIfBranches, elseBranch);
        node.line = t.line;
        return node;
    }

    // For loop /////////////////////////////////////////////////////////////////////////////////////

    //                                     FOR (init, cond, update)
    private Node parseFor() {
        Token t = expect(TokenType.FOR);
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

        ForNode node = new ForNode(init, condition, update, body);
        node.line = t.line;
        return node;
    }

    // Repeat ///////////////////////////////////////////////////////////////////////////////////////////

    //                                     REPEAT WHEN (<BOOL expression> or condition)
    private Node parseRepeat() {
        Token t = expect(TokenType.REPEAT_WHEN);
        expect(TokenType.LPAREN);
        Node condition = parseExpression();
        expect(TokenType.RPAREN);
        expect(TokenType.START_REPEAT);

        List<Node> body = new ArrayList<>();
        while (!check(TokenType.END_REPEAT) && !check(TokenType.EOF)) {
            body.add(parseStatement());
        }
        expect(TokenType.END_REPEAT);

        RepeatNode node = new RepeatNode(condition, body);
        node.line = t.line;
        return node;
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
            default -> throw new LexorException(
                    LexorException.Phase.PARSER, t.line, "Unexpected token '" + t.value + "'."
            );
        }
    }
}
