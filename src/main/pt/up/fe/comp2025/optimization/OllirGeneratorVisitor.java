package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.ast.TypeUtils;

import java.util.stream.Collectors;

import static pt.up.fe.comp2025.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are not expressions.
 */
public class OllirGeneratorVisitor extends AJmmVisitor<Void, String> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";
    private final String NL = "\n";
    private final String L_BRACKET = " {\n";
    private final String R_BRACKET = "}\n";


    private final SymbolTable table;

    private final TypeUtils types;
    private final OptUtils ollirTypes;


    private final OllirExprGeneratorVisitor exprVisitor;

    public OllirGeneratorVisitor(SymbolTable table) {
        this.table = table;
        this.types = new TypeUtils(table);
        this.ollirTypes = new OptUtils(types);
        exprVisitor = new OllirExprGeneratorVisitor(table, ollirTypes);
    }


    @Override
    protected void buildVisitor() {

        addVisit(PROGRAM, this::visitProgram);
        addVisit(CLASS_DECL, this::visitClass);
        addVisit(METHOD_DECL, this::visitMethodDecl);
        addVisit(PARAM, this::visitParam);
        addVisit(RETURN_STMT, this::visitReturn);
        addVisit(ASSIGN_STMT, this::visitAssignStmt);
        addVisit(EXPR_STMT, this::visitExprStmt);
        addVisit(IF_STMT, this::visitIfStmt);
        addVisit(WHILE_STMT, this::visitWhileStmt);
        addVisit(ARRAY_ASSIGN_STMT, this::visitArrayAssignStmt);
        addVisit(BLOCK_STMT, this::visitBlockStmt);

        setDefaultVisit(this::defaultVisit);
    }

    private String visitBlockStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        for (var child : node.getChildren()) {
            code.append(visit(child));
        }

        return code.toString();
    }

    private String visitArrayAssignStmt(JmmNode node, Void unused) {
        var arrayId = node.getChild(0).get("name");
        var array = exprVisitor.visit(node.getChild(0));
        var index = exprVisitor.visit(node.getChild(1));
        var value = exprVisitor.visit(node.getChild(2));

        String ollirType = ollirTypes.toOllirType(node.get("type"));

        StringBuilder code = new StringBuilder();

        code.append(array.getComputation());
        code.append(index.getComputation());
        code.append(value.getComputation());

        if (isField(node.getChild(0)))
            arrayId = array.getCode();

        code.append(arrayId).append("[").append(index.getCode()).append("]").append(ollirType).append(SPACE);
        code.append(ASSIGN).append(ollirType).append(SPACE);
        code.append(value.getCode()).append(END_STMT);

        return code.toString();
    }

    private String visitWhileStmt(JmmNode node, Void unused) {
        int num = ollirTypes.nextWhileLabelNumber();
        String whileLabel = "while" + num;
        num = ollirTypes.nextIfLabelNumber();
        String endIfLabel = "endif" + num;
        var loopBlockStmt = node.getChild(1);

        var condition = exprVisitor.visit(node.getChild(0));
        StringBuilder code = new StringBuilder();

        code.append(whileLabel).append(":").append(NL);
        code.append(condition.getComputation());

        var boolenType = TypeUtils.newBooleanType();
        String ollirBooleanType = ollirTypes.toOllirType(boolenType);
        code.append("if (!").append(ollirBooleanType).append(" ").append(condition.getCode()).append(") goto ").append(endIfLabel).append(END_STMT);
        code.append(visit(loopBlockStmt));
        code.append("goto ").append(whileLabel).append(END_STMT);
        code.append(endIfLabel).append(":").append(NL);

        return code.toString();
    }

    private String visitIfStmt(JmmNode node, Void unused) {
        var condition = exprVisitor.visit(node.getChild(0));

        StringBuilder code = new StringBuilder();

        code.append(condition.getComputation());

        int num = ollirTypes.nextIfLabelNumber();
        String thenLabel = "then" + num;
        String endIfLabel = "endif" + num;
        var thenBlockStmt = node.getChild(1);
        var elseBlockStmt = node.getChild(2);

        code.append("if (").append(condition.getCode()).append(") goto ").append(thenLabel).append(END_STMT);
        code.append(visit(elseBlockStmt));
        code.append("goto ").append(endIfLabel).append(END_STMT);
        code.append(thenLabel).append(":").append(NL);
        code.append(visit(thenBlockStmt));
        code.append(endIfLabel).append(":").append(NL);

        return code.toString();
    }

    private String visitExprStmt(JmmNode node, Void unused) {
        var expr = exprVisitor.visit(node.getChild(0));

        return expr.getComputation();
    }

    private boolean isField(JmmNode node) {
        String methodName = node.getAncestor(METHOD_DECL).get().get("name");
        String varRefExprName = node.get("name");

        for (var localVar : table.getLocalVariables(methodName)) {
            if (localVar.getName().equals(varRefExprName))
                return false;
        }

        for (var param : table.getParameters(methodName)) {
            if (param.getName().equals(varRefExprName))
                return false;
        }

        for (var field : table.getFields()) {
            if (field.getName().equals(varRefExprName))
                return true;
        }

        return false;
    }


    private String visitAssignStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        var rhsNode = node.getChild(0);
        var thisType = node.get("type");
        String typeString = ollirTypes.toOllirType(thisType);
        var varCode = node.get("name") + typeString;

        // Check for direct binary assignments (e.g., i = i + 1, i = 1 + i or i = i - 1)
        if (rhsNode.getKind().equals("BinaryExpr") && !isField(node)) {
            var left = rhsNode.getChild(0);
            var right = rhsNode.getChild(1);

            boolean isDirectAssign = (left.getKind().equals("VarRefExpr") && left.get("name").equals(node.get("name")) &&
                    right.getKind().equals("IntegerLiteral"))
                    || (right.getKind().equals("VarRefExpr") && right.get("name").equals(node.get("name")) &&
                    left.getKind().equals("IntegerLiteral"));

            if (isDirectAssign) {
                var op = rhsNode.get("op");
                var leftExpr = exprVisitor.visit(left);
                var rightExpr = exprVisitor.visit(right);

                code.append(leftExpr.getComputation());
                code.append(rightExpr.getComputation());
                code.append(varCode).append(SPACE).append(ASSIGN).append(typeString).append(SPACE);
                code.append(leftExpr.getCode()).append(SPACE).append(op);
                code.append(typeString).append(SPACE).append(rightExpr.getCode()).append(END_STMT);
                return code.toString();
            }
        }

        var rhs = exprVisitor.visit(rhsNode);

        // code to compute the children
        code.append(rhs.getComputation());

        if (isField(node)) {
            code.append("putfield(this, ").append(varCode).append(", ")
                    .append(rhs.getCode()).append(").V").append(END_STMT);

            return code.toString();
        }

        code.append(varCode);
        code.append(SPACE);

        code.append(ASSIGN);
        code.append(typeString);
        code.append(SPACE);

        code.append(rhs.getCode());

        code.append(END_STMT);

        return code.toString();
    }


    private String visitReturn(JmmNode node, Void unused) {
        String methodName = node.getAncestor(METHOD_DECL).map(method -> method.get("name")).orElseThrow();
        Type retType = table.getReturnType(methodName);

        StringBuilder code = new StringBuilder();


        var expr = node.getNumChildren() > 0 ? exprVisitor.visit(node.getChild(0)) : OllirExprResult.EMPTY;


        code.append(expr.getComputation());
        code.append("ret");
        code.append(ollirTypes.toOllirType(retType));
        code.append(SPACE);

        code.append(expr.getCode());

        code.append(END_STMT);

        return code.toString();
    }


    private String visitParam(JmmNode node, Void unused) {

        var typeCode = ollirTypes.toOllirType(node.getChild(0));
        var id = node.get("name");

        String code = id + typeCode;

        return code;
    }


    private String visitMethodDecl(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder(".method ");

        boolean isPublic = node.getBoolean("isPublic", false);
        if (isPublic) {
            code.append("public ");
        }

        boolean isStatic = node.getBoolean("isStatic", false);
        if (isStatic) {
            code.append("static ");
        }

        var name = node.get("name");
        var params = table.getParameters(name);

        // varargs
        if (!params.isEmpty()) {
            var isVarargs = (boolean) params.getLast().getType().getObject("isVarargs");
            if (isVarargs)
                code.append("varargs ");
        }

        // name
        code.append(name);

        // params
        var paramsCode = params.stream()
                .map(param -> param.getName() + ollirTypes.toOllirType(param.getType()))
                .collect(Collectors.joining(", "));
        code.append("(").append(paramsCode).append(")");

        // type
        var retType = ollirTypes.toOllirType(table.getReturnType(name));
        code.append(retType);
        code.append(L_BRACKET);


        // rest of its children stmts
        var stmtsCode = node.getChildren(STMT).stream()
                .map(this::visit)
                .collect(Collectors.joining("\n   ", "   ", ""));

        code.append(stmtsCode);

        if (node.getChildren(RETURN_STMT).isEmpty())
            code.append("ret.V").append(END_STMT);

        code.append(R_BRACKET);
        code.append(NL);

        return code.toString();
    }


    private String visitClass(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        code.append(NL);
        code.append(table.getClassName());

        // super class
        if (table.getSuper() != null) {
            code.append(" extends ").append(table.getSuper());
        }

        code.append(L_BRACKET);
        code.append(NL);
        code.append(NL);

        // fields
        for (var field : table.getFields()) {
            code.append(".field public ");
            code.append(field.getName());
            code.append(ollirTypes.toOllirType(field.getType()));
            code.append(END_STMT);
        }

        code.append(buildConstructor());
        code.append(NL);

        for (var child : node.getChildren(METHOD_DECL)) {
            var result = visit(child);
            code.append(result);
        }

        code.append(R_BRACKET);

        return code.toString();
    }

    private String buildConstructor() {

        return """
                .construct %s().V {
                    invokespecial(this, "<init>").V;
                }
                """.formatted(table.getClassName());
    }

    private String visitProgram(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        for (String importPath : table.getImports())
            code.append("import ").append(importPath).append(END_STMT);

        node.getChildren().stream()
                .map(this::visit)
                .forEach(code::append);

        return code.toString();
    }

    /**
     * Default visitor. Visits every child node and return an empty string.
     *
     * @param node
     * @param unused
     * @return
     */
    private String defaultVisit(JmmNode node, Void unused) {
        for (var child : node.getChildren()) {
            visit(child);
        }

        return "";
    }
}
