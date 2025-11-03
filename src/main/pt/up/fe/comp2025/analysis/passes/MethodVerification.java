package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;
import pt.up.fe.comp.jmm.analysis.table.Symbol;

import java.util.ArrayList;
import java.util.List;

public class MethodVerification extends AnalysisVisitor {
    @Override
    public void buildVisitor() {
        addVisit(Kind.MAIN_METHOD_DECL, this::visitMainMethodDecl);
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.METHOD_CALL_EXPR, this::visitMethodCallExpr);
    }

    private Void visitMainMethodDecl(JmmNode mainMethodDecl, SymbolTable table) {
        var paramTypeName = mainMethodDecl.get("string");
        var methodName = mainMethodDecl.get("name");
        if(!paramTypeName.equals("String") || !methodName.equals("main")) {
            // Create error report
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    mainMethodDecl.getLine(),
                    mainMethodDecl.getColumn(),
                    "Static methods that are not the 'main' method.",
                    null)
            );
        }
        return null;
    }

    private Void visitMethodDecl(JmmNode methodDecl, SymbolTable table) {
        // Checks if varargs are used only in the last parameter of a method declaration
        checkVargars(methodDecl, table);

        var returnStmt = methodDecl.getChildren(Kind.RETURN_STMT);

        if (returnStmt.isEmpty() && methodDecl.get("name").equals("main")){
            return null;
        }

        if (returnStmt.isEmpty() && !methodDecl.get("name").equals("main")) {
            // Create error report
            var message = String.format("Method '%s' without return statement", methodDecl.get("name"));
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    methodDecl.getLine(),
                    methodDecl.getColumn(),
                    message,
                    null)
            );
            return null;
        }

        if (returnStmt.size() > 1) {
            // Create error report
            var message = String.format("Method '%s' with more than one return statement", methodDecl.get("name"));
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    methodDecl.getLine(),
                    methodDecl.getColumn(),
                    message,
                    null)
            );
            return null;
        }

        if (returnStmt.getFirst().getIndexOfSelf() != methodDecl.getNumChildren() - 1) {
            // Create error report
            var message = String.format("'Return' statement is not the last statement in method '%s'.", methodDecl.get("name"));
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    methodDecl.getLine(),
                    methodDecl.getColumn(),
                    message,
                    null)
            );
            return null;
        }

        var returnStmtType = returnStmt.getFirst().get("type");

        if (TypeUtils.getNameType(returnStmtType).equals("imported")) {
            return null;
        }

        if (!methodDecl.get("type").equals(returnStmtType)) {
            // Create error report
            var message = String.format("Return value of type incompatible '%s' with method return type '%s'.",
                    returnStmtType, methodDecl.get("type"));
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    methodDecl.getLine(),
                    methodDecl.getColumn(),
                    message,
                    null)
            );
            return null;
        }

        return null;
    }

    private void checkVargars(JmmNode methodDecl, SymbolTable table) {
        // Get method parameters from symbol table
        List<Symbol> parameters = table.getParameters(methodDecl.get("name"));

        // Checks if varargs are used in any parameter except the last one in a method declaration
        for (int i = 0; i < parameters.size() - 1; i++) {
            var isVararg = (boolean) parameters.get(i).getType().getObject("isVarargs");

            if (isVararg) {
                // Create error report
                var message = String.format("Found varargs before the last parameter of the method '%s'.", methodDecl.get("name"));
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        methodDecl.getLine(),
                        methodDecl.getColumn(),
                        message,
                        null)
                );
                return;
            }
        }

    }

    private Void visitMethodCallExpr(JmmNode methodCallExpr, SymbolTable table) {
        if (TypeUtils.getNameType(methodCallExpr.get("type")).equals("imported")) return null;
        if (table.getSuper() == null) {
            String methodName = methodCallExpr.get("name");
            if (table.getMethods().contains(methodName)) {
                checkArgumentTypes(methodCallExpr, methodName, table);
                return null;
            }
        }

        return null;
    }

    /**
     * Checks if the types of arguments of the call are compatible with the types in the method declaration.
     * If the calling method accepts varargs, it can accept both a variable number of arguments of
     * the same type as an array, or directly an array.
     */
    private void checkArgumentTypes(JmmNode methodCallExpr, String methodName, SymbolTable table) {
        //System.out.println("Checking argument types for method: " + methodName);

        // Get method parameters from symbol table
        List<Symbol> parameters = table.getParameters(methodName);

        // Get arguments from method call node
        List<JmmNode> argumentNodes = getArgumentNodes(methodCallExpr);

        // Method without arguments, nothing to check, return
        if (parameters.isEmpty() && argumentNodes.isEmpty()) {
            return;
        }

        if (argumentNodes.isEmpty()) {
            // Create error report
            var message = String.format("Expected method to receive '%d' arguments, but got '0'", parameters.size());
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    methodCallExpr.getLine(),
                    methodCallExpr.getColumn(),
                    message,
                    null)
            );
            return;
        }

        // Check type compatibility for each argument except the last one
        int i;
        int nArgs = argumentNodes.size();
        for (i = 0; i < parameters.size() - 1; i++) {
            if (nArgs == 0) {
                // Create error report
                var message = String.format("Expected method to receive '%d' arguments, but got '%d'",
                        parameters.size(), argumentNodes.size());
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        methodCallExpr.getLine(),
                        methodCallExpr.getColumn(),
                        message,
                        null)
                );
                return;
            }

            JmmNode argNode = argumentNodes.get(i);
            Type paramType = parameters.get(i).getType();
            if (!argNode.get("type").equals(paramType.toString())) {
                // Create error report
                var message = String.format("Incompatible argument type. " +
                                "Method '%s': Parameter '%d' expects '%s' but received '%s'.",
                        methodName, i, paramType, argNode.get("type"));
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        methodCallExpr.getLine(),
                        methodCallExpr.getColumn(),
                        message,
                        null)
                );
                return;
            }
            nArgs--;
        }

        if (nArgs == 0) {
            // Create error report
            var message = String.format("Expected method to receive '%d' arguments, but got '%d'",
                    parameters.size(), argumentNodes.size());
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    methodCallExpr.getLine(),
                    methodCallExpr.getColumn(),
                    message,
                    null)
            );
            return;
        }

        // Check type compatibility for last argument
        var isVararg = (boolean) parameters.get(i).getType().getObject("isVarargs");
        if (isVararg) {
            // receive a variable of type int array
            if (argumentNodes.get(i).get("type").equals(TypeUtils.newArrayIntType().toString())) return;

            // receive variable number of (int) arguments
            for (int j = i; j < argumentNodes.size(); j++) {
                if (!argumentNodes.get(j).get("type").equals(TypeUtils.newIntType().toString())) {
                    // Create error report
                    var message = String.format("Varargs are limited to type 'int'." +
                                    "Method '%s': Parameter '%d' expects 'int' but received '%s'.",
                            methodName, j, argumentNodes.get(j).get("type"));
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            methodCallExpr.getLine(),
                            methodCallExpr.getColumn(),
                            message,
                            null)
                    );
                    return;
                }
            }
        } else {
            var lastParamType = parameters.get(i).getType();
            if (!argumentNodes.get(i).get("type").equals(lastParamType.toString())) {
                // Create error report
                var message = String.format("Incompatible argument type. " +
                                "Method '%s': Parameter '%d' expects '%s' but received '%s'.",
                        methodName, i, lastParamType, argumentNodes.get(i).get("type"));
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        methodCallExpr.getLine(),
                        methodCallExpr.getColumn(),
                        message,
                        null)
                );
                return;
            }
        }
    }

    /**
     * Extract argument nodes from a method call node.
     */
    private List<JmmNode> getArgumentNodes(JmmNode methodCallNode) {
        List<JmmNode> args = new ArrayList<>();

        if (methodCallNode.getNumChildren() > 1) {
            for (int i = 1; i < methodCallNode.getNumChildren(); i++) {
                args.add(methodCallNode.getChildren().get(i));
            }
        }

        return args;
    }

}


