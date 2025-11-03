package pt.up.fe.comp2025.optimization;

import org.specs.comp.ollir.Method;
import org.specs.comp.ollir.inst.Instruction;
import org.specs.comp.ollir.*;

import java.util.*;

public class InterferenceGraph {

    private final Method method;

    private final Map<String, Set<String>> graph;
    private final Map<Instruction, Set<String>> outMap;
    private final Map<Instruction, Set<String>> defMap;

    public InterferenceGraph(Map<Instruction, Set<String>> outMap, Map<Instruction, Set<String>> defMap, Method method) {
        this.graph = new HashMap<>();
        this.outMap = new HashMap<>(outMap);
        this.defMap = new HashMap<>(defMap);
        this.method = method;
    }

    public Map<String, Set<String>> getGraph() {
        return graph;
    }

    public Set<String> getNeighbors(String var) {
        return graph.getOrDefault(var, Collections.emptySet());
    }

    private void addNode(String var) {
        graph.putIfAbsent(var, new HashSet<>());
    }

    private void addEdge(String var1, String var2) {
        if (var1.equals(var2)) return; // no self edges

        addNode(var1);
        addNode(var2);

        graph.get(var1).add(var2);
        graph.get(var2).add(var1);
    }

    public void buildGraph() {
        for (var var : method.getVarTable().keySet()) {
            if (method.getVarTable().get(var).getScope() == VarScope.LOCAL) {
                addNode(var);
            }
        }

        for (var entry : outMap.entrySet()) {
            Instruction inst = entry.getKey();

            var out = entry.getValue();
            var def = defMap.get(inst);

            // def U live-out
            Set<String> interfereSet = new HashSet<>(out);
            interfereSet.addAll(def);

            for (String var1 : interfereSet) {
                for (String var2 : interfereSet) {
                    addEdge(var1, var2);
                }
            }
        }
    }
}
