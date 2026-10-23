package lexor.nodes;

import lexor.ValueType;

public class DeclareNode extends Node {
    public final ValueType type;
    public final String name;    // variable name
    public final Node value;     // value, can be null

    public DeclareNode(int line, ValueType type, String name, Node value) {
        super(line);
        this.type = type;
        this.name = name;
        this.value = value;
    }


}
