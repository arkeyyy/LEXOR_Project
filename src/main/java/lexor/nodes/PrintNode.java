package lexor.nodes;
import java.util.List;

public class PrintNode extends Node{
    public final List<Node> parts;      // each item separated by &

    public PrintNode(int line, List<Node> parts) {
        super(line);
        this.parts = parts;
    }
}
