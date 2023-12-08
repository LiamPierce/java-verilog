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

    public void resetGateStates() {
        simulationTime = 0;
        for(Gate g : Gate.getValidGates()) {
            // In class, all flip flops initialized to 0.
            if (g.getType() == GateType.DFF) {
                g.setState(0);
            } else { // X otherwise.
                g.setState(2);
            }
        }
    }

    public void start(GateEvaluationMethod evaluationMethod) {
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
            ArrayList<LinkedList<Gate>> propagations = new ArrayList<>();

            // Obviously, a frame must start by setting the input gates to the input vector.
            for (int i = 0; i < inputVector.size(); i++) {
                int inputVectorState = inputVector.get(i);
                this.inputGates.get(i).setState(inputVectorState);

                if (inputVectorState != previousInputVector.get(i)) {
                    // Add the full propagation path to the propagations array.
                    propagations.add(schedulePrecomputes.get(this.inputGates.get(i).getName()));
                }
            }

            schedulePropagation(propagations, evaluationMethod);

            // Once the propagation for the inputs has completed, we can check the propagation for the DFFs.
            for (Gate g : stateGates) {
                if (g.getWireDriver().getState() != g.getState()) {
                    // Add the full propagation path to the propagations array.
                    propagations.add(schedulePrecomputes.get(g.getName()));
                }
            }

            schedulePropagation(propagations, evaluationMethod);

            long frameDuration = System.nanoTime() - frameStartTime;
            this.log("["+evaluationMethod.toString()+"] Execution time: " + frameDuration + " ns"); // Tends to have about 125 ns of overhead.
            printSystemState();
            simulationTime++;
            adjustedDuration += frameDuration;
        }

        long totalDuration = System.nanoTime() - initTime;
        this.log("["+evaluationMethod.toString()+"] Total execution time: " + totalDuration + " ns. Execution time adjusted for logging code: " + adjustedDuration + " ns.");
    }

    /**
     * This method is the core of the simulator. It takes an unconstrained number of precomputed extended
     * fan outs (LinkedList<Gate> and properly iterates over all of them at once to update in the correct level order.
     *
     * @param propagations An array list of linked lists which represent a gate's extended fan out.
     */
    private void schedulePropagation(ArrayList<LinkedList<Gate>> propagations, GateEvaluationMethod evaluationMethod){
        int propagationLevel = 0;
        ArrayList<ListIterator<Gate>> propagationIterators = new ArrayList<>();

        // Set up new iterators for each propagation path. We'll follow each at the same time up the level depth.
        for (LinkedList<Gate> propagation : propagations) {
            propagationIterators.add(propagation.listIterator(0));
        }

        Queue<LinkedList<Gate>> removalQueue = new ArrayDeque<>();

        while (propagationLevel < Gate.getMaxGateLevel()) {
            // For each propagation path, execute per level.
            int iterationLocks = 0;
            for (int propagationIndex = 0; propagationIndex < propagations.size(); propagationIndex++) {
                ListIterator<Gate> iterator = propagationIterators.get(propagationIndex);

                while (iterator.hasNext()) {
                    Gate g = iterator.next();

                    if (g.getLevel() > propagationLevel) {
                        iterator.previous();
                        iterationLocks += 1;
                        break;
                    }

                    System.out.println("Iteration level " + propagationLevel +" "+ g);

                    g.evaluateState(evaluationMethod);
                }

                if (!iterator.hasNext()) {
                    removalQueue.add(propagations.get(propagationIndex));
                    continue;
                }
            }

            if (iterationLocks == propagations.size()) {
                propagationLevel++;
            }

            while (removalQueue.size() > 0) {
                propagations.remove(removalQueue.poll());
            }
        }
    }

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
