package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;

/**
 * Annotates each expression node with its type.
 */
public class AddType extends AnalysisVisitor {

    private String currentMethod;
    private final TypeUtils types;

    public AddType(SymbolTable table) {
        this.types = new TypeUtils(table);
    }

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.PARAM, this::visitVarDecl);
        addVisit(Kind.VAR_DECL, this::visitVarDecl);
        addVisit(Kind.BINARY_EXPR, this::visitBinaryExpr);
        addVisit(Kind.INTEGER_LITERAL, this::visitIntegerLiteral);
        addVisit(Kind.BOOLEAN_LITERAL, this::visitBooleanLiteral);
        addVisit(Kind.VAR_REF_EXPR, this::visitVarRefExpr);
        addVisit(Kind.ARRAY_ACCESS_EXPR, this::visitArrayAccessExpr);
        addVisit(Kind.ARRAY_EXPR, this::visitArrayExpr);
        addVisit(Kind.ASSIGN_STMT, this::visitAssignStmt);
        addVisit(Kind.ARRAY_ASSIGN_STMT, this::visitArrayAssignStmt);
        addVisit(Kind.NEW_INT_ARRAY_EXPR, this::visitNewIntArrayExpr);
        addVisit(Kind.NEW_OBJECT_EXPR, this::visitNewObjectExpr);
        addVisit(Kind.METHOD_CALL_EXPR, this::visitMethodCallExpr);
        addVisit(Kind.LENGTH_EXPR, this::visitLengthExpr);
        addVisit(Kind.NOT_EXPR, this::visitNotExpr);
        addVisit(Kind.THIS_EXPR, this::visitThisExpr);
        addVisit(Kind.RETURN_STMT, this::visitReturnStmt);
        addVisit(Kind.PAREN_EXPR, this::visitParenExpr);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        if (currentMethod.equals("main"))
            method.put("type", TypeUtils.newVoidType().toString());
        else
            method.put("type", table.getReturnType(currentMethod).toString());

        return null;
    }

    private Void visitVarDecl(JmmNode varDecl, SymbolTable table) {
        Type varType = types.getExprType(varDecl);
        if (varDecl.getChild(0).getKind().equals("ClassType")) {
            var varTypeName = TypeUtils.getNameType(varType.toString());

            // Special case for String
            if (varTypeName.equals("String")) {
                varDecl.put("type", varType.toString());
                return null;
            }

            // Check if the variable is a class
            if (varTypeName.equals(table.getClassName())) {
                varDecl.put("type", varType.toString());
                return null;
            }

            // Check if the variable is an import
            for (var importName : table.getImports()) {
                if (importName.equals(varTypeName) || importName.endsWith("." + varTypeName)) {
                    varDecl.put("type", varType.toString());
                    return null;
                }
            }
            varDecl.put("type", TypeUtils.newType("invalid").toString());

            // Create error report
            var message = String.format("Undeclared type '%s', probably missing import", varTypeName);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    varDecl.getLine(),
                    varDecl.getColumn(),
                    message,
                    null)
            );
        } else
            varDecl.put("type", varType.toString());

        return null;
    }

    private Void visitBinaryExpr(JmmNode binaryExpr, SymbolTable table) {
        Type type = types.getExprType(binaryExpr);
        binaryExpr.put("type", type.toString());
        return null;
    }

    private Void visitParenExpr(JmmNode parenExpr, SymbolTable table) {
        var expr = parenExpr.getChild(0);
        if (!expr.hasAttribute("type"))
            visit(expr, table);
        parenExpr.put("type", expr.get("type"));
        return null;
    }

    private Void visitIntegerLiteral(JmmNode integerLiteral, SymbolTable table) {
        integerLiteral.put("type", TypeUtils.newIntType().toString());
        return null;
    }

    private Void visitBooleanLiteral(JmmNode booleanLiteral, SymbolTable table) {
        booleanLiteral.put("type", TypeUtils.newBooleanType().toString());
        return null;
    }

    private Void visitNotExpr(JmmNode notExpr, SymbolTable table) {
        var expr = notExpr.getChild(0);
        if (!expr.hasAttribute("type"))
            visit(expr, table);
        var booleanType = TypeUtils.newBooleanType();
        if (!expr.get("type").equals(booleanType.toString())) {
            // Create error report
            var message = String.format("Type error on children of operator '!', " +
                    "expected type compatible with '%s' and got '%s'", booleanType, expr.get("type"));
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    notExpr.getLine(),
                    notExpr.getColumn(),
                    message,
                    null)
            );
            notExpr.put("type", TypeUtils.newType("invalid").toString());
            return null;
        }

        notExpr.put("type", TypeUtils.newBooleanType().toString());
        return null;
    }

    private Void visitVarRefExpr(JmmNode varRefExpr, SymbolTable table) {
        Type varRefExprType = types.getExprType(varRefExpr);

        if (varRefExprType != null) {
            varRefExpr.put("type", varRefExprType.toString());
            return null;
        }

        varRefExpr.put("type", TypeUtils.newType("invalid").toString());

        // Create error report
        var message = String.format("Variable '%s' not found.", varRefExpr.get("name"));
        addReport(Report.newError(
                Stage.SEMANTIC,
                varRefExpr.getLine(),
                varRefExpr.getColumn(),
                message,
                null)
        );

        return null;
    }

    private Void visitArrayAccessExpr(JmmNode arrayAccessExpr, SymbolTable table) {
        var arrayId = arrayAccessExpr.getChild(0);
        visit(arrayId, table);
        if (arrayId.get("type").equals(TypeUtils.newArrayIntType().toString())) {
            arrayAccessExpr.put("type", TypeUtils.newIntType().toString());
            return null;
        } else if (arrayId.get("type").equals(TypeUtils.newArrayType("String").toString())) {
            arrayAccessExpr.put("type", TypeUtils.newType("String").toString());
            return null;
        }

        arrayAccessExpr.put("type", TypeUtils.newType("invalid").toString());
        // Create error report
        var message = String.format("Array access done over an int/String array expected, got '%s' instead", arrayId.get("type"));
        addReport(Report.newError(
                Stage.SEMANTIC,
                arrayAccessExpr.getLine(),
                arrayAccessExpr.getColumn(),
                message,
                null)
        );

        return null;
    }

    private Void visitArrayExpr(JmmNode arrayExpr, SymbolTable table) {
        // arrays are always of type int[]
        arrayExpr.put("type", TypeUtils.newArrayIntType().toString());

        return null;
    }

    private Void visitAssignStmt(JmmNode assignStmt, SymbolTable table) {
        // assignee = assigned

        Type assigneeType = types.getExprType(assignStmt);
        if (assigneeType == null) {
            assignStmt.put("type", TypeUtils.newType("invalid").toString());

            // Create error report
            var message = String.format("Variable '%s' does not exist.", assignStmt.get("name"));
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    assignStmt.getLine(),
                    assignStmt.getColumn(),
                    message,
                    null)
            );
            return null;
        }

        var assigned = assignStmt.getChild(0);
        visit(assigned, table);

        // Check if the type of the assignee is compatible with the assigned
        var assignedTypeName = TypeUtils.getNameType(assigned.get("type"));

        if (assigneeType.toString().equals(assigned.get("type"))) {
            assignStmt.put("type", assigneeType.toString());
            return null;
        }

        if (assigneeType.getName().equals(table.getSuper()) && assignedTypeName.equals(table.getClassName())) {
            assignStmt.put("type", assigneeType.toString());
            return null;
        } else if (assigneeType.getName().equals(table.getClassName()) && assignedTypeName.equals(table.getSuper())) {
            //  a = new B() NOT valid!
            // Because A extends B, B can’t extend A, and so we can infer this is not correct!

            // Create error report
            var message = String.format("Type of the assignee must be compatible with the assigned.'" +
                            "%s' extends '%s', '%s' can’t extend '%s'.",
                    table.getClassName(), table.getSuper(), table.getSuper(), table.getClassName());
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    assignStmt.getLine(),
                    assignStmt.getColumn(),
                    message,
                    null)
            );

            assignStmt.put("type", TypeUtils.newType("invalid").toString());
            return null;
        }

        if (assigneeType.getName().equals(table.getClassName()) && table.getImports().contains(assignedTypeName)) {
            assignStmt.put("type", assigneeType.toString());
            return null;
        }

        if (table.getImports().contains(assigneeType.getName()) && table.getImports().contains(assignedTypeName)) {
            assignStmt.put("type", assigneeType.toString());
            return null;
        }

        if (table.getImports().contains(assigneeType.getName()) && assignedTypeName.equals(table.getClassName())) {
            if (!assigneeType.getName().equals(table.getSuper())){
                assignStmt.put("type", TypeUtils.newType("invalid").toString());
                // Create error report
                var message = String.format("Type of the assignee must be compatible with the assigned. " +
                                "'%s' does not extend '%s'",
                        assignedTypeName, assigneeType.getName());
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        assignStmt.getLine(),
                        assignStmt.getColumn(),
                        message,
                        null)
                );
                return null;
            }
            assignStmt.put("type", assigneeType.toString());
            return null;
        }


        if (assignedTypeName.equals("invalid")) {
            assignStmt.put("type", TypeUtils.newType("invalid").toString());
            return null;
        }

        // TODO
        if (assignedTypeName.equals("imported")) {
            assignStmt.put("type", assigneeType.toString());
            return null;
        }


        assignStmt.put("type", TypeUtils.newType("invalid").toString());

        // Create error report
        var message = String.format("Type of the assignee must be compatible with the assigned. '%s' cannot be converted to '%s'",
                assigneeType, assigned.get("type"));
        addReport(Report.newError(
                Stage.SEMANTIC,
                assignStmt.getLine(),
                assignStmt.getColumn(),
                message,
                null)
        );

        return null;
    }

    private Void visitArrayAssignStmt(JmmNode arrayAssignStmt, SymbolTable table) {
        if (!arrayAssignStmt.getChild(0).getKind().equals(Kind.VAR_REF_EXPR.toString())) {
            // Create error report
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    arrayAssignStmt.getLine(),
                    arrayAssignStmt.getColumn(),
                    "Assignee must be a variable reference",
                    null)
            );
            arrayAssignStmt.put("type", TypeUtils.newType("invalid").toString());
            return null;
        }

        var assigneeType = types.getExprType(arrayAssignStmt.getChild(0));
        // variable reference does not exist, but we already reported it
        if (assigneeType == null)
            return null;
        if (!assigneeType.toString().equals(TypeUtils.newArrayIntType().toString())) {
            // Create error report
            var message = String.format("Expected an int array but found '%s' instead.", assigneeType);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    arrayAssignStmt.getLine(),
                    arrayAssignStmt.getColumn(),
                    message,
                    null)
            );
            arrayAssignStmt.put("type", TypeUtils.newType("invalid").toString());
            return null;
        }

        arrayAssignStmt.put("type", TypeUtils.newIntType().toString());

        return null;
    }

    private Void visitNewIntArrayExpr(JmmNode newIntArrayExpr, SymbolTable table) {
        newIntArrayExpr.put("type", TypeUtils.newArrayIntType().toString());
        return null;
    }

    private Void visitNewObjectExpr(JmmNode newObjectExpr, SymbolTable table) {
        var className = newObjectExpr.get("name");
        newObjectExpr.put("type", TypeUtils.newType(className).toString());
        return null;
    }

    private Void visitThisExpr(JmmNode thisExpr, SymbolTable table) {
        var staticMethod = thisExpr.getAncestor(Kind.METHOD_DECL).get().get("isStatic");
        if (!Boolean.parseBoolean(staticMethod)) {
            thisExpr.put("type", TypeUtils.newType(table.getClassName()).toString());
            return null;
        }

        thisExpr.put("type", TypeUtils.newType("invalid").toString());
        // Create error report
        var message = String.format("'This' expression cannot be used in a static method: '%s'", currentMethod);
        addReport(Report.newError(
                Stage.SEMANTIC,
                thisExpr.getLine(),
                thisExpr.getColumn(),
                message,
                null)
        );

        return null;
    }

    private Void visitMethodCallExpr(JmmNode methodCallExpr, SymbolTable table) {
        for (var child : methodCallExpr.getChildren())
            visit(child, table);
        var object = methodCallExpr.getChild(0);
        var objectType = TypeUtils.getNameType(object.get("type"));

        if (objectType.equals(table.getClassName()) && table.getMethods().contains(methodCallExpr.get("name"))) {
            var returnType = table.getReturnType(methodCallExpr.get("name"));
            methodCallExpr.put("type", returnType.toString());
            return null;
        }

        if (objectType.equals("imported")
                || table.getImports().contains(objectType)
                || table.getImports().contains(table.getSuper())
                || objectType.equals("String")) {
            methodCallExpr.put("type", TypeUtils.newType("imported").toString());
            return null;
        }

        methodCallExpr.put("type", TypeUtils.newType("invalid").toString());

        // Create error report
        var message = String.format("Object of type '%s' has no method named '%s'.", objectType, methodCallExpr.get("name"));
        addReport(Report.newError(
                Stage.SEMANTIC,
                methodCallExpr.getLine(),
                methodCallExpr.getColumn(),
                message,
                null)
        );


        return null;
    }

    private Void visitReturnStmt(JmmNode returnStmt, SymbolTable table) {
        if (currentMethod.equals("main")) {
            // Create error report
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    returnStmt.getLine(),
                    returnStmt.getColumn(),
                    "'Return' statement cannot be used in main method.",
                    null)
            );
        }
        var expr = returnStmt.getChild(0);
        visit(expr, table);
        if (expr.hasAttribute("type")) {
            returnStmt.put("type", expr.get("type"));
        }
        return null;
    }

    private Void visitLengthExpr(JmmNode lengthExpr, SymbolTable table) {
        if (!lengthExpr.get("name").equals("length")) {
            lengthExpr.put("type", TypeUtils.newType("invalid").toString());
            // Create error report
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    lengthExpr.getLine(),
                    lengthExpr.getColumn(),
                    "Access to properties that are not 'length'.",
                    null)
            );
            return null;
        }
        lengthExpr.put("type", TypeUtils.newIntType().toString());

        return null;
    }


}
