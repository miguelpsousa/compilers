package pt.up.fe.comp2025.optimization;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.inst.*;

import java.util.*;

public class LivenessAnalysis {

    private final Method method;

    private final Map<Instruction, Set<String>> inMap;
    private final Map<Instruction, Set<String>> outMap;
    private final Map<Instruction, Set<String>> defMap;
    private final Map<Instruction, Set<String>> useMap;

    public LivenessAnalysis(Method method) {
        this.inMap = new HashMap<>();
        this.outMap = new HashMap<>();
        this.defMap = new HashMap<>();
        this.useMap = new HashMap<>();
        this.method = method;
    }

    public Map<Instruction, Set<String>> getOutMap() {
        return outMap;
    }

    public Map<Instruction, Set<String>> getDefMap() {
        return defMap;
    }

    public void analyze() {
        List<Instruction> instructions = method.getInstructions();

        for (Instruction inst : instructions) {
            inMap.put(inst, new HashSet<>());
            outMap.put(inst, new HashSet<>());
            // Compute DEF[n] and USE[n]
            defMap.put(inst, getDef(inst));
            useMap.put(inst, getUse(inst));
        }

        boolean changed;
        do {
            changed = false;

            // More efficient to iterate backwards
            for (int i = instructions.size() - 1; i >= 0; i--) {
                Instruction inst = instructions.get(i);

                Set<String> oldIn = new HashSet<>(inMap.get(inst));
                Set<String> oldOut = new HashSet<>(outMap.get(inst));

                // OUT[n] = U IN(successors of n)
                // More efficient to compute OUT first
                Set<String> out = new HashSet<>();
                for (var succ : inst.getSuccessorsAsInst()) {
                    out.addAll(inMap.get(succ));
                }

                // IN[n] = USE[n] U (OUT[n] - DEF[n])
                Set<String> in = new HashSet<>(useMap.get(inst));
                Set<String> outMinusDef = new HashSet<>(out);
                outMinusDef.removeAll(defMap.get(inst));
                in.addAll(outMinusDef);

                // Update IN and OUT
                inMap.put(inst, in);
                outMap.put(inst, out);

                // Check if anything changed
                if (!in.equals(oldIn) || !out.equals(oldOut)) {
                    changed = true;
                }
            }
        } while (changed);
    }

    private Set<String> getDef(Instruction inst) {
        Set<String> def = new HashSet<>();

        if (inst instanceof AssignInstruction assign) {
            Element lhs = assign.getDest();
            if (lhs instanceof Operand op) {
                def.add(op.getName());
            }
        }

        return def;
    }

    private Set<String> getUse(Instruction inst) {
        Set<String> use = new HashSet<>();

        if (inst instanceof AssignInstruction assign) {
            Instruction rhs = assign.getRhs();
            use.addAll(getUsedVarsFromInstruction(rhs));
        }

        if (inst instanceof CallInstruction call) {
            for (Element arg : call.getArguments()) {
                if (arg instanceof Operand op && !op.isLiteral()) {
                    use.add(op.getName());
                }
            }
        }

        if (inst instanceof ReturnInstruction ret) {
            ret.getOperand().ifPresent(op -> {
                if (op instanceof Operand operand && !operand.isLiteral()) {
                    use.add(operand.getName());
                }
            });
        }

        return use;
    }

    private Set<String> getUsedVarsFromInstruction(Instruction inst) {
        Set<String> used = new HashSet<>();

        if (inst instanceof BinaryOpInstruction binOp) {
            Element left = binOp.getLeftOperand();
            Element right = binOp.getRightOperand();

            if (left instanceof Operand op && !op.isLiteral()) used.add(op.getName());
            if (right instanceof Operand op && !op.isLiteral()) used.add(op.getName());

        } else if (inst instanceof UnaryOpInstruction unOp) {
            Element operand = unOp.getOperand();
            if (operand instanceof Operand op && !op.isLiteral()) used.add(op.getName());

        } else if (inst instanceof CallInstruction call) {
            for (Element arg : call.getArguments()) {
                if (arg instanceof Operand op && !op.isLiteral()) used.add(op.getName());
            }
        } else if (inst instanceof SingleOpInstruction single) {
            Element operand = single.getSingleOperand();
            if (operand instanceof Operand op && !op.isLiteral()) used.add(op.getName());
        }

        return used;
    }
}
