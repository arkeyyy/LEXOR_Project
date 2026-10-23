package lexor;

import java.util.ArrayList;
import java.util.List;
import lexor.nodes.*;

public class Parser {
    private final List<Token> tokens;
    private int pos;

    public Parser(List<Token> tokens) {
        if (tokens.isEmpty() || tokens.getLast().type != TokenType.EOF) {
            int line = tokens.isEmpty() ? 1 : tokens.getLast().line;
            throw new LexorException(LexorException.Phase.PARSER, line, "Expected EOF token.");
        }
        this.tokens = List.copyOf(tokens);
    }

    private Token current() {
        return tokens.get(Math.min(pos, tokens.size() - 1));
    }

    private Token consume() {
        Token token = current();
        if (token.type != TokenType.EOF) pos++;
        return token;
    }

    private Token peek() {
        return tokens.get(Math.min(pos + 1, tokens.size() - 1));
    }

    private Token expect(TokenType type) {
        if (!check(type)) {
            throw error(current(), "Expected " + type + " but got '" + current().value + "'.");
        }
        return consume();
    }

    private boolean check(TokenType type) {
        return current().type == type;
    }

    private boolean match(TokenType type) {
        if (!check(type)) return false;
        consume();
        return true;
    }

    private void skipNewlines() {
        while (match(TokenType.NEWLINE)) { }
    }

    private void endLine() {
        expect(TokenType.NEWLINE);
        skipNewlines();
    }

    public ProgramNode parse() {
        skipNewlines();
        Token start = expect(TokenType.SCRIPT_AREA);
        endLine();
        expect(TokenType.START_SCRIPT);
        endLine();

        List<Node> statements = new ArrayList<>();
        while (check(TokenType.DECLARE)) statements.addAll(parseDeclaration());
        while (!check(TokenType.END_SCRIPT) && !check(TokenType.EOF)) {
            statements.add(parseStatement());
        }
        expect(TokenType.END_SCRIPT);
        if (!check(TokenType.EOF)) endLine();
        expect(TokenType.EOF);
        return new ProgramNode(start.line, statements);
    }

    private List<Node> parseDeclaration() {
        Token declaration = expect(TokenType.DECLARE);
        Token typeToken = current();
        ValueType type = switch (typeToken.type) {
            case INT -> ValueType.INT;
            case FLOAT -> ValueType.FLOAT;
            case CHAR -> ValueType.CHAR;
            case BOOL -> ValueType.BOOL;
            default -> throw error(typeToken, "Expected declaration type INT, FLOAT, CHAR, or BOOL.");
        };
        consume();

        List<Node> declarations = new ArrayList<>();
        do {
            Token name = expect(TokenType.IDENTIFIER);
            Node value = match(TokenType.ASSIGN) ? parseExpression() : null;
            declarations.add(new DeclareNode(declaration.line, type, name.value, value));
        } while (match(TokenType.COMMA));
        endLine();
        return declarations;
    }

    private Node parseStatement() {
        switch (current().type) {
            case IF: return parseIf();
            case FOR: return parseFor();
            case REPEAT_WHEN: return parseRepeat();
            default: break;
        }
        Node statement = switch (current().type) {
            case IDENTIFIER -> isIncDec(peek().type) ? parsePrimary() : parseAssignment();
            case INCREMENT, DECREMENT -> parseUnary();
            case PRINT -> parsePrint();
            case SCAN -> parseScan();
            default -> throw error(current(), "Unexpected token '" + current().value + "'.");
        };
        endLine();
        return statement;
    }

    private Node parseAssignment() {
        Token name = expect(TokenType.IDENTIFIER);
        expect(TokenType.ASSIGN);
        Node value = check(TokenType.IDENTIFIER) && peek().type == TokenType.ASSIGN
                ? parseAssignment() : parseExpression();
        return new AssignNode(name.line, name.value, value);
    }

