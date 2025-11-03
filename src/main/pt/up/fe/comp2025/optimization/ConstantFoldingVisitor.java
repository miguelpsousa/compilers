package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;

import java.util.ArrayList;
import java.util.List;

public class ConstantFoldingVisitor extends AJmmVisitor<Void, Void> {

    private boolean changed;

    public ConstantFoldingVisitor() {
        this.changed = false;
    }

    public boolean didChange() {
        return changed;
    }

    @Override
    protected void buildVisitor() {
        addVisit(Kind.BINARY_EXPR, this::foldBinaryExpr);
        setDefaultVisit(this::defaultVisit);
    }

    private Void foldBinaryExpr(JmmNode node, Void unused) {
        visit(node.getChild(0));
        visit(node.getChild(1));

        var left = node.getChild(0);
        var right = node.getChild(1);
        var op = node.get("op");

        if (Kind.INTEGER_LITERAL.check(left) && Kind.INTEGER_LITERAL.check(right)) {
            int leftVal = Integer.parseInt(left.get("value"));
            int rightVal = Integer.parseInt(right.get("value"));
            int result = 0;

            // Special case for operator "<"
            if (op.equals("<")) {
                boolean comparisonResult = leftVal < rightVal;

                List<String> hierarchy = new ArrayList<>(List.of(Kind.BOOLEAN_LITERAL.toString(), "Expr"));
                JmmNode newNode = new JmmNodeImpl(hierarchy);
                newNode.put("value", Boolean.toString(comparisonResult));
                newNode.put("type", TypeUtils.newBooleanType().toString());
                node.replace(newNode);
            } else {
                switch (op) {
                    case "+":
                        result = leftVal + rightVal;
                        break;
                    case "-":
                        result = leftVal - rightVal;
                        break;
                    case "*":
                        result = leftVal * rightVal;
                        break;
                    case "/":
                        if (rightVal == 0) throw new ArithmeticException("Division by zero");
                        result = leftVal / rightVal;
                        break;
                    default:
                        return null; // Unsupported operator

                }

                // Create a new literal node
                JmmNode newNode = new JmmNodeImpl(left.getHierarchy()); // left or right can be used
                newNode.put("value", Integer.toString(result));
                newNode.put("type", left.get("type"));
                node.replace(newNode);
            }

        } else if (Kind.BOOLEAN_LITERAL.check(left) && Kind.BOOLEAN_LITERAL.check(right)) {
            boolean leftVal = Boolean.parseBoolean(left.get("value"));
            boolean rightVal = Boolean.parseBoolean(right.get("value"));
            boolean result;
            if (op.equals("&&")) {
                result = leftVal && rightVal;
            } else {
                return null; // Unsupported operator for boolean folding
            }

            // Create a new literal node
            JmmNode newNode = new JmmNodeImpl(left.getHierarchy()); // left or right can be used
            newNode.put("value", Boolean.toString(result));
            newNode.put("type", left.get("type"));
            node.replace(newNode);

        } else {
            return null;
        }

        changed = true;

        return null;
    }

    private Void defaultVisit(JmmNode node, Void unused) {
        for (var child : node.getChildren())
            visit(child);

        return null;
    }
}
