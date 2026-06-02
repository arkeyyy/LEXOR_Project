package lexor;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

public class Lexer {
    private final String source;
    private int pos = 0;
    private int line = 1;
    private final List<Token> tokens = new ArrayList<>();

    public Lexer(String source) {
        this.source = source;
    }

    private static final Map<String, TokenType> KEYWORDS = new HashMap<>();

    static {
        KEYWORDS.put("DECLARE", TokenType.DECLARE);           //
        KEYWORDS.put("PRINT",   TokenType.PRINT);             //
        KEYWORDS.put("SCAN",    TokenType.SCAN);              //
        KEYWORDS.put("IF",      TokenType.IF);                //
        KEYWORDS.put("FOR",     TokenType.FOR);               //
        KEYWORDS.put("AND",     TokenType.AND);               //
        KEYWORDS.put("OR",      TokenType.OR);                //
        KEYWORDS.put("NOT",     TokenType.NOT);               //
        KEYWORDS.put("INT",     TokenType.INT);               //
        KEYWORDS.put("FLOAT",   TokenType.FLOAT);             //
        KEYWORDS.put("CHAR",    TokenType.CHAR);              //
        KEYWORDS.put("BOOL",    TokenType.BOOL);              //
        // "SCRIPT", "START", "END", "ELSE", "REPEAT" kay handled sa readIdentifierOrKeyword() coz naa silay space between two keywords
    }

    public List<Token> tokenize() {
        while (pos < source.length()) {
            skipWhitespace();
            if (pos >= source.length()) break;

            char c = source.charAt(pos);

            if (isComment()) { skipComment(); }
            else if (c == '\'') { readCharLiteral(); }
            else if (c == '"') { readStringOrBool(); }
            else if (Character.isDigit(c)) { readNumber(); }
            else if (Character.isLetter(c) || c == '_') { readIdentifierOrKeyword(); }
            else { readSymbol(); }
        }

        tokens.add(new Token(TokenType.EOF, "", line));
        return tokens;
    }

    private boolean isComment() {
        return pos + 1 < source.length() &&
                source.charAt(pos) == '%' &&
                source.charAt(pos + 1) == '%';
    }

    private void skipComment() {
        while (pos < source.length() && source.charAt(pos) != '\n') pos++;
    }

    private void skipWhitespace() {
        while (pos < source.length()) {
            char c = source.charAt(pos);
            if (c == '\n') { line++; pos++; }
            else if (Character.isWhitespace(c)) { pos++; }
            else break;
        }
    }

