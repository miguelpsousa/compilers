package pt.up.fe.comp2025.optimization;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.inst.AssignInstruction;
import org.specs.comp.ollir.inst.Instruction;
import org.specs.comp.ollir.inst.SingleOpInstruction;

import java.util.*;

public class RegisterAllocation {

    private final Method method;
    private final int maxRegs;
    private final InterferenceGraph graph;
    private final Map<String, Integer> regAllocation;

    public RegisterAllocation(InterferenceGraph graph, int maxRegs, Method method) {
        this.method = method;
        this.maxRegs = maxRegs;
        this.graph = graph;
        this.regAllocation = new HashMap<>();
    }

    public int getUsedRegisters() {
        int maxRegister = 0;

        for (int reg : regAllocation.values())
            maxRegister = Math.max(maxRegister, reg);

        // Registers start at 0, so we need to add 1 to the max register used
        if (!regAllocation.isEmpty())
            maxRegister++;

        var offset = method.isStaticMethod() ? 0 : 1;
        offset += method.getParams().size();
        return maxRegister + offset;
    }

    public boolean graphColoring() {
        Map<String, Set<String>> simplifiedGraph = new HashMap<>();

        // Initialize the simplified interference graph excluding "this" and method parameters
        for (String variable : graph.getGraph().keySet()) {
            if (variable.equals("this") || isMethodParam(method, variable)) continue;

            // Copy neighbors excluding "this" and method parameters
            Set<String> filteredNeighbors = new HashSet<>();
            for (String neighbor : graph.getNeighbors(variable)) {
                if (!neighbor.equals("this") && !isMethodParam(method, neighbor)) {
                    filteredNeighbors.add(neighbor);
                }
            }
            simplifiedGraph.put(variable, filteredNeighbors);
        }

        Stack<String> stack = new Stack<>();
        Set<String> processed = new HashSet<>();

        while (processed.size() < simplifiedGraph.size()) {
            boolean removed = false;

            for (String variable : simplifiedGraph.keySet()) {
                if (processed.contains(variable)) continue;

                var neighborCount = simplifiedGraph.get(variable).stream()
                        .filter(neigh -> !processed.contains(neigh))
                        .count();

                if (maxRegs == 0 || neighborCount < maxRegs) {
                    // found a node with less than k edges
                    stack.push(variable);
                    processed.add(variable);
                    removed = true;
                    break;
                }
            }

            if (!removed) {
                // If no node with less than k edges is found, the algorithm cannot proceed
                return false;
            }
        }

        while (!stack.isEmpty()) {
            String variable = stack.pop();
            Set<Integer> takenColors = new HashSet<>();

            for (String neighbor : graph.getNeighbors(variable)) {
                if (regAllocation.containsKey(neighbor)) {
                    takenColors.add(regAllocation.get(neighbor));
                }
            }

            // Assign the lowest available color (register) to the variable
            int assignedColor = 0;
            while (takenColors.contains(assignedColor)) assignedColor++;

            // Check if the assigned color exceeds the maximum number of registers
            if (maxRegs > 0 && assignedColor >= maxRegs) {
                return false;
            }

            regAllocation.put(variable, assignedColor);
        }

        return true;
    }

    public void updateRegisters() {
        var offset = method.isStaticMethod() ? 0 : 1;
        offset += method.getParams().size();

        for (var entry : method.getVarTable().entrySet()) {
            String varName = entry.getKey();
            Descriptor descriptor = entry.getValue();

            if (descriptor.getScope() == VarScope.LOCAL && regAllocation.containsKey(varName)) {
                int virtualReg = regAllocation.get(varName) + offset;
                descriptor.setVirtualReg(virtualReg);
            }
        }

        /*
        // Cleanup: Remove unused temporaries
        var unusedTemps = new ArrayList<String>();
        for (var entry : method.getVarTable().entrySet()) {
            String varName = entry.getKey();
            Descriptor descriptor = entry.getValue();

            if (varName.startsWith("tmp") && !regAllocation.containsKey(varName)) {
                unusedTemps.add(varName);
            }
        }
        for (String temp : unusedTemps) {
            method.getVarTable().remove(temp);
        }
        */
    }


    private boolean isMethodParam(Method method, String varName) {
        return method.getParams().stream()
                .filter(param -> param instanceof Operand)
                .map(param -> ((Operand) param).getName())
                .anyMatch(name -> name.equals(varName));
    }
}