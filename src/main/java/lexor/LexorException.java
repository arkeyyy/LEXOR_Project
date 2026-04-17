package lexor;

public class LexorException extends RuntimeException {
    public enum Phase{
        LEXER, PARSER, SEMANTIC, RUNTIME
    }

    private final Phase phase;
    private final int line;

    public LexorException(Phase phase, int line, String message) {
        super(formatMessage(phase, line, message));
        this.phase = phase;
        this.line  = line;
    }

    // For errors without a line number (e.g. file not found)
    public LexorException(Phase phase, String message) {
        super(formatMessage(phase, -1, message));
        this.phase = phase;
        this.line  = -1;
    }

    private static String formatMessage(Phase phase, int line, String message) {
        String prefix = switch (phase) {
            case LEXER -> "LexicalError";
            case PARSER -> "SyntaxError";
            case SEMANTIC -> "SemanticError";
            case RUNTIME -> "RuntimeError";
        };
        if (line >= 0) return prefix + " at line " + line + ": " + message;
        return prefix + ": " + message;
    }

    public int getLine(){return line;}
    public Phase getPhase(){return phase;}
}
