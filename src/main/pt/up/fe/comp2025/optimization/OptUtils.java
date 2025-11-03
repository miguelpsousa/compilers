package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.specs.util.collections.AccumulatorMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

import static pt.up.fe.comp2025.ast.Kind.TYPE;

/**
 * Utility methods related to the optimization middle-end.
 */
public class OptUtils {


    private final AccumulatorMap<String> temporaries;
    private int ifLabelNumber;
    private int whileLabelNumber;

    private final TypeUtils types;

    public OptUtils(TypeUtils types) {
        this.types = types;
        this.temporaries = new AccumulatorMap<>();
        this.ifLabelNumber = -1;
        this.whileLabelNumber = -1;
    }


    public String nextTemp() {

        return nextTemp("tmp");
    }

    public String nextTemp(String prefix) {

        // Subtract 1 because the base is 1
        var nextTempNum = temporaries.add(prefix) - 1;

        return prefix + nextTempNum;
    }

    public int nextIfLabelNumber() {

        return ++ifLabelNumber;
    }

    public int nextWhileLabelNumber() {

        return ++whileLabelNumber;
    }


    public String toOllirType(JmmNode typeNode) {

        TYPE.checkOrThrow(typeNode);

        return toOllirType(types.convertType(typeNode));
    }

    public String toOllirType(Type type) {
        return toOllirType(type.getName(), type.isArray());
    }

    public String toOllirType(String type) {
        var typeName = TypeUtils.getNameType(type);
        var isArray = TypeUtils.getIsArrayType(type);
        return toOllirType(typeName, Boolean.parseBoolean(isArray));
    }

    private String toOllirType(String typeName, boolean isArray) {

        String type = (isArray ? ".array" : "") + "." + switch (typeName) {
            case "int" -> "i32";
            case "boolean" -> "bool";
            case "String" -> "String";
            case "void" -> "V";
            default -> typeName;
        };

        return type;
    }


}
