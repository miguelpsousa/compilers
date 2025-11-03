package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp2025.ast.TypeUtils;

import java.util.ArrayList;
import java.util.List;

import static pt.up.fe.comp2025.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are expressions.
 */
public class OllirExprGeneratorVisitor extends PreorderJmmVisitor<Void, OllirExprResult> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";
    private final String NL = "\n";

    private final SymbolTable table;

    private final TypeUtils types;
    private final OptUtils ollirTypes;


    public OllirExprGeneratorVisitor(SymbolTable table, OptUtils ollirTypes) {
        this.table = table;
        this.types = new TypeUtils(table);
        this.ollirTypes = ollirTypes;
    }


    @Override
    protected void buildVisitor() {
        addVisit(VAR_REF_EXPR, this::visitVarRef);
        addVisit(BINARY_EXPR, this::visitBinExpr);
        addVisit(INTEGER_LITERAL, this::visitInteger);
        addVisit(BOOLEAN_LITERAL, this::visitBoolean);
        addVisit(METHOD_CALL_EXPR, this::visitMethodCallExpr);
        addVisit(NEW_INT_ARRAY_EXPR, this::visitNewIntArrayExpr);
        addVisit(LENGTH_EXPR, this::visitLengthExpr);
        addVisit(ARRAY_ACCESS_EXPR, this::visitArrayAccessExpr);
        addVisit(NEW_OBJECT_EXPR, this::visitNewObjectExpr);
        addVisit(THIS_EXPR, this::visitThisExpr);
        addVisit(PAREN_EXPR, this::visitParenExpr);
        addVisit(NOT_EXPR, this::visitNotExpr);
        addVisit(ARRAY_EXPR, this::visitArrayExpr);

        setDefaultVisit(this::defaultVisit);
    }

    private OllirExprResult visitArrayExpr(JmmNode node, Void unused) {
        String ollirType = ollirTypes.toOllirType(node.get("type"));
        var tmp = ollirTypes.nextTemp();
        String code = tmp + ollirType;
        // jmm array elems are of type int
        String ollirIntType = ollirTypes.toOllirType(TypeUtils.newIntType());

        StringBuilder computation = new StringBuilder();
        var arrayElems = node.getChildren();

        computation.append(code).append(SPACE).append(ASSIGN).append(ollirType).append(SPACE)
                .append("new(array, ").append(arrayElems.size()).append(ollirIntType).append(")").append(ollirType).append(END_STMT);

        for (int i = 0; i < arrayElems.size(); i++) {
            var elem = visit(arrayElems.get(i));
            computation.append(elem.getComputation());
            computation.append(tmp).append("[").append(i).append(ollirIntType).append("]").append(ollirIntType).append(SPACE)
                    .append(ASSIGN).append(ollirIntType).append(SPACE).append(elem.getCode()).append(END_STMT);
        }

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitNotExpr(JmmNode node, Void unused) {
        var expr = visit(node.getChild(0));

        StringBuilder computation = new StringBuilder();
        computation.append(expr.getComputation());

        String ollirType = ollirTypes.toOllirType(node.get("type"));
        String code = ollirTypes.nextTemp() + ollirType;

        computation.append(code).append(SPACE).append(ASSIGN).append(ollirType).append(SPACE)
                .append("!").append(ollirType).append(SPACE).append(expr.getCode()).append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitParenExpr(JmmNode node, Void unused) {
        var child = visit(node.getChild(0));
        return new OllirExprResult(child.getCode(), child.getComputation());
    }

    private OllirExprResult visitThisExpr(JmmNode node, Void unused) {
        String ollirType = ollirTypes.toOllirType(node.get("type"));
        String code = "this" + ollirType;
        return new OllirExprResult(code);
    }

    private OllirExprResult visitNewObjectExpr(JmmNode node, Void unused) {
        String className = node.get("name");
        String ollirType = ollirTypes.toOllirType(node.get("type"));
        String code = ollirTypes.nextTemp() + ollirType;

        StringBuilder computation = new StringBuilder();
        computation.append(code).append(SPACE).append(ASSIGN).append(ollirType).append(SPACE)
                .append("new").append("(").append(className).append(")").append(ollirType).append(END_STMT);
        computation.append("invokespecial(").append(code).append(", \"<init>\").V").append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitArrayAccessExpr(JmmNode node, Void unused) {
        var array = visit(node.getChild(0));
        var index = visit(node.getChild(1));

        StringBuilder computation = new StringBuilder();
        computation.append(array.getComputation());
        computation.append(index.getComputation());

        String ollirType = ollirTypes.toOllirType(node.get("type"));
        String code = ollirTypes.nextTemp() + ollirType;

        computation.append(code).append(SPACE).append(ASSIGN).append(ollirType).append(SPACE)
                .append(array.getCode()).append("[").append(index.getCode()).append("]").append(ollirType).append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitLengthExpr(JmmNode node, Void unused) {
        var array = visit(node.getChild(0));

        StringBuilder computation = new StringBuilder();
        computation.append(array.getComputation());

        String ollirType = ollirTypes.toOllirType(node.get("type"));
        String code = ollirTypes.nextTemp() + ollirType;

        computation.append(code).append(SPACE).append(ASSIGN).append(ollirType).append(SPACE)
                .append("arraylength(").append(array.getCode()).append(")").append(ollirType).append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitNewIntArrayExpr(JmmNode node, Void unused) {
        var size = visit(node.getChild(0));

        StringBuilder computation = new StringBuilder();
        computation.append(size.getComputation());

        String ollirType = ollirTypes.toOllirType(node.get("type"));
        String code = ollirTypes.nextTemp() + ollirType;

        computation.append(code).append(SPACE).append(ASSIGN).append(ollirType).append(SPACE)
                .append("new").append("(").append("array").append(", ").append(size.getCode()).append(")")
                .append(ollirType).append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitMethodCallExpr(JmmNode node, Void unused) {

        var caller = visit(node.getChild(0));

        StringBuilder computation = new StringBuilder();
        computation.append(caller.getComputation());

        List<String> argCodes = new ArrayList<>();
        var numArgNodes = node.getChildren().size() - 1;
        for (int i = 1; i <= numArgNodes; i++) {
            var arg = visit(node.getChild(i));
            computation.append(arg.getComputation());
            argCodes.add(arg.getCode());
        }

        var methodName = node.get("name");
        var methodType = TypeUtils.getTypeFromString(node.get("type"));

        var isVarargs = false;
        var params = table.getParameters(methodName);
        if (params != null && !params.isEmpty())
            isVarargs = (boolean) params.getLast().getType().getObject("isVarargs");

        if (isVarargs) {
            String ollirIntArrayType = ollirTypes.toOllirType(TypeUtils.newArrayIntType());
            String ollirIntType = ollirTypes.toOllirType(TypeUtils.newIntType());
            String tmp = ollirTypes.nextTemp();
            String tmpCode = tmp + ollirIntArrayType;
            var numArrayElems = numArgNodes - params.size() + 1;
            numArgNodes = params.size();

            computation.append(tmpCode).append(SPACE).append(ASSIGN).append(ollirIntArrayType).append(SPACE)
                    .append("new(array, ").append(numArrayElems).append(ollirIntType).append(")").append(ollirIntArrayType).append(END_STMT);

            for (int i = 0; i < numArrayElems; i++) {
                var elemCode = argCodes.get(argCodes.size() - numArrayElems + i);
                computation.append(tmp).append("[").append(i).append(ollirIntType).append("]").append(ollirIntType).append(SPACE)
                        .append(ASSIGN).append(ollirIntType).append(SPACE).append(elemCode).append(END_STMT);
            }

            argCodes.subList(argCodes.size() - numArrayElems, argCodes.size()).clear();
            argCodes.add(tmpCode);
        }

        // if method type is imported, we try to get the type from the assign statement
        // if it is not present, we set it to void
        var assignStmt = node.getAncestor(ASSIGN_STMT);
        if (methodType.getName().equals("imported")) {
            if (assignStmt.isPresent())
                methodType = TypeUtils.getTypeFromString(assignStmt.get().get("type"));
            else
                methodType = TypeUtils.newVoidType();
        }
        var methodOllirType = ollirTypes.toOllirType(methodType);

        // if the method is void or the return value is not used, we don't need to assign the result to tmp
        boolean isVoid = methodOllirType.equals(".V");
        boolean isReturnUsed = !node.getParent().getKind().equals(EXPR_STMT.toString());
        String code = (isVoid || !isReturnUsed) ? "" : ollirTypes.nextTemp() + methodOllirType;
        if (!isVoid && isReturnUsed)
            computation.append(code).append(SPACE).append(ASSIGN).append(methodOllirType).append(SPACE);

        var callerType = TypeUtils.getTypeFromString(node.getChild(0).get("type"));
        if (callerType.getName().equals("imported"))
            computation.append("invokestatic");
        else if (methodName.equals(table.getClassName()))
            computation.append("invokespecial");
        else
            computation.append("invokevirtual");

        computation.append("(").append(caller.getCode()).append(", ").append(String.format("\"%s\"", methodName));
        for (int i = 1; i <= numArgNodes; i++) {
            computation.append(", ");
            computation.append(argCodes.get(i - 1));
        }
        computation.append(")").append(methodOllirType).append(END_STMT);

        return new OllirExprResult(code, computation.toString());
    }


    private OllirExprResult visitInteger(JmmNode node, Void unused) {
        var intType = TypeUtils.newIntType();
        String ollirIntType = ollirTypes.toOllirType(intType);
        String code = node.get("value") + ollirIntType;
        return new OllirExprResult(code);
    }

    private OllirExprResult visitBoolean(JmmNode node, Void unused) {
        var booleanType = TypeUtils.newBooleanType();
        String ollirBooleanType = ollirTypes.toOllirType(booleanType);
        String code = (node.get("value").equals("true") ? "1" : "0") + ollirBooleanType;
        return new OllirExprResult(code);
    }

    private OllirExprResult visitShortCircuitAnd(JmmNode node) {
        var lhs = visit(node.getChild(0));
        var rhs = visit(node.getChild(1));

        StringBuilder computation = new StringBuilder();
        computation.append(lhs.getComputation());

        String ollirType = ollirTypes.toOllirType(node.get("type"));
        String code = ollirTypes.nextTemp() + ollirType;

        int num = ollirTypes.nextIfLabelNumber();
        String thenLabel = "then" + num;
        String endIfLabel = "endif" + num;

        computation.append("if (").append(lhs.getCode()).append(") goto ").append(thenLabel).append(END_STMT);
        computation.append(code).append(SPACE).append(ASSIGN).append(ollirType).append(SPACE)
                .append("0").append(ollirType).append(END_STMT);
        computation.append("goto ").append(endIfLabel).append(END_STMT);
        computation.append(thenLabel).append(":").append(NL);
        computation.append(rhs.getComputation());
        computation.append(code).append(SPACE).append(ASSIGN).append(ollirType).append(SPACE)
                .append(rhs.getCode()).append(END_STMT);
        computation.append(endIfLabel).append(":").append(NL);

        return new OllirExprResult(code, computation);
    }


    private OllirExprResult visitBinExpr(JmmNode node, Void unused) {
        var op = node.get("op");

        if (op.equals("&&"))
            return visitShortCircuitAnd(node);

        var lhs = visit(node.getChild(0));
        var rhs = visit(node.getChild(1));

        StringBuilder computation = new StringBuilder();

        // code to compute the children
        computation.append(lhs.getComputation());
        computation.append(rhs.getComputation());

        // code to compute self
        Type resType = types.getExprType(node);
        String resOllirType = ollirTypes.toOllirType(resType);
        String code = ollirTypes.nextTemp() + resOllirType;

        computation.append(code).append(SPACE)
                .append(ASSIGN).append(resOllirType).append(SPACE)
                .append(lhs.getCode()).append(SPACE);

        Type type = types.getExprType(node);
        computation.append(op).append(ollirTypes.toOllirType(type)).append(SPACE)
                .append(rhs.getCode()).append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    private boolean isField(JmmNode node) {
        String methodName = node.getAncestor(METHOD_DECL).get().get("name");
        String varRefExprName = node.get("name");

        for (var localVar: table.getLocalVariables(methodName)) {
            if (localVar.getName().equals(varRefExprName))
                return false;
        }

        for (var param: table.getParameters(methodName)) {
            if (param.getName().equals(varRefExprName))
                return false;
        }

        for (var field: table.getFields()) {
            if (field.getName().equals(varRefExprName))
                return true;
        }

        return false;
    }


    private OllirExprResult visitVarRef(JmmNode node, Void unused) {

        var id = node.get("name");
        Type type = types.getExprType(node);

        if (type.getName().equals("imported"))
            return new OllirExprResult(id);

        String ollirType = ollirTypes.toOllirType(type);
        String code = id + ollirType;

        StringBuilder computation = new StringBuilder();
        if(isField(node)) {
            String tmp = ollirTypes.nextTemp() + ollirType;

            computation.append(tmp).append(SPACE).append(ASSIGN).append(ollirType).append(SPACE)
                    .append("getfield(this, ").append(code).append(")").append(ollirType).append(END_STMT);
            code = tmp;
        }

        return new OllirExprResult(code, computation);
    }

    /**
     * Default visitor. Visits every child node and return an empty result.
     *
     * @param node
     * @param unused
     * @return
     */
    private OllirExprResult defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return OllirExprResult.EMPTY;
    }

}
