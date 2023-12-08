package simulator;

import javax.naming.InvalidNameException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Simulator {

    public int maxLogLevel = 3;

    private Map<String, Boolean> inputNames = new HashMap<>();
    private Map<String, Boolean> outputNames = new HashMap<>();

    private ArrayList<Gate> inputGates = new ArrayList<>();
    private ArrayList<Gate> outputGates = new ArrayList<>();
    private ArrayList<Gate> stateGates = new ArrayList<>();

    private Queue<Gate> gateFanQueue = new ArrayDeque<>();
    private Queue<ArrayList<String>> fanInQueues = new ArrayDeque<>();
    private Queue<ArrayList<String>> fanOutQueues = new ArrayDeque<>();

    private ArrayList<ArrayList<Integer>> inputVectors = new ArrayList<>();

    private Map<String, LinkedList<Gate>> schedulePrecomputes = new HashMap<>();

    private boolean gatesReady = false;
    private boolean inputsReady = false;

    private int simulationTime = 0; // "Time" is more like "Tick" in this. Just increments.

    private void log(Object x, SimulatorLogLevel logLevel) {
        if (logLevel.getValue() <= maxLogLevel) {
            System.out.println("["+logLevel.toString()+"][@"+ Instant.now().truncatedTo(ChronoUnit.MICROS)+"] "+x.toString());
        }
    }

    private void log(Object x) {
        this.log(x, SimulatorLogLevel.INFO);
    }

    /**
     * Resets the simulation.
     */
    private void resetGateStates() {
        simulationTime = 0;
        for(Gate g : Gate.getValidGates()) {
            // In class, all flip flops initialized to 0.
            if (g.getType() == GateType.DFF) {
                g.setState(2);
            } else { // X otherwise.
                g.setState(2);
            }
        }
    }

    /**
     * Runs the simulation.
     *
     * @param evaluationMethod The method for evaluating gate states.
     */
    public void start(GateScheduleMethod scheduleMethod, GateEvaluationMethod evaluationMethod) {
        if (!gatesReady || !inputsReady) {
            this.log("The simulator is not yet properly configured to run.");
        }

        this.resetGateStates();

        long initTime = System.nanoTime();
        long adjustedDuration = 0;

        ArrayList<Integer> previousInputVector = new ArrayList<>(Collections.nCopies(inputGates.size(), 2));
        // Loop through each "frame" of input vector.
        for (ArrayList<Integer> inputVector : inputVectors) {
            long frameStartTime = System.nanoTime();

            // Because of the way the propagations are structured,
            // it becomes easy to propagate correctly using a list of extended fans.
            ArrayList<Gate> sensitivities = new ArrayList<>();

            // Obviously, a frame must start by setting the input gates to the input vector.
            for (int i = 0; i < inputVector.size(); i++) {
                int inputVectorState = inputVector.get(i);

                Gate inputGate = this.inputGates.get(i);
                inputGate.setState(inputVectorState);

                if (inputVectorState != previousInputVector.get(i)) {
                    // Add the full propagation path to the propagations array.
                    sensitivities.add(inputGate);
                }
            }
            propagate(sensitivities, scheduleMethod, evaluationMethod);

            long frameDuration = System.nanoTime() - frameStartTime;
            long stateStartTime = System.nanoTime();
            this.log("["+evaluationMethod.toString()+"] Execution time: " + frameDuration + " ns"); // Tends to have about 125 ns of overhead.
            printSystemState();

            // Once the propagation for the inputs has completed, we can check the propagation for the DFFs.
            for (Gate g : stateGates) {
                if (g.getWireDriver().getState() != g.getState()) {
                    // Add the full propagation path to the propagations array.
                    sensitivities.add(g);
                }
            }
            propagate(sensitivities, scheduleMethod, evaluationMethod);

            long stateDuration = System.nanoTime() - stateStartTime;
            this.log("["+evaluationMethod.toString()+"] State update time: " + stateDuration + " ns"); // Tends to have about 125 ns of overhead.

            simulationTime++;
            adjustedDuration += frameDuration;
            previousInputVector = inputVector;
        }

        long totalDuration = System.nanoTime() - initTime;
        this.log("["+evaluationMethod.toString()+"] Total execution time: " + totalDuration + " ns. Execution time adjusted for logging code: " + adjustedDuration + " ns.");
    }

    /**
     *
     * Schedules and calculates gate values through fan out.
     *
     * @param sensitivities List of gates to fan out propagate from.
     * @param scheduleMethod The method the simulator should choose to schedule gates.
     * @param evaluationMethod The method the simulator should use to calculate gates.
     */
    private void propagate(ArrayList<Gate> sensitivities, GateScheduleMethod scheduleMethod, GateEvaluationMethod evaluationMethod) {
        if (scheduleMethod == GateScheduleMethod.NAIVE) {
            naivePropagation(evaluationMethod);
        } else if (scheduleMethod == GateScheduleMethod.BREADTH) {
            schedulePropagation(sensitivities, evaluationMethod);
            sensitivities.clear();
        } else if (scheduleMethod == GateScheduleMethod.PRECOMPUTE) {
            schedulePrecomputePropagation(sensitivities, evaluationMethod);
            sensitivities.clear();
        }
    }

    /**
     * This method is the core of the simulator.
     *
     * @param sensitivities An array list of gates that need to be propagated.
     */
    private void schedulePropagation(ArrayList<Gate> sensitivities, GateEvaluationMethod evaluationMethod) {

        Set<Integer> visited = new HashSet<>();

        Queue<Gate> gateProcessQueue = new ArrayDeque<>();
        for (Gate sensitive : sensitivities) {
            gateProcessQueue.add(sensitive);
        }

        while (gateProcessQueue.size() > 0) {

            Gate currentGate = gateProcessQueue.poll();
            System.out.println("Branching on : " + currentGate);
            currentGate.evaluateState(evaluationMethod);
            visited.add(currentGate.getId());

            for (Gate f : currentGate.getRawFan(GateFanType.FANOUT)) {
                System.out.println("Scheduling : " + f);
                if (!visited.contains(f.getId())) {
                    gateProcessQueue.add(f);
                }
            }

        }
    }

    /**
     * This method is the core of the simulator.
     *
     * @param sensitivities An array list of gates that need to be propagated.
     */
    private void schedulePrecomputePropagation(ArrayList<Gate> sensitivities, GateEvaluationMethod evaluationMethod) {

        Set<Integer> visited = new HashSet<>();
        for (Gate sensitive : sensitivities) {
            ListIterator<Gate> iterator = sensitive.getPropagationPath().listIterator();
            while (iterator.hasNext()) {
                iterator.next().evaluateState(evaluationMethod);
            }
        }

    }

    /**
     * This method is the core of the simulator. Just updates every gate by level. Great way to test if something's wrong.
     */
    private void naivePropagation(GateEvaluationMethod evaluationMethod) {
        for (int i = 0; i <= Gate.getMaxGateLevel(); i++) {
            for (Gate g : Gate.getGatesAtLevel(i)) {
                g.evaluateState(evaluationMethod);
            }
        }
    }

    /**
     * Prints the input, output, state report of the system.
     */
    public void printSystemState(){

        this.log(" ===== STATE REPORT [SimTime = "+simulationTime+"] =====" );

        this.log("INPUT : "+ IntStream.range(0, inputGates.size())
                .mapToObj(i -> inputGates.get(i).getName() + "[" + inputVectors.get(simulationTime).get(i) + "] ")
                .reduce("", (a, b) -> a + b));

        this.log("STATE : "+this.stateGates
                .stream()
                .map(k -> k.getName() + "[" + k.getState() + "] ")
                .map(Object::toString)
                .reduce("", (a, b) -> a + b));

        this.log("OUTPUT : "+ this.outputGates
                .stream()
                .map(k -> k.getName() + "[" + k.getState() + "] ")
                .map(Object::toString)
                .reduce("", (a, b) -> a + b));
    }

    /**
     * Takes in a line of "compiled" verilog and turns it back into Gates. Queues gates and fans for reassembly.
     *
     * @param line The line (broken by newline) to be parsed.
     * @param lineNumber The line number of the line that's being parsed.
     * @throws VerilogFormatException
     * @throws InvalidGateException
     */
    private void parseLine(String line, int lineNumber) throws VerilogFormatException, InvalidGateException {
        if (line.length() == 0) {
            return;
        }

        String[] tokens = line.split(" ");

        GateType gateType = GateType.valueOf(tokens[0].trim().toUpperCase());
        int gateLevel = Integer.parseInt(tokens[2]);

        int fanInCount = Integer.parseInt(tokens[3]);
        int fanOutCount = Integer.parseInt(tokens[4 + fanInCount]);

        String gateName = tokens[5 + fanInCount + fanOutCount];

        Gate gateDescription;
        try {
            gateDescription = new Gate(gateName, gateType, gateLevel);
        } catch (InvalidNameException e) {
            throw new InvalidGateException("Can't have a duplicate gate name.");
        }

        if (gateType == GateType.INPUT) {
            inputNames.put(gateName, true);
            inputGates.add(gateDescription);
        } else if (gateType == GateType.OUTPUT) {
            outputNames.put(gateName, true);
            outputGates.add(gateDescription);
        } else if (gateType == GateType.DFF) {
            stateGates.add(gateDescription);
        }

        // If there are any fans on this gate, queue them for creation once the design gates are fully loaded.
        // Since this doesn't work with predefined wires like the verilog source, we must wait for full load.
        if (fanInCount + fanOutCount > 0) {
            ArrayList<String> fanInQueue = new ArrayList<>();
            ArrayList<String> fanOutQueue = new ArrayList<>();

            for (int i = 0; i < fanInCount; i++) {
                fanInQueue.add(tokens[4 + i]);
            }

            for (int i = 0; i < fanOutCount; i++) {
                fanOutQueue.add(tokens[5 + fanInCount + i]);
            }

            gateFanQueue.add(gateDescription);
            fanInQueues.add(fanInQueue);
            fanOutQueues.add(fanOutQueue);
        }

        this.log("Created new "+gateType+" " + gateDescription+".");
    }

    /**
     * Takes in the intermediate file format for simulation.
     *
     * @param fromPath the path of the "compiled" verilog.
     */
    public void loadCompiledVerilog(String fromPath) {
        try {
            String content = new String(Files.readAllBytes(Paths.get(fromPath)));

            // Could have done this with a readLine but the other was already written... Less efficient for loading design.
            String[] lines = content.split("\n");
            int tokenLineNumber = 0;
            for (String line : lines) {
                parseLine(line.trim(), tokenLineNumber++);
            }

            // Create all fan in and fan outs through gate name lookups.
            while (this.gateFanQueue.size() > 0) {
                Gate gateDescription = this.gateFanQueue.poll();

                ArrayList<String> fanInQueue = fanInQueues.poll();
                for (String gateName : fanInQueue) {
                    System.out.println(gateName + " : "+Gate.fromName(gateName));
                    gateDescription.addFan(GateFanType.FANIN, Gate.fromName(gateName));
                }

                ArrayList<String> fanOutQueue = fanOutQueues.poll();
                for (String gateName : fanOutQueue) {
                    System.out.println(gateName + " : "+Gate.fromName(gateName));
                    gateDescription.addFan(GateFanType.FANOUT, Gate.fromName(gateName));
                }
            }

            // Compute schedule precomputes for inputs.
            for (Gate g : this.inputGates){
                schedulePrecomputes.put(g.getName(), g.getPropagationPath());
            }

            // Compute schedule precomputes for DFFs.
            for (Gate g : this.stateGates){
                schedulePrecomputes.put(g.getName(), g.getPropagationPath());
            }

            this.gatesReady = true;

            // Verify the level tree to make sure the fan ins and fan outs were initialized correctly.
            this.verifyLevelTree();

        } catch (IOException | VerilogFormatException | InvalidGateException e) {
            e.printStackTrace();
        }
    }

    /**
     * Loads the simulation input vectors from file.
     *
     * @param fromPath input vector file path.
     */
    public void loadSimulationInputs(String fromPath) {
        try {
            String content = new String(Files.readAllBytes(Paths.get(fromPath)));

            // Could have done this with a readLine but the other was already written... Less efficient for loading design.
            String[] lines = content.split("\n");

            for (String line : lines) {
                inputVectors.add(
                        (ArrayList<Integer>) Arrays.stream(line.trim().split("")).map(a -> Integer.parseInt(a)).collect(Collectors.toList())
                );
            }

            inputsReady = true;
            System.out.println(Arrays.toString(inputVectors.toArray()));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Prints the "level tree" for every gate that has been initialized in the simulator.
     */
    public void verifyLevelTree() {
        for (Gate g : Gate.getValidGates()) {
            this.log(g + " : " + g.validateLevelPath(), SimulatorLogLevel.VERBOSE);
        }
    }

}
