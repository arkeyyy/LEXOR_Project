package lexor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Lexer {
    private static final Map<String, TokenType> KEYWORDS = Map.ofEntries(
            Map.entry("DECLARE", TokenType.DECLARE), Map.entry("PRINT", TokenType.PRINT),
            Map.entry("SCAN", TokenType.SCAN), Map.entry("IF", TokenType.IF),
            Map.entry("ELSE", TokenType.ELSE), Map.entry("FOR", TokenType.FOR),
            Map.entry("AND", TokenType.AND), Map.entry("OR", TokenType.OR),
            Map.entry("NOT", TokenType.NOT), Map.entry("INT", TokenType.INT),
            Map.entry("FLOAT", TokenType.FLOAT), Map.entry("CHAR", TokenType.CHAR),
            Map.entry("BOOL", TokenType.BOOL), Map.entry("SCRIPT", TokenType.SCRIPT),
            Map.entry("AREA", TokenType.AREA), Map.entry("START", TokenType.START),
            Map.entry("END", TokenType.END), Map.entry("REPEAT", TokenType.REPEAT),
            Map.entry("WHEN", TokenType.WHEN));

    private static final Map<String, TokenType> COMPOUND_KEYWORDS = Map.ofEntries(
            Map.entry("SCRIPT AREA", TokenType.SCRIPT_AREA),
            Map.entry("START SCRIPT", TokenType.START_SCRIPT),
            Map.entry("END SCRIPT", TokenType.END_SCRIPT),
            Map.entry("START IF", TokenType.START_IF), Map.entry("END IF", TokenType.END_IF),
            Map.entry("ELSE IF", TokenType.ELSE_IF),
            Map.entry("START FOR", TokenType.START_FOR), Map.entry("END FOR", TokenType.END_FOR),
            Map.entry("REPEAT WHEN", TokenType.REPEAT_WHEN),
            Map.entry("START REPEAT", TokenType.START_REPEAT),
            Map.entry("END REPEAT", TokenType.END_REPEAT));

    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private int pos;
    private int line = 1;

    public Lexer(String source) {
        this.source = source;
        // Only the first source code unit can be a UTF-8 signature after decoding.
        // Advancing the offset keeps line numbering and every literal unchanged.
        if (!source.isEmpty() && source.charAt(0) == '\uFEFF') pos = 1;
    }

    public List<Token> tokenize() {
        if (!tokens.isEmpty() && tokens.getLast().type == TokenType.EOF) {
            return List.copyOf(tokens);
        }
        while (pos < source.length()) {
            char c = source.charAt(pos);
            if (isHorizontalWhitespace(c)) {
                pos++;
            } else if (isLineBreak(c)) {
                readNewline();
            } else if (c == '%' && nextIs('%')) {
                while (pos < source.length() && !isLineBreak(source.charAt(pos))) pos++;
            } else if (c == '\'') {
                readCharLiteral();
            } else if (c == '"') {
                readStringOrBool();
            } else if (Character.isDigit(c)) {
                readNumber();
            } else if (Character.isLetter(c) || c == '_') {
                readIdentifierOrKeyword();
            } else {
                readSymbol();
            }
        }
        tokens.add(new Token(TokenType.EOF, "", line));
        return List.copyOf(tokens);
    }

    private static boolean isHorizontalWhitespace(char c) {
        return c == ' ' || c == '\t';
    }

    private static boolean isLineBreak(char c) {
        return c == '\n' || c == '\r';
    }

    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private boolean nextIs(char c) {
        return pos + 1 < source.length() && source.charAt(pos + 1) == c;
    }

    private void readNewline() {
        tokens.add(new Token(TokenType.NEWLINE, "\n", line));
        if (source.charAt(pos) == '\r' && nextIs('\n')) pos++;
        pos++;
        line++;
    }

    private void readIdentifierOrKeyword() {
        int start = pos;
        while (pos < source.length() && isIdentifierPart(source.charAt(pos))) pos++;
        String word = source.substring(start, pos);

        if (word.equals("SCRIPT") || word.equals("START") || word.equals("END")
                || word.equals("ELSE") || word.equals("REPEAT")) {
            int nextStart = pos;
            while (nextStart < source.length() && isHorizontalWhitespace(source.charAt(nextStart))) nextStart++;
            int nextEnd = nextStart;
            while (nextEnd < source.length() && isIdentifierPart(source.charAt(nextEnd))) nextEnd++;
            if (nextStart > pos && nextEnd > nextStart) {
                String compound = word + " " + source.substring(nextStart, nextEnd);
                TokenType type = COMPOUND_KEYWORDS.get(compound);
                if (type != null) {
                    pos = nextEnd;
                    tokens.add(new Token(type, compound, line));
                    return;
                }
            }
        }
        tokens.add(new Token(KEYWORDS.getOrDefault(word, TokenType.IDENTIFIER), word, line));
    }

    private void readCharLiteral() {
        pos++;
        if (pos >= source.length() || isLineBreak(source.charAt(pos))) {
            throw error("Unterminated CHAR literal.");
        }
        char value = source.charAt(pos++);
        if (pos >= source.length() || source.charAt(pos) != '\'') {
            throw error("CHAR literal must contain exactly one character.");
        }
        pos++;
        tokens.add(new Token(TokenType.CHAR_LITERAL, String.valueOf(value), line));
    }

    private void readStringOrBool() {
        int start = ++pos;
        while (pos < source.length() && source.charAt(pos) != '"') {
            if (isLineBreak(source.charAt(pos))) throw error("Unterminated string literal.");
            pos++;
        }
        if (pos >= source.length()) throw error("Unterminated string literal.");
        String value = source.substring(start, pos++);
        TokenType type = value.equals("TRUE") || value.equals("FALSE")
                ? TokenType.BOOL_LITERAL : TokenType.STRING_LITERAL;
        tokens.add(new Token(type, value, line));
    }

    private void readNumber() {
        int start = pos;
        while (pos < source.length() && Character.isDigit(source.charAt(pos))) pos++;
        boolean isFloat = pos + 1 < source.length() && source.charAt(pos) == '.'
                && Character.isDigit(source.charAt(pos + 1));
        if (isFloat) {
            pos++;
            while (pos < source.length() && Character.isDigit(source.charAt(pos))) pos++;
        }
        tokens.add(new Token(isFloat ? TokenType.FLOAT_LITERAL : TokenType.INT_LITERAL,
                source.substring(start, pos), line));
    }

    private void readSymbol() {
        switch (source.charAt(pos)) {
            case '&' -> addSymbol(TokenType.AMPERSAND, 1);
            case '$' -> addSymbol(TokenType.DOLLAR, 1);
            case ':' -> addSymbol(TokenType.COLON, 1);
            case ',' -> addSymbol(TokenType.COMMA, 1);
            case '(' -> addSymbol(TokenType.LPAREN, 1);
            case ')' -> addSymbol(TokenType.RPAREN, 1);
            case '*' -> addSymbol(TokenType.MULTIPLY, 1);
            case '/' -> addSymbol(TokenType.DIVIDE, 1);
            case '%' -> addSymbol(TokenType.MODULO, 1);
            case '+' -> addSymbol(nextIs('+') ? TokenType.INCREMENT : TokenType.PLUS, nextIs('+') ? 2 : 1);
            case '-' -> addSymbol(nextIs('-') ? TokenType.DECREMENT : TokenType.MINUS, nextIs('-') ? 2 : 1);
            case '=' -> addSymbol(nextIs('=') ? TokenType.EQUAL : TokenType.ASSIGN, nextIs('=') ? 2 : 1);
            case '>' -> addSymbol(nextIs('=') ? TokenType.GREATER_EQ : TokenType.GREATER, nextIs('=') ? 2 : 1);
            case '<' -> {
                if (nextIs('=')) addSymbol(TokenType.LESS_EQ, 2);
                else if (nextIs('>')) addSymbol(TokenType.NOT_EQUAL, 2);
                else addSymbol(TokenType.LESS, 1);
            }
            case '[' -> readEscape();
            case ']' -> throw error("Unmatched closing bracket.");
            default -> throw error("Unknown character '" + source.charAt(pos) + "'.");
        }
    }

    private void readEscape() {
        if (pos + 2 >= source.length() || isLineBreak(source.charAt(pos + 1))
                || source.charAt(pos + 2) != ']') {
            throw error("Escape code must contain exactly one character inside [].");
        }
        tokens.add(new Token(TokenType.ESCAPE_CODE, source.substring(pos + 1, pos + 2), line));
        pos += 3;
    }

    private void addSymbol(TokenType type, int length) {
        tokens.add(new Token(type, source.substring(pos, pos + length), line));
        pos += length;
    }

    private LexorException error(String message) {
        return new LexorException(LexorException.Phase.LEXER, line, message);
    }
}
