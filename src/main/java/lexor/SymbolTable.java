package lexor;
import java.util.Map;
import java.util.HashMap;

public class SymbolTable {
    private final Map<String, String> types  = new HashMap<>(); // name -> dataType   INT, FLOAT, CHAR, STRING, BOOL
    private final Map<String, Object> values = new HashMap<>(); // name -> value     used Object coz value could be of any data type

    public void declare(String name, String type, Object value) {
        if (types.containsKey(name)) {
            throw new RuntimeException("Variable '" + name + "' is already declared.");
        }
        types.put(name, type);
        values.put(name, value);
    }

    public String getType(String name) {
        if (!types.containsKey(name)) {
            throw new RuntimeException("Undeclared variable '" + name + "'.");
        }
        return types.get(name);
    }

    public Object getValue(String name) {
        if (!values.containsKey(name)) {
            throw new RuntimeException("Undeclared variable '" + name + "'.");
        }
        return values.get(name);
    }

    public void setValue(String name, Object value) {
        if (!types.containsKey(name)) {
            throw new RuntimeException("Undeclared variable '" + name + "'.");
        }
        values.put(name, value);
    }

    public boolean isDeclared(String name) {
        return types.containsKey(name);
    }
}
