package pt.up.fe.comp2025.ast;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.symboltable.JmmSymbolTable;

import java.util.regex.*;

/**
 * Utility methods regarding types.
 */
public class TypeUtils {


    private final JmmSymbolTable table;

    private static final String MULT = "*";
    private static final String DIV = "/";
    private static final String PLUS = "+";
    private static final String MINUS = "-";
    private static final String LT = "<";
    private static final String AND = "&&";
    private static final String NOT = "!";

    public TypeUtils(SymbolTable table) {
        this.table = (JmmSymbolTable) table;
    }

    public static Type newIntType() {
        return new Type("int", false);
    }

    public static Type newArrayIntType() {
        return new Type("int", true);
    }

    public static Type newArrayType(String name) {
        return new Type(name, true);
    }

    public static Type newBooleanType() {
        return new Type("boolean", false);
    }

    public static Type newType(String name) {
        return new Type(name, false);
    }

    public static Type newVoidType() {
        return new Type("void", false);
    }

    public static String getNameType(String type) {
        // "Type[name=int, isArray=false]"
        Pattern pattern = Pattern.compile("name=([^,\\]]+)");
        Matcher matcher = pattern.matcher(type);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    public static String getIsArrayType(String type) {
        // "Type[name=int, isArray=false]"
        Pattern pattern = Pattern.compile("isArray=([^,\\]]+)");
        Matcher matcher = pattern.matcher(type);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    public static Type getTypeFromString(String type) {
        return new Type(getNameType(type), Boolean.parseBoolean(getIsArrayType(type)));
    }

    public static Type convertType(JmmNode typeNode) {

        // TODO: When you support new types, this must be updated
        var name = typeNode.get("name");
        var isArray = (Boolean.parseBoolean(typeNode.get("isArray")) || Boolean.parseBoolean(typeNode.get("isVarargs")));
        Type type = new Type(name, isArray);
        if (Boolean.parseBoolean(typeNode.get("isVarargs")))
            type.putObject("isVarargs", true);
        else
            type.putObject("isVarargs", false);
        return type;
    }


    /**
     * Gets the {@link Type} of an arbitrary expression.
     *
     * @param expr
     * @return
     */
    public Type getExprType(JmmNode expr) {
        // TODO: Update when there are new types

        switch (Kind.fromString(expr.getKind())) {
            case BINARY_EXPR:
                return getBinaryExprType(expr);
            case ASSIGN_STMT, ARRAY_ASSIGN_STMT, VAR_REF_EXPR, VAR_DECL, PARAM:
                return getVarType(expr);
            default:
                throw new IllegalArgumentException("Unsupported expression type: " + expr.getKind());
        }
    }

    private static Type getBinaryExprType(JmmNode binaryExpr) {
        var operator = binaryExpr.get("op");
        return switch (operator) {
            case MULT, DIV, PLUS, MINUS -> newIntType();
            case NOT, AND, LT -> newBooleanType();
            default -> throw new RuntimeException("Unknown operator " + operator);
        };
    }

    private Type getVarType(JmmNode node) {
        var varName = node.get("name");

        if (node.getAncestor(Kind.METHOD_DECL).isPresent()) {
            var method = node.getAncestor(Kind.METHOD_DECL).get().get("name");

            // Check if the variable is a local variable
            for (var localVar : table.getLocalVariables(method)) {
                if (localVar.getName().equals(varName))
                    return localVar.getType();
            }

            // Check if the variable is a parameter
            for (var param : table.getParameters(method)) {
                if (param.getName().equals(varName))
                    return param.getType();
            }
        }

        // Check if the variable is a field
        for (var field : table.getFields()) {
            if (field.getName().equals(varName))
                return field.getType();
        }

        // Check if the variable is an import
        for (var importName : table.getImports()) {
            if (importName.equals(varName) || importName.endsWith("." + varName)) {
                return TypeUtils.newType("imported");
            }
        }

        return null;
    }
}
