package lexor;

import lexor.nodes.*;
import java.util.List;

public class SemanticAnalyzer {
    private SymbolTable symbolTable = new SymbolTable();

    public SymbolTable getSymbolTable() {
        return symbolTable;
    }

    public void analyze(ProgramNode program) {
        symbolTable = new SymbolTable();
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
            case IncDecNode    n -> analyzeIncDec(n);
            default -> throw new LexorException(
                    LexorException.Phase.SEMANTIC, node.line, "Unknown node type: " + node.getClass().getSimpleName()
            );
        }
    }

    // Declarations //////////////////////////////////////////////////////////////////////////////

    private void analyzeDeclare(DeclareNode node) {
        if (node.value != null) {
            ValueType valueType = inferType(node.value);
            checkTypeMatch(node.type, valueType, "Declaration of '" + node.name + "'", node.line);
        }
        symbolTable.declare(node.name, node.type, node.line, node.type.defaultValue());
    }

    // Assignment ///////////////////////////////////////////////////////////////////////////////

    private void analyzeAssign(AssignNode node) {
        ValueType varType = symbolTable.getType(node.name, LexorException.Phase.SEMANTIC, node.line);
        ValueType valueType = inferType(node.value);
        checkTypeMatch(varType, valueType, "Assignment to '" + node.name + "'", node.line);
    }

    // Printing ///////////////////////////////////////////////////////////////////////////////////

    private void analyzePrint(PrintNode node) {
        for (Node part : node.parts) {
            inferType(part);
        }
    }

    // Scanning ////////////////////////////////////////////////////////////////////////////////////

    private void analyzeScan(ScanNode node) {
        for (String var : node.variables) {
            symbolTable.getType(var, LexorException.Phase.SEMANTIC, node.line);
        }
    }

    // If ///////////////////////////////////////////////////////////////////////////////////////////

    private void analyzeIf(IfNode node) {
        ValueType condType = inferType(node.condition);
        if (condType != ValueType.BOOL) {
            throw new LexorException(LexorException.Phase.SEMANTIC, node.line, "IF condition must be BOOL, got " + condType + ".");
        }
        analyzeBlock(node.thenBranch);
        for (IfNode elseIf : node.elseIfBranches) analyzeIf(elseIf);
        if (node.elseBranch != null) analyzeBlock(node.elseBranch);
    }

    // For ///////////////////////////////////////////////////////////////////////////////////////////

    private void analyzeFor(ForNode node) {
        analyzeNode(node.initialization);
        ValueType condType = inferType(node.condition);
        if (condType != ValueType.BOOL) {
            throw new LexorException(LexorException.Phase.SEMANTIC, node.line, "FOR condition must be BOOL, got " + condType + ".");
        }
        analyzeNode(node.update);
        analyzeBlock(node.body);
    }

    // Repeat /////////////////////////////////////////////////////////////////////////////////////////

    private void analyzeRepeat(RepeatNode node) {
        ValueType condType = inferType(node.condition);
        if (condType != ValueType.BOOL) {
            throw new LexorException(LexorException.Phase.SEMANTIC, node.line, "REPEAT WHEN condition must be BOOL, got " + condType + ".");
        }
        analyzeBlock(node.body);
    }

    // Block //////////////////////////////////////////////////////////////////////////////////////////

    private void analyzeBlock(List<Node> block) {
        for (Node node : block) analyzeNode(node);
    }

    // Type Inference methods /////////////////////////////////////////////////////////////////////////

    public ValueType inferType(Node node) {
        return switch (node) {
            case LiteralNode  n -> n.type;
            case VariableNode n -> symbolTable.getType(n.name, LexorException.Phase.SEMANTIC, n.line);
            case BinaryOpNode n -> inferBinaryType(n);
            case UnaryOpNode  n -> inferUnaryType(n);
            case AssignNode   n -> {
                analyzeAssign(n);
                yield symbolTable.getType(n.name, LexorException.Phase.SEMANTIC, n.line);
            }
            case IncDecNode n -> {
                analyzeIncDec(n);
                yield symbolTable.getType(n.name, LexorException.Phase.SEMANTIC, n.line);
            }
            default -> throw new LexorException(
                    LexorException.Phase.SEMANTIC, node.line, "Cannot infer type of: " + node.getClass().getSimpleName()
            );
        };
    }

    private ValueType inferBinaryType(BinaryOpNode node) {
        ValueType left = inferType(node.left);
        ValueType right = inferType(node.right);

        return switch (node.operator) {
            case "+", "-", "*", "/", "%" -> {
                if (!left.isNumeric() || !right.isNumeric()) {
                    throw new LexorException(LexorException.Phase.SEMANTIC, node.line,
                            "Arithmetic operator '" + node.operator + "' requires numeric operands, got "
                                    + left + " and " + right + ".");
                }
                yield left == ValueType.FLOAT || right == ValueType.FLOAT ? ValueType.FLOAT : ValueType.INT;
            }
            case ">", "<", ">=", "<=", "==", "<>" -> {
                boolean numeric = left.isNumeric() && right.isNumeric();
                boolean equality = node.operator.equals("==") || node.operator.equals("<>");
                boolean scalarEquality = equality && left == right
                        && (left == ValueType.BOOL || left == ValueType.CHAR);
                if (!numeric && !scalarEquality) {
                    throw new LexorException(
                            LexorException.Phase.SEMANTIC, node.line,
                            "Cannot compare " + left + " with " + right + " using '" + node.operator + "'."
                    );
                }
                yield ValueType.BOOL;
            }
            case "AND", "OR" -> {
                if (left != ValueType.BOOL || right != ValueType.BOOL) {
                    throw new LexorException(LexorException.Phase.SEMANTIC, node.line, "AND/OR requires BOOL operands.");
                }
                yield ValueType.BOOL;
            }
            default -> throw new LexorException(LexorException.Phase.SEMANTIC, node.line, "Unknown operator: " + node.operator);
        };
    }

    private ValueType inferUnaryType(UnaryOpNode node) {
        ValueType operandType = inferType(node.operand);
        return switch (node.operator) {
            case "+", "-" -> {
                if (!operandType.isNumeric()) {
                    throw new LexorException(LexorException.Phase.SEMANTIC, node.line, "Unary +/- requires INT or FLOAT.");
                }
                yield operandType;
            }
            case "NOT" -> {
                if (operandType != ValueType.BOOL) {
                    throw new LexorException(LexorException.Phase.SEMANTIC, node.line, "NOT requires a BOOL operand.");
                }
                yield ValueType.BOOL;
            }
            default -> throw new LexorException(LexorException.Phase.SEMANTIC, node.line, "Unknown unary operator: " + node.operator);
        };
    }

    // Type Check Helper /////////////////////////////////////////////////////////////////////////////

    private void checkTypeMatch(ValueType expected, ValueType actual, String context, int line) {
        if (!expected.accepts(actual)) {
            throw new LexorException(
                    LexorException.Phase.SEMANTIC, line, context + ": type mismatch — expected " + expected + ", got " + actual + "."
            );
        }
    }

    private void analyzeIncDec(IncDecNode node) {
        ValueType type = symbolTable.getType(node.name, LexorException.Phase.SEMANTIC, node.line);
        if (!type.isNumeric()) {
            throw new LexorException(LexorException.Phase.SEMANTIC, node.line,
                    "Increment/decrement requires INT or FLOAT, got " + type + "."
            );
        }
    }
}


