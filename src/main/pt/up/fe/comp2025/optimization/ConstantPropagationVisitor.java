package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp2025.ast.Kind;

import java.util.*;

public class ConstantPropagationVisitor extends AJmmVisitor<Void, Void> {

    private boolean changed;
    private final Map<String, Map<String, JmmNode>> constants;
    private String currentMethod;
    private final SymbolTable table;

    public ConstantPropagationVisitor(SymbolTable table) {
        this.changed = false;
        this.constants = new HashMap<>();
        this.currentMethod = null;
        this.table = table;
    }

    public boolean didChange() {
        return changed;
    }

    @Override
    protected void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.ASSIGN_STMT, this::visitAssignStmt);
        addVisit(Kind.VAR_REF_EXPR, this::visitVarRefExpr);
        addVisit(Kind.WHILE_STMT, this::visitWhileStmt);
        addVisit(Kind.IF_STMT, this::visitIfStmt);
        setDefaultVisit(this::defaultVisit);
    }

    private Void visitMethodDecl(JmmNode node, Void unused) {
        currentMethod = node.get("name");
        constants.putIfAbsent(currentMethod, new HashMap<>());

        for (var child : node.getChildren())
            visit(child);

        return null;
    }

    private Void visitAssignStmt(JmmNode node, Void unused) {
        String varName = node.get("name");

        // Check if the variable is a local variable
        if (!isLocalVariable(varName)) {
            return null;
        }

        // Get the right-hand side of the assignment
        JmmNode rhs = node.getChild(0);

        // If RHS is a constant literal, store it
        if (Kind.INTEGER_LITERAL.check(rhs) || Kind.BOOLEAN_LITERAL.check(rhs)) {
            constants.get(currentMethod).put(varName, rhs);
        } else {
            visit(rhs);
            constants.get(currentMethod).remove(varName);
        }

        return null;
    }

    private Void visitVarRefExpr(JmmNode node, Void unused) {
        String varName = node.get("name");
        var methodConstants = constants.get(currentMethod);

        // Check if this variable has a constant value
        if (methodConstants != null && methodConstants.containsKey(varName)) {
            var constant = methodConstants.get(varName);

            // Replace variable reference with the constant literal
            JmmNode newNode = new JmmNodeImpl(constant.getHierarchy());
            newNode.put("value", constant.get("value"));
            newNode.put("type", constant.get("type"));

            node.replace(newNode);
            changed = true;
        }

        return null;
    }

    private Void visitWhileStmt(JmmNode node, Void unused) {
        // Get the block statement of the while loop
        var blockStmt = node.getChild(1);
        var condition = node.getChild(0);

        for (var child : blockStmt.getChildren()) {
            if (Kind.ASSIGN_STMT.check(child)) {
                constants.get(currentMethod).remove(child.get("name"));
            }
        }

        visit(blockStmt);
        visit(condition);

        return null;
    }

    private Void visitIfStmt(JmmNode node, Void unused) {
        visit(node.getChild(0));

        var blockStmt1 = node.getChild(1);
        for (var child : blockStmt1.getChildren()) {
            visit(child);
            if (Kind.ASSIGN_STMT.check(child)) {
                constants.get(currentMethod).remove(child.get("name"));
            }
        }

        var blockStmt2 = node.getChild(2);
        for (var child : blockStmt2.getChildren()) {
            visit(child);
            if (Kind.ASSIGN_STMT.check(child)) {
                constants.get(currentMethod).remove(child.get("name"));
            }
        }

        return null;
    }

    private Void defaultVisit(JmmNode node, Void unused) {
        for (var child : node.getChildren())
            visit(child);

        return null;
    }

    private boolean isLocalVariable(String varName) {
        return table.getLocalVariables(currentMethod).stream()
                .anyMatch(var -> var.getName().equals(varName));
    }
}