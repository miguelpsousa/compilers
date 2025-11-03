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


public class OperatorType extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(Kind.BINARY_EXPR, this::visitBinaryExpr);
    }

    private Void visitBinaryExpr(JmmNode binaryExpr, SymbolTable table) {
        var type = binaryExpr.get("type");
        var typeLeftOperand = binaryExpr.getChild(0).get("type");
        var typeRightOperand = binaryExpr.getChild(1).get("type");

        if (binaryExpr.get("op").equals("<")) {
            if (typeLeftOperand.equals(TypeUtils.newIntType().toString()) &&
                    typeRightOperand.equals(TypeUtils.newIntType().toString())) {
                return null;
            } else {
                // Create error report
                var message = String.format("Operator '<' can only be used with integer types, but got '%s' and '%s'", typeLeftOperand, typeRightOperand);
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        binaryExpr.getLine(),
                        binaryExpr.getColumn(),
                        message,
                        null)
                );
            }
        }

        if (type.equals(typeLeftOperand) && type.equals(typeRightOperand)) return null;

        // Create error report
        var message = String.format("Expected both operands of type '%s', got '%s' and '%s' instead", type, typeLeftOperand, typeRightOperand);
        addReport(Report.newError(
                Stage.SEMANTIC,
                binaryExpr.getLine(),
                binaryExpr.getColumn(),
                message,
                null)
        );

        return null;
    }


}