    private void readIdentifierOrKeyword() {
        int start = pos;
        while (pos < source.length() &&
                (Character.isLetterOrDigit(source.charAt(pos)) || source.charAt(pos) == '_')) {
            pos++;
        }
        String word = source.substring(start, pos);

        // Handle compound keywords by peeking at next word
        switch (word) {
            case "SCRIPT" -> {
                String next = peekNextWord();
                if (next.equals("AREA")) {
                    consumeNextWord();
                    tokens.add(new Token(TokenType.SCRIPT_AREA, "SCRIPT AREA", line));
                } else {
                    tokens.add(new Token(TokenType.IDENTIFIER, word, line));
                }
            }
            case "START" -> {
                String next = peekNextWord();
                switch (next) {
                    case "SCRIPT" -> { consumeNextWord(); tokens.add(new Token(TokenType.START_SCRIPT, "START SCRIPT", line)); }
                    case "IF"     -> { consumeNextWord(); tokens.add(new Token(TokenType.START_IF,     "START IF",     line)); }
                    case "FOR"    -> { consumeNextWord(); tokens.add(new Token(TokenType.START_FOR,    "START FOR",    line)); }
                    case "REPEAT" -> { consumeNextWord(); tokens.add(new Token(TokenType.START_REPEAT, "START REPEAT", line)); }
                    default       -> tokens.add(new Token(TokenType.IDENTIFIER, word, line));
                }
            }
            case "END" -> {
                String next = peekNextWord();
                switch (next) {
                    case "SCRIPT" -> { consumeNextWord(); tokens.add(new Token(TokenType.END_SCRIPT, "END SCRIPT", line)); }
                    case "IF"     -> { consumeNextWord(); tokens.add(new Token(TokenType.END_IF,     "END IF",     line)); }
                    case "FOR"    -> { consumeNextWord(); tokens.add(new Token(TokenType.END_FOR,    "END FOR",    line)); }
                    case "REPEAT" -> { consumeNextWord(); tokens.add(new Token(TokenType.END_REPEAT, "END REPEAT", line)); }
                    default       -> tokens.add(new Token(TokenType.IDENTIFIER, word, line));
                }
            }
            case "ELSE" -> {
                String next = peekNextWord();
                if (next.equals("IF")) {
                    consumeNextWord();
                    tokens.add(new Token(TokenType.ELSE_IF, "ELSE IF", line));
                } else {
                    tokens.add(new Token(TokenType.ELSE, "ELSE", line));
                }
            }
            case "REPEAT" -> {
                String next = peekNextWord();
                if (next.equals("WHEN")) {
                    consumeNextWord();
                    tokens.add(new Token(TokenType.REPEAT_WHEN, "REPEAT WHEN", line));
                } else {
                    tokens.add(new Token(TokenType.IDENTIFIER, word, line));
                }
            }
            default -> {
                TokenType type = KEYWORDS.getOrDefault(word, TokenType.IDENTIFIER);
                tokens.add(new Token(type, word, line));
            }
        }
    }

    private void readCharLiteral() {
        pos++; // skip opening '
        if (pos >= source.length()) {
            throw new LexorException(LexorException.Phase.LEXER, line, "Unterminated CHAR literal.");
        }
        char value = source.charAt(pos);
        pos++; // skip the actual character
        if (pos >= source.length() || source.charAt(pos) != '\'') {
            throw new LexorException(LexorException.Phase.LEXER, line, "CHAR literal must be a single character");
        }
        pos++; // skip closing '
        tokens.add(new Token(TokenType.CHAR_LITERAL, String.valueOf(value), line));
    }

    private void readStringOrBool() {
        pos++; // skip opening "
        StringBuilder sb = new StringBuilder();
        while (pos < source.length() && source.charAt(pos) != '"') {
            if (source.charAt(pos) == '\n') {
                throw new LexorException(LexorException.Phase.LEXER, line, "Unterminated string");
            }
            sb.append(source.charAt(pos));
            pos++;
        }
        if (pos >= source.length()) {
            throw new LexorException(LexorException.Phase.LEXER, line, "Unterminated string");
        }
        pos++; // skip closing "
        String value = sb.toString();

        // Check if it's a BOOL literal
        if (value.equals("TRUE") || value.equals("FALSE")) {
            tokens.add(new Token(TokenType.BOOL_LITERAL, value, line));
        } else {
            tokens.add(new Token(TokenType.STRING_LITERAL, value, line));
        }
    }

    private void readNumber() {
        int start = pos;
        boolean isFloat = false;

        while (pos < source.length() && Character.isDigit(source.charAt(pos))) {
            pos++;
        }

        // Check for decimal point — makes it a FLOAT
        if (pos < source.length() && source.charAt(pos) == '.' &&
                pos + 1 < source.length() && Character.isDigit(source.charAt(pos + 1))) {
            isFloat = true;
            pos++; // skip the dot
            while (pos < source.length() && Character.isDigit(source.charAt(pos))) {
                pos++;
            }
        }

        String value = source.substring(start, pos);
        TokenType type = isFloat ? TokenType.FLOAT_LITERAL : TokenType.INT_LITERAL;
        tokens.add(new Token(type, value, line));
    }

