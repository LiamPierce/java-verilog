package simulator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.naming.InvalidNameException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GateTest {

    private Gate gate;

    @BeforeEach
    void setUp() throws InvalidNameException, InvalidGateException {

    }

    @Test
    void testEvaluateStateTableLookupAND() throws InvalidNameException, InvalidGateException {
        Gate inputA = new Gate("IN1", GateType.INPUT, 0);
        inputA.setState(1);

        Gate inputB = new Gate("IN2", GateType.INPUT, 0);
        inputB.setState(1);

        Gate inputC = new Gate("IN3", GateType.INPUT, 0);
        inputC.setState(0);

        Gate uut = new Gate("AND", GateType.AND, 0);
        uut.addFan(GateFanType.FANIN, inputA);
        uut.addFan(GateFanType.FANIN, inputB);

        uut.evaluateState(GateEvaluationMethod.TABLE_LOOKUP);

        int expectedState = 1; // EXPECT AND(1, 1) = 1.
        assertEquals(expectedState, uut.getState());

        uut.addFan(GateFanType.FANIN, inputC);

        uut.evaluateState(GateEvaluationMethod.TABLE_LOOKUP);

        expectedState = 0; // EXPECT AND(1, 1, 0) = 0.
        assertEquals(expectedState, uut.getState());
        System.out.println("Passed 3 input test.");

        inputC.setState(1);

        uut.evaluateState(GateEvaluationMethod.TABLE_LOOKUP);

        expectedState = 1; // EXPECT AND(1, 1, 0) = 0.
        assertEquals(expectedState, uut.getState());
        System.out.println("Passed 3 input test.");

        inputC.setState(2);

        uut.evaluateState(GateEvaluationMethod.TABLE_LOOKUP);

        expectedState = 2; // EXPECT AND(1, 1, X) = X.
        assertEquals(expectedState, uut.getState());
        System.out.println("Passed X input test.");
    }

    @Test
    void testEvaluateStateTableLookupDFF() throws InvalidNameException, InvalidGateException {
        Gate inputA = new Gate("IN1", GateType.INPUT, 0);
        inputA.setState(1);

        Gate uut = new Gate("DFF", GateType.DFF, 0);
        uut.addFan(GateFanType.FANIN, inputA);

        uut.evaluateState(GateEvaluationMethod.TABLE_LOOKUP);

        int expectedState = 1;
        assertEquals(expectedState, uut.getState());
        System.out.println("Passed DFF test.");
    }

    @Test
    void testEvaluateStateTableLookupNOT() throws InvalidNameException, InvalidGateException {
        Gate inputA = new Gate("IN1", GateType.INPUT, 0);
        inputA.setState(1);

        Gate uut = new Gate("NOT", GateType.NOT, 0);
        uut.addFan(GateFanType.FANIN, inputA);

        uut.evaluateState(GateEvaluationMethod.TABLE_LOOKUP);

        int expectedState = 0;
        assertEquals(expectedState, uut.getState());
        System.out.println("Passed NOT test.");

        inputA.setState(0);

        expectedState = 0; // EXPECT AND(1, 1) = 1.
        assertEquals(expectedState, uut.getState());
        System.out.println("Passed NOT test.");
    }

    @Test
    void testEvaluateStateTableLookupXOR() throws InvalidNameException, InvalidGateException {
        Gate inputA = new Gate("IN1", GateType.INPUT, 0);
        inputA.setState(1);

        Gate inputB = new Gate("IN2", GateType.INPUT, 0);
        inputB.setState(1);

        Gate inputC = new Gate("IN3", GateType.INPUT, 0);
        inputC.setState(0);

        Gate inputD = new Gate("IN4", GateType.INPUT, 0);
        inputD.setState(0);

        Gate uut = new Gate("XOR", GateType.XOR, 0);
        uut.addFan(GateFanType.FANIN, inputA);
        uut.addFan(GateFanType.FANIN, inputB);

        uut.evaluateState(GateEvaluationMethod.TABLE_LOOKUP);

        int expectedState = 0;
        assertEquals(expectedState, uut.getState());
        System.out.println("Passed XOR test.");

        uut.addFan(GateFanType.FANIN, inputC);

        uut.evaluateState(GateEvaluationMethod.TABLE_LOOKUP);

        expectedState = 0; // EXPECT XOR(1, 1) = 1.
        assertEquals(expectedState, uut.getState());
        System.out.println("Passed XOR test.");

        inputB.setState(0);

        uut.evaluateState(GateEvaluationMethod.TABLE_LOOKUP);

        expectedState = 1; // EXPECT XOR(1, 1) = 1.
        assertEquals(expectedState, uut.getState());
        System.out.println("Passed XOR test.");

        //

        uut.addFan(GateFanType.FANIN, inputD);

        uut.evaluateState(GateEvaluationMethod.TABLE_LOOKUP);

        expectedState = 1; // EXPECT XOR(1, 1) = 1.
        assertEquals(expectedState, uut.getState());
        System.out.println("Passed XOR test.");

        //

        inputD.setState(1);
        uut.evaluateState(GateEvaluationMethod.TABLE_LOOKUP);

        expectedState = 0; // EXPECT XOR(1, 1) = 1.
        assertEquals(expectedState, uut.getState());
        System.out.println("Passed XOR test.");
    }

    @Test
    void testEvaluateStateInputScan() {
        // Similar setup as the previous test but use INPUT_SCAN evaluation method
        // gate.evaluateState(GateEvaluationMethod.INPUT_SCAN);
        // Verify the gate state as above
    }

    // Additional tests can be added here for different types of gates and evaluation methods
}
