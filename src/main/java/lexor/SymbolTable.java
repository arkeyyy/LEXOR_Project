package lexor;

import java.util.HashMap;
import java.util.Map;

public class SymbolTable {
    private static final class Entry {
        private final ValueType type;
        private Object value;

        private Entry(ValueType type, Object value) {
            this.type = type;
            this.value = value;
        }
    }

    private final Map<String, Entry> entries = new HashMap<>();

    public void declare(String name, ValueType type, int line, Object value) {
        if (entries.containsKey(name)) {
            throw new LexorException(LexorException.Phase.SEMANTIC, line,
                    "Variable '" + name + "' is already declared.");
        }
        if (type == ValueType.STRING) {
            throw new LexorException(LexorException.Phase.SEMANTIC, line,
                    "STRING is only supported in printable literals.");
        }
        Object converted = type.coerce(value, LexorException.Phase.SEMANTIC, line);
        entries.put(name, new Entry(type, converted));
    }

    public ValueType getType(String name) {
        return getType(name, LexorException.Phase.SEMANTIC, -1);
    }

    public ValueType getType(String name, LexorException.Phase phase, int line) {
        return lookup(name, phase, line).type;
    }

    public Object getValue(String name) {
        return getValue(name, -1);
    }

    public Object getValue(String name, int line) {
        return lookup(name, LexorException.Phase.RUNTIME, line).value;
    }

    public void setValue(String name, Object value) {
        setValue(name, value, -1);
    }

    public void setValue(String name, Object value, int line) {
        Entry entry = lookup(name, LexorException.Phase.RUNTIME, line);
        entry.value = entry.type.coerce(value, LexorException.Phase.RUNTIME, line);
    }

    public boolean isDeclared(String name) {
        return entries.containsKey(name);
    }

    private Entry lookup(String name, LexorException.Phase phase, int line) {
        Entry entry = entries.get(name);
        if (entry == null) {
            throw new LexorException(phase, line, "Undeclared variable '" + name + "'.");
        }
        return entry;
    }
}
