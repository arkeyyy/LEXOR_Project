package lexor.nodes;

public class AssignNode extends Node{
    public final String name;
    public final Node value;

    public AssignNode(int line, String name, Node value) {
        super(line);
        this.name = name;
        this.value = value;
    }

}