    private Node parsePrint() {
        Token print = expect(TokenType.PRINT);
        expect(TokenType.COLON);
        List<Node> parts = new ArrayList<>();
        parts.add(parsePrintPart());
        while (match(TokenType.AMPERSAND)) parts.add(parsePrintPart());
        return new PrintNode(print.line, parts);
    }

    private Node parsePrintPart() {
        Token token = current();
        if (match(TokenType.DOLLAR)) return new LiteralNode(token.line, "\n", ValueType.STRING);
        if (match(TokenType.ESCAPE_CODE)) return new LiteralNode(token.line, token.value, ValueType.STRING);
        return parseExpression();
    }

    private Node parseScan() {
        Token scan = expect(TokenType.SCAN);
        expect(TokenType.COLON);
        List<String> variables = new ArrayList<>();
        variables.add(expect(TokenType.IDENTIFIER).value);
        while (match(TokenType.COMMA)) variables.add(expect(TokenType.IDENTIFIER).value);
        return new ScanNode(scan.line, variables);
    }

    private Node parseCondition() {
        expect(TokenType.LPAREN);
        Node condition = parseExpression();
        expect(TokenType.RPAREN);
        endLine();
        return condition;
    }

    private List<Node> parseBlock(TokenType start, TokenType end) {
        expect(start);
        endLine();
        List<Node> body = new ArrayList<>();
        while (!check(end) && !check(TokenType.EOF)) body.add(parseStatement());
        expect(end);
        endLine();
        return body;
    }

    private Node parseIf() {
        Token keyword = expect(TokenType.IF);
        Node condition = parseCondition();
        List<Node> thenBranch = parseBlock(TokenType.START_IF, TokenType.END_IF);
        List<IfNode> alternatives = new ArrayList<>();
        while (check(TokenType.ELSE_IF)) {
            Token alternative = consume();
            Node alternativeCondition = parseCondition();
            List<Node> body = parseBlock(TokenType.START_IF, TokenType.END_IF);
            alternatives.add(new IfNode(alternative.line, alternativeCondition, body, List.of(), null));
        }
        List<Node> elseBranch = null;
        if (match(TokenType.ELSE)) {
            endLine();
            elseBranch = parseBlock(TokenType.START_IF, TokenType.END_IF);
        }
        return new IfNode(keyword.line, condition, thenBranch, alternatives, elseBranch);
    }

    private Node parseFor() {
        Token keyword = expect(TokenType.FOR);
        expect(TokenType.LPAREN);
        Node initialization = parseAssignment();
        expect(TokenType.COMMA);
        Node condition = parseExpression();
        expect(TokenType.COMMA);
        Node update;
        if (isIncDec(current().type)) update = parseUnary();
        else if (check(TokenType.IDENTIFIER) && isIncDec(peek().type)) update = parsePrimary();
        else update = parseAssignment();
        expect(TokenType.RPAREN);
        endLine();
        List<Node> body = parseBlock(TokenType.START_FOR, TokenType.END_FOR);
        return new ForNode(keyword.line, initialization, condition, update, body);
    }

    private Node parseRepeat() {
        Token keyword = expect(TokenType.REPEAT_WHEN);
        Node condition = parseCondition();
        List<Node> body = parseBlock(TokenType.START_REPEAT, TokenType.END_REPEAT);
        return new RepeatNode(keyword.line, condition, body);
    }

    private Node parseExpression() {
        return parseOr();
    }

    private Node parseOr() {
        Node left = parseAnd();
        while (check(TokenType.OR)) {
            Token operator = consume();
            left = new BinaryOpNode(operator.line, left, operator.value, parseAnd());
        }
        return left;
    }

    private Node parseAnd() {
        Node left = parseEquality();
        while (check(TokenType.AND)) {
            Token operator = consume();
            left = new BinaryOpNode(operator.line, left, operator.value, parseEquality());
        }
        return left;
    }

