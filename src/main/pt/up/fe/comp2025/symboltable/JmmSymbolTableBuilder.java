package pt.up.fe.comp2025.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import static pt.up.fe.comp2025.ast.Kind.*;

public class JmmSymbolTableBuilder {

    // In case we want to already check for some semantic errors during symbol table building.
    private List<Report> reports;

    public List<Report> getReports() {
        return reports;
    }

    private static Report newError(JmmNode node, String message) {
        return Report.newError(
                Stage.SEMANTIC,
                node.getLine(),
                node.getColumn(),
                message,
                null);
    }

    public JmmSymbolTable build(JmmNode root) {

        reports = new ArrayList<>();

        var imports = buildImports(root);

        var classDecl = root.getChildren(CLASS_DECL).getFirst();
        SpecsCheck.checkArgument(Kind.CLASS_DECL.check(classDecl), () -> "Expected a class declaration: " + classDecl);

        String className = classDecl.get("name");
        String superClassName = classDecl.getOptional("superClass").orElse(null);
        if (superClassName != null && superClassName.equals(className)) {
            // Create error report
            var message = String.format("Class '%s' cannot extend itself.", className);
            reports.add(Report.newError(
                    Stage.SEMANTIC,
                    classDecl.getLine(),
                    classDecl.getColumn(),
                    message,
                    null)
            );
        }

        if (superClassName != null && !imports.contains(superClassName) ) {
            if (imports.stream().noneMatch(s -> s.endsWith("." + superClassName))){
                // Create error report
                var message = String.format("Class '%s' extends class '%s' that was not imported.", className, superClassName);
                reports.add(Report.newError(
                        Stage.SEMANTIC,
                        classDecl.getLine(),
                        classDecl.getColumn(),
                        message,
                        null)
                );
            }
        }

        var methods = buildMethods(classDecl);
        var returnTypes = buildReturnTypes(classDecl);
        var params = buildParams(classDecl);
        var locals = buildLocals(classDecl);
        var fields = buildFields(classDecl);

        return new JmmSymbolTable(className, methods, returnTypes, params, locals, fields, imports, superClassName);
    }


    private Map<String, Type> buildReturnTypes(JmmNode classDecl) {
        Map<String, Type> map = new HashMap<>();

        for (var method : classDecl.getChildren(METHOD_DECL)) {
            var name = method.get("name");
            if (method.getKind().equals("RegularMethodDecl")) {
                var typeNode = method.getChild(0);
                var returnType = TypeUtils.convertType(typeNode);

                var isVararg = (boolean) returnType.getObject("isVarargs");
                if (isVararg) {
                    // Create report
                    reports.add(Report.newError(
                            Stage.SEMANTIC,
                            typeNode.getLine(),
                            typeNode.getColumn(),
                            "Found varargs outside of method parameters",
                            null)
                    );
                } else
                    map.put(name, returnType);

            } else if (method.getKind().equals("MainMethodDecl")) {
                map.put(name, new Type("void", false));
            }
        }

        return map;
    }


    private Map<String, List<Symbol>> buildParams(JmmNode classDecl) {
        Map<String, List<Symbol>> map = new HashMap<>();

        for (var method : classDecl.getChildren(METHOD_DECL)) {
            var name = method.get("name");
            Set<String> paramNames = new HashSet<>();
            List<Symbol> params = new ArrayList<>();

            for (var param : method.getChildren(PARAM)) {
                var paramName = param.get("name");

                if (!paramNames.add(paramName)) {
                    // Create error report for duplicate parameter
                    var message = String.format("Duplicate parameter '%s' in method '%s'.", paramName, name);
                    reports.add(Report.newError(
                            Stage.SEMANTIC,
                            param.getLine(),
                            param.getColumn(),
                            message,
                            null)
                    );
                }

                var paramType = TypeUtils.convertType(param.getChild(0));
                params.add(new Symbol(paramType, paramName));
            }

            // Special case for main method
            if (method.getKind().equals("MainMethodDecl")) {
                var paramType = TypeUtils.newArrayType("String");
                paramType.putObject("isVarargs", false);
                params.add(new Symbol(paramType, method.get("argName")));
            }

            map.put(name, params);
        }

        return map;
    }

