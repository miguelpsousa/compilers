# Compiler Project

| Name                                 | Number    | Contribution (%) |
|--------------------------------------|-----------|------------------|
| Ana Frederica Ferreira e Pereira     | 202108167 | 25                |
| Daniel Gomes Silva                   | 201909935 | 50                |
| Miguel Correia Barros Pereira Sousa  | 202207986 | 25                |


## Extra Features

- We support different array types during semantic analysis.

## 1. Optimizations 

### 1.1. Constant propagation and constant folding
*JmmSemanticsResult optimize(JmmSemanticsResult semanticsResult)*

- **Constant Propagation**  
  Replaces variables with their constant values when possible. This is achieved by:
    - Tracking assignments of constant values to (local) variables
    - Replacing variable references with their corresponding constant values in subsequent expressions

- **Constant Folding**  
  Simplifies constant expressions at compile time. This includes:
    - Evaluating arithmetic operations (e.g., `2 + 3` becomes `5`)
    - Simplifying boolean expressions (e.g., `true && false` becomes `false`)

- **Implementation Details**
    - **Visitors**:
        - `ConstantPropagationVisitor`: Traverses the AST, propagating constant values and replacing variable references with constants
        - `ConstantFoldingVisitor`: Simplifies constant expressions by evaluating them directly in the AST
    - **Optimization Process**:
        - Both visitors are applied iteratively until no further changes are detected in the AST

### 1.2. Register allocation

*OllirResult optimize(OllirResult ollirResult);* 

- **Configuration Check**  
    - Checks the option `–r=<n>` that controls the register allocation to determine the number of registers to be used - `maxRegs`  
    - If it is set to `-1`, return without any modifications

- **Control Flow Graphs**  
  - Calls `ClassUnit.buildCFGs()` to ensure that the proper connections between instructions are formed

- **Register Allocation Process**  
For each method:
  - **Liveness Analysis**  
    - Uses `analyze()` from `LivenessAnalysis` to compute the use, def, live-in and live-out sets for each instruction
  - **Interference Graph**  
    - Uses `buildGraph()` from `InterferenceGraph` to create the interference graph for the method, using the union of the def and live-outs set
  - **Graph Coloring**  
    - Uses `graphColoring()` from `RegisterAllocation` to color the interference graph, assigning registers to variables and building the map `regAllocation`  
    - If the k registers are not enough, we increase the number of registers and repeat the process until we find a solution and report an error
  - **Update Registers**  
    - Uses `updateRegisters()` from `RegisterAllocation` to update the varTable that each OLLIR method has to reflect the new register allocation
  - **Register Allocation Details**  
    - We print the total number of registers needed and the mapping of each variable to its assigned register, as defined in the method's `varTable`

### 1.3. Low Cost Instructions

- `iload_x`, `istore_x`, `astore_x`, `aload_x` (e.g., instead of `iload x`)
- `iconst_0`, `bipush`, `sipush`, `ldc` (load constants to the stack with the appropriate instruction)
- use of `iinc` (replace i=i+1 with i++)
- `iflt`, `ifne`, etc (compare against zero, instead of two values, e.g., `if_icmplt`)

#### Code Example
```java
    private String generateLiteral(LiteralElement literal) {
        limits.increment();

        int intValue = Integer.parseInt(literal.getLiteral());

        if (intValue == -1) {
            return "iconst_m1" + NL;
        } else if (intValue >= 0 && intValue <= 5) {
            return "iconst_" + intValue + NL;
        } else if (intValue >= -128 && intValue <= 127) {
            return "bipush " + intValue + NL;
        } else if (intValue >= -32768 && intValue <= 32767) {
            return "sipush " + intValue + NL;
        } else {
            return "ldc " + literal.getLiteral() + NL;
        }
    }
```