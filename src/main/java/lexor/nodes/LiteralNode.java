package lexor.nodes;

public class LiteralNode extends Node{
    public final Object value;   // Integer, Float, Character, Boolean, String
    public final String type;    // INT, FLOAT, CHAR, BOOL, STRING

    public LiteralNode(Object value, String type) {
        this.value = value;
        this.type = type;
    }
}