    private Node parseEquality() {
        Node left = parseComparison();
        while (check(TokenType.EQUAL) || check(TokenType.NOT_EQUAL)) {
            Token operator = consume();
            left = new BinaryOpNode(operator.line, left, operator.value, parseComparison());
        }
        return left;
    }

    private Node parseComparison() {
        Node left = parseAddSub();
        while (check(TokenType.GREATER) || check(TokenType.LESS)
                || check(TokenType.GREATER_EQ) || check(TokenType.LESS_EQ)) {
            Token operator = consume();
            left = new BinaryOpNode(operator.line, left, operator.value, parseAddSub());
        }
        return left;
    }

    private Node parseAddSub() {
        Node left = parseMulDiv();
        while (check(TokenType.PLUS) || check(TokenType.MINUS)) {
            Token operator = consume();
            left = new BinaryOpNode(operator.line, left, operator.value, parseMulDiv());
        }
        return left;
    }

    private Node parseMulDiv() {
        Node left = parseUnary();
        while (check(TokenType.MULTIPLY) || check(TokenType.DIVIDE) || check(TokenType.MODULO)) {
            Token operator = consume();
            left = new BinaryOpNode(operator.line, left, operator.value, parseUnary());
        }
        return left;
    }

    private Node parseUnary() {
        Token operator = current();
        if (isIncDec(operator.type)) {
            consume();
            String name = expect(TokenType.IDENTIFIER).value;
            return new IncDecNode(operator.line, name, operator.type == TokenType.INCREMENT, true);
        }
        if (check(TokenType.MINUS) || check(TokenType.PLUS) || check(TokenType.NOT)) {
            consume();
            // The minimum INT's magnitude is valid only directly beneath unary minus.
            if (operator.type == TokenType.MINUS && check(TokenType.INT_LITERAL)) {
                int negative = parseInteger(current(), "-" + current().value);
                if (negative == Integer.MIN_VALUE) {
                    consume();
                    return new LiteralNode(operator.line, negative, ValueType.INT);
                }
            }
            return new UnaryOpNode(operator.line, operator.value, parseUnary());
        }
        return parsePrimary();
    }

    private Node parsePrimary() {
        Token token = current();
        switch (token.type) {
            case INT_LITERAL:
                consume();
                return new LiteralNode(token.line, parseInteger(token, token.value), ValueType.INT);
            case FLOAT_LITERAL:
                consume();
                try {
                    float value = Float.parseFloat(token.value);
                    if (!Float.isFinite(value)) throw error(token, "FLOAT literal must be finite.");
                    return new LiteralNode(token.line, value, ValueType.FLOAT);
                } catch (NumberFormatException e) {
                    throw error(token, "Invalid FLOAT literal '" + token.value + "'.");
                }
            case CHAR_LITERAL:
                consume();
                return new LiteralNode(token.line, token.value.charAt(0), ValueType.CHAR);
            case BOOL_LITERAL:
                consume();
                return new LiteralNode(token.line, token.value.equals("TRUE"), ValueType.BOOL);
            case STRING_LITERAL:
                consume();
                return new LiteralNode(token.line, token.value, ValueType.STRING);
            case IDENTIFIER:
                consume();
                if (isIncDec(current().type)) {
                    Token operator = consume();
                    return new IncDecNode(operator.line, token.value, operator.type == TokenType.INCREMENT, false);
                }
                return new VariableNode(token.line, token.value);
            case LPAREN:
                consume();
                Node expression = parseExpression();
                expect(TokenType.RPAREN);
                return expression;
            default:
                throw error(token, "Unexpected token '" + token.value + "'.");
        }
    }

    private int parseInteger(Token token, String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw error(token, "INT literal out of range: " + value + ".");
        }
    }

    private static boolean isIncDec(TokenType type) {
        return type == TokenType.INCREMENT || type == TokenType.DECREMENT;
    }

    private LexorException error(Token token, String message) {
        return new LexorException(LexorException.Phase.PARSER, token.line, message);
    }
}
