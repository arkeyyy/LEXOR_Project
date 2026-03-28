package lexor.nodes;
import java.util.List;

public class PrintNode extends Node{
    public final List<Node> parts;      // each item separated by &

    public PrintNode(List<Node> parts){
        this.parts = parts;
    }
}
