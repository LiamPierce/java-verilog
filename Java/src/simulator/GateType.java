package simulator;

// Using the "OUTPUT" and "INPUT" types allows outputs to be named during execution.
public enum GateType {
    INPUT(-1),
    DFF(-1),
    NOT(-1),
    AND(0),
    OR(1),
    NAND(2),
    NOR(3),
    XOR(4),
    UNKNOWN(-1),
    OUTPUT(-1),
    WIRE(-1);

    private final int value;

    private GateType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
