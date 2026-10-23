package lexor;

public enum ValueType {
    INT, FLOAT, CHAR, BOOL, STRING;

    public boolean isNumeric() {
        return this == INT || this == FLOAT;
    }

    public Object defaultValue() {
        return switch (this) {
            case INT -> 0;
            case FLOAT -> 0.0f;
            case CHAR -> '\0';
            case BOOL -> false;
            case STRING -> "";
        };
    }

    public boolean accepts(ValueType actual) {
        return this == actual || this == FLOAT && actual == INT;
    }

    public Object coerce(Object value, LexorException.Phase phase, int line) {
        Object converted = switch (this) {
            case INT -> value instanceof Integer ? value : null;
            case FLOAT -> {
                if (value instanceof Integer integer) yield integer.floatValue();
                yield value instanceof Float ? value : null;
            }
            case CHAR -> value instanceof Character ? value : null;
            case BOOL -> value instanceof Boolean ? value : null;
            case STRING -> value instanceof String ? value : null;
        };
        if (converted == null) {
            String actual = value == null ? "null" : value.getClass().getSimpleName();
            throw new LexorException(phase, line,
                    "Type mismatch: expected " + this + ", got " + actual + ".");
        }
        if (converted instanceof Float number && !Float.isFinite(number)) {
            throw new LexorException(phase, line, "FLOAT value must be finite.");
        }
        return converted;
    }
}
