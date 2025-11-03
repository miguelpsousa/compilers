package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.CompilerConfig;

import java.util.Collections;

public class JmmOptimizationImpl implements JmmOptimization {

    @Override
    public OllirResult toOllir(JmmSemanticsResult semanticsResult) {

        // Create visitor that will generate the OLLIR code
        var visitor = new OllirGeneratorVisitor(semanticsResult.getSymbolTable());

        // Visit the AST and obtain OLLIR code
        var ollirCode = visitor.visit(semanticsResult.getRootNode());

        System.out.println("\nOLLIR:\n\n" + ollirCode);

        return new OllirResult(semanticsResult, ollirCode, Collections.emptyList());
    }

    @Override
    public JmmSemanticsResult optimize(JmmSemanticsResult semanticsResult) {
        // Check if optimization is enabled (option "-o")
        if (!CompilerConfig.getOptimize(semanticsResult.getConfig()))
            return semanticsResult;

        var ast = semanticsResult.getRootNode();

        // Print AST before optimization
        //System.out.println("\nAST BEFORE OPTIMIZATION:\n\n" + ast.toTree());

        boolean changed;
        do {
            // Apply constant propagation
            var propagationVisitor = new ConstantPropagationVisitor(semanticsResult.getSymbolTable());
            propagationVisitor.visit(ast);

            // Apply constant folding
            var foldingVisitor = new ConstantFoldingVisitor();
            foldingVisitor.visit(ast);

            changed = propagationVisitor.didChange() || foldingVisitor.didChange();

        } while (changed);

        // Print AST after optimization
        //System.out.println("\nAST AFTER OPTIMIZATION:\n\n" + ast.toTree());

        return semanticsResult;
    }

    @Override
    public OllirResult optimize(OllirResult ollirResult) {
        // Check the option "–r=<n>" that controls the register allocation
        int configMaxRegs = CompilerConfig.getRegisterAllocation(ollirResult.getConfig());
        int maxRegs;
        int usedRegs = 0;

        // If n is -1, return the result without optimizing (default value)
        if (configMaxRegs == -1)
            return ollirResult;

        // call buildCFGs() to ensure that the proper connections between instructions are formed
        ollirResult.getOllirClass().buildCFGs();
        var classUnit = ollirResult.getOllirClass();

        for (var method : classUnit.getMethods()) {
            maxRegs = configMaxRegs;

            var livenessAnalysis = new LivenessAnalysis(method);
            livenessAnalysis.analyze();

            var interferenceGraph = new InterferenceGraph(livenessAnalysis.getOutMap(), livenessAnalysis.getDefMap(), method);
            interferenceGraph.buildGraph();

            boolean success;
            do {
                var registerAllocation = new RegisterAllocation(interferenceGraph, maxRegs, method);
                success = registerAllocation.graphColoring();

                if (!success) {
                    maxRegs++;
                } else {
                    usedRegs = registerAllocation.getUsedRegisters();
                    registerAllocation.updateRegisters();
                }
            } while (!success);

            if (maxRegs > configMaxRegs) {
                // Create error report
                var message = String.format("The specified limit of '%d' local variables is insufficient for method '%s'. " +
                        "A minimum of '%d' local variables is required.", configMaxRegs, method.getMethodName(), maxRegs);
                ollirResult.getReports().add(
                        Report.newError(
                                Stage.OPTIMIZATION,
                                0,
                                0,
                                message,
                                null)
                );

                return ollirResult;
            }

            // Print register allocation details
            System.out.println("Register allocation for method `" + method.getMethodName() + "`: "
                    + usedRegs + " registers are needed");
            for (var entry : method.getVarTable().entrySet()) {
                String varName = entry.getKey();
                var descriptor = entry.getValue();
                System.out.println("Variable " + varName + " assigned to register #" + descriptor.getVirtualReg());
            }
            System.out.println();
        }

        return ollirResult;
    }


}
