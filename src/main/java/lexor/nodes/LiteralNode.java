package lexor.nodes;

import lexor.ValueType;

public class LiteralNode extends Node{
    public final Object value;   // Integer, Float, Character, Boolean, String
    public final ValueType type;

    public LiteralNode(int line, Object value, ValueType type) {
        super(line);
        this.value = value;
        this.type = type;
    }
}
