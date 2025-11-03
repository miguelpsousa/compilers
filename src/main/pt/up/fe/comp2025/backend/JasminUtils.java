package pt.up.fe.comp2025.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.type.*;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.specs.util.SpecsCheck;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

import java.util.HashMap;
import java.util.Map;

public class JasminUtils {

    private final OllirResult ollirResult;

    public JasminUtils(OllirResult ollirResult) {
        // Can be useful to have if you expand this class with more methods
        this.ollirResult = ollirResult;
    }


    public String getModifier(AccessModifier accessModifier) {
        return accessModifier != AccessModifier.DEFAULT ?
                accessModifier.name().toLowerCase() + " " :
                "";
    }

    public String getArrayType(Type type) {
        if (type instanceof BuiltinType builtinType) {
            return switch (builtinType.getKind()) {
                case INT32 -> "int";
                default -> throw new NotImplementedException(builtinType.getKind());
            };
        }
        throw new NotImplementedException(type);
    }

    public String getPrefix(Type type) {
        if (type instanceof ArrayType arrayType) {
            return "a";
        }
        if (type instanceof BuiltinType builtinType) {
            return switch (builtinType.getKind()) {
                case INT32, BOOLEAN -> "i";
                default -> throw new NotImplementedException(builtinType.getKind());
            };
        }
        if (type instanceof ClassType classType) {
            return "a";
        }
        throw new NotImplementedException(type);
    }

    public String getDescriptor(Type type) {
        if (type instanceof ArrayType arrayType) {
            return "[" + getDescriptor(arrayType.getElementType());
        }
        if (type instanceof BuiltinType builtinType) {
            return switch (builtinType.getKind()) {
                case INT32 -> "I";
                case BOOLEAN -> "Z";
                case VOID -> "V";
                case STRING -> "Ljava/lang/String;";
            };
        }
        if (type instanceof ClassType classType) {
            return "L" + classType.getName().replace('.', '/') + ";";
        }
        throw new NotImplementedException(type);
    }
}
