package pt.up.fe.comp2025.backend;

public class Limits {

    private int maxLocals;

    private int maxStack;
    private int currentStack;

    public Limits() {
        this.maxLocals = -1;
        this.maxStack = 0;
        this.currentStack = 0;
    }

    public void updateLocals(int regNumber) {
        int actualNumber = regNumber + 1;

        maxLocals = Math.max(maxLocals, actualNumber);
    }

    public int getMaxLocals() {
        //if (maxLocals == -1) {
            //return 1;
        //}
        return maxLocals;
    }

    public int getMaxStack() {
        return maxStack;
    }

    public void increment(int value) {
        currentStack += value;
        maxStack = Math.max(maxStack, currentStack);
    }

    public void increment() {
        increment(1);
    }

    public void decrement(int value) {
        currentStack -= value;
    }

    public void decrement() {
        decrement(1);
    }
}
