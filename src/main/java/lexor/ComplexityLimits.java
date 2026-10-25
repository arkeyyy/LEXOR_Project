package lexor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import lexor.nodes.*;

/** Structural limits shared by parsing and the public AST consumers. */
final class ComplexityLimits {
    static final int PARSER_NESTING = 128;
    static final int AST_DEPTH = 256;

    private ComplexityLimits() {}

    private record Entry(Node node, int depth) {}

    /** Iterative so even a caller-constructed oversized tree cannot exhaust the Java stack. */
    static void validateAst(Node root, LexorException.Phase phase) {
        Deque<Entry> pending = new ArrayDeque<>();
        pending.push(new Entry(root, 1));
        while (!pending.isEmpty()) {
            Entry entry = pending.pop();
            Node node = entry.node();
            if (entry.depth() > AST_DEPTH) {
                throw new LexorException(phase, node.line,
                        "AST depth exceeds limit of " + AST_DEPTH + ".");
            }
            int depth = entry.depth() + 1;
            switch (node) {
                case ProgramNode n -> pushAll(pending, n.statements, depth);
                case DeclareNode n -> push(pending, n.value, depth);
                case AssignNode n -> push(pending, n.value, depth);
                case PrintNode n -> pushAll(pending, n.parts, depth);
                case BinaryOpNode n -> {
                    push(pending, n.right, depth);
                    push(pending, n.left, depth);
                }
                case UnaryOpNode n -> push(pending, n.operand, depth);
                case IfNode n -> {
                    pushAll(pending, n.elseBranch, depth);
                    pushAll(pending, n.elseIfBranches, depth);
                    pushAll(pending, n.thenBranch, depth);
                    push(pending, n.condition, depth);
                }
                case ForNode n -> {
                    pushAll(pending, n.body, depth);
                    push(pending, n.update, depth);
                    push(pending, n.condition, depth);
                    push(pending, n.initialization, depth);
                }
                case RepeatNode n -> {
                    pushAll(pending, n.body, depth);
                    push(pending, n.condition, depth);
                }
                default -> { /* Leaves and unknown nodes are handled by the consuming phase. */ }
            }
        }
    }

    private static void push(Deque<Entry> pending, Node node, int depth) {
        if (node != null) pending.push(new Entry(node, depth));
    }

    private static void pushAll(Deque<Entry> pending, List<? extends Node> nodes, int depth) {
        if (nodes == null) return;
        for (int i = nodes.size() - 1; i >= 0; i--) push(pending, nodes.get(i), depth);
    }
}