    private Map<String, List<Symbol>> buildLocals(JmmNode classDecl) {
        var map = new HashMap<String, List<Symbol>>();

        for (var method : classDecl.getChildren(METHOD_DECL)) {
            var name = method.get("name");
            Set<String> localNames = new HashSet<>();
            List<Symbol> locals = new ArrayList<>();

            for (var local : method.getChildren(VAR_DECL)) {
                var localName = local.get("name");

                if (!localNames.add(localName)) {
                    // Create error report for duplicate local variable
                    var message = String.format("Duplicate local variable '%s' in method '%s'.", localName, name);
                    reports.add(Report.newError(
                            Stage.SEMANTIC,
                            local.getLine(),
                            local.getColumn(),
                            message,
                            null)
                    );
                }

                var localType = TypeUtils.convertType(local.getChild(0));
                var isVararg = (boolean) localType.getObject("isVarargs");
                if (isVararg) {
                    // Create report
                    reports.add(Report.newError(
                            Stage.SEMANTIC,
                            local.getLine(),
                            local.getColumn(),
                            "Found varargs outside of method parameters",
                            null)
                    );
                } else
                    locals.add(new Symbol(localType, localName));
            }

            map.put(name, locals);
        }

        return map;
    }

    private List<String> buildMethods(JmmNode classDecl) {
        List<String> methods = new ArrayList<>();
        Set<String> methodNames = new HashSet<>();

        for (var method : classDecl.getChildren(METHOD_DECL)) {
            var methodName = method.get("name");

            if (!methodNames.add(methodName)) {
                // Create error report for duplicate method
                var message = String.format("Duplicate method '%s'.", methodName);
                reports.add(Report.newError(
                        Stage.SEMANTIC,
                        method.getLine(),
                        method.getColumn(),
                        message,
                        null)
                );
            }

            methods.add(methodName);
        }

        return methods;
    }

    private List<Symbol> buildFields(JmmNode classDecl) {
        List<Symbol> fields = new ArrayList<>();
        Set<String> fieldNames = new HashSet<>();
        for (var field : classDecl.getChildren(VAR_DECL)) {
            var name = field.get("name");

            if (!fieldNames.add(name)) {
                // Create error report
                var message = String.format("Duplicate field '%s'.", name);
                reports.add(Report.newError(
                        Stage.SEMANTIC,
                        field.getLine(),
                        field.getColumn(),
                        message,
                        null)
                );
            }

            var typeNode = field.getChild(0);
            var fieldType = TypeUtils.convertType(typeNode);
            var isVararg = (boolean) fieldType.getObject("isVarargs");
            if (isVararg) {
                // Create report
                reports.add(Report.newError(
                        Stage.SEMANTIC,
                        field.getLine(),
                        field.getColumn(),
                        "Found varargs outside of method parameters",
                        null)
                );
            } else
                fields.add(new Symbol(fieldType, name));
        }

        return fields;
    }

    private List<String> buildImports(JmmNode root) {
        List<String> imports = new ArrayList<>();
        Set<String> importClassNames = new HashSet<>();

        for (var importDecl : root.getChildren(IMPORT_DECL)) {
            var parts = importDecl.get("path");
            String importPath = String.join(".", parts.substring(1, parts.length() - 1).split(",")).replaceAll("\\s", "");
            String className = importPath.substring(importPath.lastIndexOf('.') + 1);

            if (!importClassNames.add(className)) {
                // Create error report for duplicate import class
                var message = String.format("Duplicate import class '%s'.", className);
                reports.add(Report.newError(
                        Stage.SEMANTIC,
                        importDecl.getLine(),
                        importDecl.getColumn(),
                        message,
                        null)
                );
            } else {
                imports.add(importPath);
            }
        }

        return imports;
    }


}
