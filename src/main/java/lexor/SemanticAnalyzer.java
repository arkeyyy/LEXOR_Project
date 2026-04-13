package lexor;

import lexor.nodes.*;
import java.util.List;

public class SemanticAnalyzer {
    private final SymbolTable symbolTable = new SymbolTable();

    public SymbolTable getSymbolTable() {
        return symbolTable;
    }

    public void analyze(ProgramNode program) {
        for (Node statement : program.statements) {
            analyzeNode(statement);
        }
    }

    private void analyzeNode(Node node) {
        switch (node) {
            case DeclareNode   n -> analyzeDeclare(n);
            case AssignNode    n -> analyzeAssign(n);
            case PrintNode     n -> analyzePrint(n);
            case ScanNode      n -> analyzeScan(n);
            case IfNode        n -> analyzeIf(n);
            case ForNode       n -> analyzeFor(n);
            case RepeatNode    n -> analyzeRepeat(n);
            default -> throw new RuntimeException(
                    "Unknown node type: " + node.getClass().getSimpleName()
            );
        }
    }

    // Declarations //////////////////////////////////////////////////////////////////////////////

    private void analyzeDeclare(DeclareNode node) {
        Object defaultValue = getDefault(node.type);
        if (node.value != null) {
            String valueType = inferType(node.value);
            checkTypeMatch(node.type, valueType, "Declaration of '" + node.name + "'");
        }
        symbolTable.declare(node.name, node.type, defaultValue);
    }

    private Object getDefault(String type) {
        return switch (type) {
            case "INT"   -> 0;
            case "FLOAT" -> 0.0f;
            case "CHAR"  -> '\0';
            case "BOOL"  -> false;
            default      -> null;
        };
    }

    // Assignment ///////////////////////////////////////////////////////////////////////////////

    private void analyzeAssign(AssignNode node) {
        if (!symbolTable.isDeclared(node.name)) {
            throw new RuntimeException("Assignment to undeclared variable '" + node.name + "'.");
        }
        String varType   = symbolTable.getType(node.name);
        String valueType = inferType(node.value);
        checkTypeMatch(varType, valueType, "Assignment to '" + node.name + "'");
    }

    // Printing ///////////////////////////////////////////////////////////////////////////////////

    private void analyzePrint(PrintNode node) {
        for (Node part : node.parts) {
            if (part instanceof VariableNode v && !symbolTable.isDeclared(v.name)) {
                throw new RuntimeException("Undeclared variable '" + v.name + "' in PRINT.");
            }
        }
    }

    // Scanning ////////////////////////////////////////////////////////////////////////////////////

    private void analyzeScan(ScanNode node) {
        for (String var : node.variables) {
            if (!symbolTable.isDeclared(var)) {
                throw new RuntimeException("Undeclared variable '" + var + "' in SCAN.");
            }
        }
    }

    // If ///////////////////////////////////////////////////////////////////////////////////////////

    private void analyzeIf(IfNode node) {
        String condType = inferType(node.condition);
        if (!condType.equals("BOOL")) {
            throw new RuntimeException("IF condition must be BOOL, got " + condType + ".");
        }
        analyzeBlock(node.thenBranch);
        for (IfNode elseIf : node.elseIfBranches) analyzeIf(elseIf);
        if (node.elseBranch != null) analyzeBlock(node.elseBranch);
    }

    // For ///////////////////////////////////////////////////////////////////////////////////////////

    private void analyzeFor(ForNode node) {
        analyzeNode(node.initialization);
        String condType = inferType(node.condition);
        if (!condType.equals("BOOL")) {
            throw new RuntimeException("FOR condition must be BOOL, got " + condType + ".");
        }
        analyzeNode(node.update);
        analyzeBlock(node.body);
    }

    // Repeat /////////////////////////////////////////////////////////////////////////////////////////

    private void analyzeRepeat(RepeatNode node) {
        String condType = inferType(node.condition);
        if (!condType.equals("BOOL")) {
            throw new RuntimeException("REPEAT WHEN condition must be BOOL, got " + condType + ".");
        }
        analyzeBlock(node.body);
    }

    // Block //////////////////////////////////////////////////////////////////////////////////////////

    private void analyzeBlock(List<Node> block) {
        for (Node node : block) analyzeNode(node);
    }

    // Type Inference methods /////////////////////////////////////////////////////////////////////////

    public String inferType(Node node) {
        return switch (node) {
            case LiteralNode  n -> n.type;
            case VariableNode n -> symbolTable.getType(n.name);
            case BinaryOpNode n -> inferBinaryType(n);
            case UnaryOpNode  n -> inferUnaryType(n);
            case AssignNode   n -> {
                analyzeAssign(n);
                yield symbolTable.getType(n.name);
            }
            default -> throw new RuntimeException(
                    "Cannot infer type of: " + node.getClass().getSimpleName()
            );
        };
    }

    private String inferBinaryType(BinaryOpNode node) {
        String left  = inferType(node.left);
        String right = inferType(node.right);

        return switch (node.operator) {
            case "+", "-", "*", "/", "%" -> {
                if (left.equals("FLOAT") || right.equals("FLOAT")) yield "FLOAT";
                if (left.equals("INT")   && right.equals("INT"))   yield "INT";
                throw new RuntimeException(
                        "Arithmetic operator '" + node.operator + "' not valid for " + left + " and " + right
                );
            }
            case ">", "<", ">=", "<=", "==", "<>" -> {
                if (!left.equals(right)) {
                    throw new RuntimeException(
                            "Cannot compare " + left + " with " + right + "."
                    );
                }
                yield "BOOL";
            }
            case "AND", "OR" -> {
                if (!left.equals("BOOL") || !right.equals("BOOL")) {
                    throw new RuntimeException("AND/OR requires BOOL operands.");
                }
                yield "BOOL";
            }
            default -> throw new RuntimeException("Unknown operator: " + node.operator);
        };
    }

    private String inferUnaryType(UnaryOpNode node) {
        String operandType = inferType(node.operand);
        return switch (node.operator) {
            case "+", "-" -> {
                if (!operandType.equals("INT") && !operandType.equals("FLOAT")) {
                    throw new RuntimeException("Unary +/- requires INT or FLOAT.");
                }
                yield operandType;
            }
            case "NOT" -> {
                if (!operandType.equals("BOOL")) {
                    throw new RuntimeException("NOT requires a BOOL operand.");
                }
                yield "BOOL";
            }
            default -> throw new RuntimeException("Unknown unary operator: " + node.operator);
        };
    }

    // Type Check Helper /////////////////////////////////////////////////////////////////////////////

    private void checkTypeMatch(String expected, String actual, String context) {
        // Allow INT -> FLOAT promotion since float can hold int values no problemos
        if (expected.equals("FLOAT") && actual.equals("INT")) return;
        if (!expected.equals(actual)) {
            throw new RuntimeException(
                    context + ": type mismatch — expected " + expected + ", got " + actual + "."
            );
        }
    }
}


