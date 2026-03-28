package lexor.nodes;

public class DeclareNode extends Node {
    public final String type;    // INT, FLOAT, CHAR, BOOL
    public final String name;    // variable name
    public final Node value;     // value, can be null

    public DeclareNode(String type, String name, Node value) {
        this.type = type;
        this.name = name;
        this.value = value;
    }


}
