package lexor;

public enum TokenType {
    // Literals
    INT_LITERAL, FLOAT_LITERAL, CHAR_LITERAL, BOOL_LITERAL, STRING_LITERAL,

    // Data Types
    INT, FLOAT, CHAR, BOOL,

    // Keywords
    SCRIPT_AREA, START_SCRIPT, END_SCRIPT,
    SCRIPT, AREA, START, END, REPEAT, WHEN,
    DECLARE, PRINT, SCAN,
    IF, ELSE, ELSE_IF, START_IF, END_IF,
    FOR, START_FOR, END_FOR,
    REPEAT_WHEN, START_REPEAT, END_REPEAT,

    // Operators
    PLUS, MINUS, MULTIPLY, DIVIDE, MODULO,
    GREATER, LESS, GREATER_EQ, LESS_EQ,
    EQUAL, NOT_EQUAL,
    AND, OR, NOT,

    // Symbols
    AMPERSAND, DOLLAR, COLON, COMMA,
    LBRACKET, RBRACKET, LPAREN, RPAREN,

    // Special
    ESCAPE_CODE,                       // escape characters [#]
    COMMENT,                           // comment %%
    IDENTIFIER,                        // variables
    ASSIGN,                            // assignment operators
    NEWLINE, EOF,

    INCREMENT,  // ++
    DECREMENT,  // --
}