    private void readSymbol() {
        char c = source.charAt(pos);

        switch (c) {
            case '&' -> { tokens.add(new Token(TokenType.AMPERSAND, "&", line)); pos++; }
            case '$' -> { tokens.add(new Token(TokenType.DOLLAR,    "$", line)); pos++; }
            case ':' -> { tokens.add(new Token(TokenType.COLON,     ":", line)); pos++; }
            case ',' -> { tokens.add(new Token(TokenType.COMMA,     ",", line)); pos++; }
            case '(' -> { tokens.add(new Token(TokenType.LPAREN,    "(", line)); pos++; }
            case ')' -> { tokens.add(new Token(TokenType.RPAREN,    ")", line)); pos++; }
            case '+' -> {
                            if (pos + 1 < source.length() && source.charAt(pos + 1) == '+') {
                                tokens.add(new Token(TokenType.INCREMENT, "++", line)); pos += 2;
                            } else {
                                tokens.add(new Token(TokenType.PLUS, "+", line)); pos++;
                            }
                        }
            case '-' -> {
                            if (pos + 1 < source.length() && source.charAt(pos + 1) == '-') {
                                tokens.add(new Token(TokenType.DECREMENT, "--", line)); pos += 2;
                            } else {
                                tokens.add(new Token(TokenType.MINUS, "-", line)); pos++;
                            }
                        }
            case '*' -> { tokens.add(new Token(TokenType.MULTIPLY,  "*", line)); pos++; }
            case '/' -> { tokens.add(new Token(TokenType.DIVIDE,    "/", line)); pos++; }
            case '%' -> { tokens.add(new Token(TokenType.MODULO,    "%", line)); pos++; }
            case '=' -> {
                if (pos + 1 < source.length() && source.charAt(pos + 1) == '='){
                    tokens.add(new Token(TokenType.EQUAL, "==", line)); pos+=2;
                } else {
                    tokens.add(new Token(TokenType.ASSIGN, "=", line)); pos++;
                }
            }

            // Two-character operators
            case '>' -> {
                if (pos + 1 < source.length() && source.charAt(pos + 1) == '=') {
                    tokens.add(new Token(TokenType.GREATER_EQ, ">=", line)); pos += 2;
                } else {
                    tokens.add(new Token(TokenType.GREATER, ">", line)); pos++;
                }
            }
            case '<' -> {
                if (pos + 1 < source.length() && source.charAt(pos + 1) == '=') {
                    tokens.add(new Token(TokenType.LESS_EQ, "<=", line)); pos += 2;
                } else if (pos + 1 < source.length() && source.charAt(pos + 1) == '>') {
                    tokens.add(new Token(TokenType.NOT_EQUAL, "<>", line)); pos += 2;
                } else {
                    tokens.add(new Token(TokenType.LESS, "<",  line)); pos++;
                }
            }

            case '[' -> {
                // Could be escape code like [#] or [[]
                if (pos + 2 < source.length() && source.charAt(pos + 2) == ']') {
                    String escape = source.substring(pos + 1, pos + 2); // the char inside []
                    tokens.add(new Token(TokenType.ESCAPE_CODE, escape, line));
                    pos += 3; // skip [ + char + ]
                } else {
                    tokens.add(new Token(TokenType.LBRACKET, "[", line)); pos++;
                }
            }
            case ']' -> { tokens.add(new Token(TokenType.RBRACKET, "]", line)); pos++; }

            default -> {
                throw new LexorException(LexorException.Phase.LEXER, line, "Unknown character '" + c + "'.");
            }
        }
    }

    private String peekNextWord() {
        int tempPos = pos;

        // Skip spaces and tabs only (NOT newlines — compound keywords are on same line)
        while (tempPos < source.length() && source.charAt(tempPos) == ' ') tempPos++;

        // Read the next word
        StringBuilder sb = new StringBuilder();
        while (tempPos < source.length() &&
                Character.isLetter(source.charAt(tempPos))) {
            sb.append(source.charAt(tempPos));
            tempPos++;
        }
        return sb.toString();
    }

    private void consumeNextWord() {
        // Skip spaces
        while (pos < source.length() && source.charAt(pos) == ' ') pos++;
        // Skip the word itself
        while (pos < source.length() && Character.isLetter(source.charAt(pos))) pos++;
    }


}
