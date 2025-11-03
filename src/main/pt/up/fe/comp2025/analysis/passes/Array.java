package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;

/**
 * Checks ...
 *
 */
public class Array extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(Kind.ARRAY_ACCESS_EXPR, this::visitArrayAccessExpr);
        addVisit(Kind.ARRAY_EXPR, this::visitArrayExpr);
        addVisit(Kind.LENGTH_EXPR, this::visitLengthExpr);
        addVisit(Kind.NEW_INT_ARRAY_EXPR, this::visitNewIntArrayExpr);
    }

    private Void visitNewIntArrayExpr(JmmNode newIntArrayExpr, SymbolTable table) {
        var size = newIntArrayExpr.getChild(0);
        if (!size.get("type").equals(TypeUtils.newIntType().toString())) {
            // Create error report
            var message = String.format("Array size must be of type int but found '%s'", size.get("type"));
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    newIntArrayExpr.getLine(),
                    newIntArrayExpr.getColumn(),
                    message,
                    null)
            );
        }

        return null;
    }

    private Void visitArrayAccessExpr(JmmNode arrayAccessExpr, SymbolTable table) {
        var arrayIndex = arrayAccessExpr.getChild(1);
        if (arrayIndex.get("type").equals(TypeUtils.newIntType().toString())) {
            return null;
        }

        // Create error report
        var message = String.format("Array access index must be of type integer but found '%s'", arrayIndex.get("type"));
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
        if (arrayExpr.getChildren().isEmpty()) return null;
        var arrayExpType = arrayExpr.get("type");
        var type = TypeUtils.newType(TypeUtils.getNameType(arrayExpType));
        for (var elem : arrayExpr.getChildren()) {
            visit(elem, table);
            if (!elem.get("type").equals(type.toString())) {
                // Create error report
                var message = String.format("Array elements must be of type '%s' but found '%s'", type, elem.get("type"));
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        elem.getLine(),
                        elem.getColumn(),
                        message,
                        null)
                );
            }
        }
        return null;
    }

    private Void visitLengthExpr(JmmNode lengthExpr, SymbolTable table) {
        var expr = lengthExpr.getChild(0);
        if (!expr.get("type").equals(TypeUtils.newArrayIntType().toString())) {
            // Create error report
            var message = String.format("Expected an array but found '%s' instead.", expr.get("type"));
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    lengthExpr.getLine(),
                    lengthExpr.getColumn(),
                    message,
                    null)
            );
        }

        return null;
    }

}
