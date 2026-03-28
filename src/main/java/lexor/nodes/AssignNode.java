package lexor.nodes;

public class AssignNode extends Node{
    public final String name;
    public final Node value;

    public AssignNode(String name, Node value) {
        this.name = name;
        this.value = value;
    }

}
