package lexor.nodes;

public abstract class Node {
    public final int line;

    protected Node(int line) {
        if (line < 1) {
            throw new IllegalArgumentException("Source line must be positive.");
        }
        this.line = line;
    }
}
