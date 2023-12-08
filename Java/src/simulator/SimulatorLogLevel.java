package simulator;

public enum SimulatorLogLevel {
    SIM(0), INFO(1), DEBUG(2), VERBOSE(3), WARNING(0), ERROR(0);

    private final int value;

    private SimulatorLogLevel(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
