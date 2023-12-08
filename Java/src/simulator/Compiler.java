package simulator;

import javax.naming.InvalidNameException;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

public class Compiler {

    public int maxLogLevel = 3;

    ///
    ///  Parsing information.
    ///

    protected boolean discoveredModule = false;
    protected String moduleName;
    protected String[] portNames;
    protected Map<String, Boolean> portDefinitionLookup = new HashMap<>();

    protected Map<String, Boolean> inputNames = new HashMap<>();
    protected Map<String, Boolean> outputNames = new HashMap<>();

    // For DFF gates that need to be "defaned" from previous outputs to ensure correct state.
    protected ArrayList<Gate> defanGates = new ArrayList<>();


    private void log(Object x, SimulatorLogLevel logLevel) {
        if (logLevel.getValue() <= maxLogLevel) {
            System.out.println("["+logLevel.toString()+"][@"+Instant.now().truncatedTo(ChronoUnit.MICROS)+"] "+x.toString());
        }
    }

    private void log(Object x) {
        this.log(x, SimulatorLogLevel.INFO);
    }


    /**
     * This function is designed to take a token line (a piece of the file split by semicolon) and parse module information,
     * gate information, wire information, and inputs and outputs.
     *
     * @param line The line of verilog code to parse
     * @param lineNumber The current token line (not true line) of in the file.
     * @throws VerilogFormatException When there is an issue in the verilog file formatting.
     */
    private void parseTokenLine(String line, int lineNumber) throws VerilogFormatException, InvalidGateException {

        if (line.length() == 0) {
            return;
        }

        String[] tokens = line.replace("\n", "").split(" ");

        //System.out.println(Arrays.stream(tokens).reduce("", (a, b) -> a + b + "|"));

        if (tokens[0].equals("module")) {
            int defStart = line.indexOf("(");
            int defEnd = line.indexOf(")");

            if (defStart < 0 || defEnd < 0) {
                throw new VerilogFormatException("Illegal verilog module line.");
            }

            moduleName = tokens[1].substring(0, defStart - tokens[0].length() - 1);
            portNames = line.substring(defStart + 1, defEnd).replace(" ", "").split(",");
            for (String name : portNames) {
                portDefinitionLookup.put(name.trim(), true);
            }

            log("Parsed module '" + moduleName + "' with "+portNames.length+" ports.");
            log("Discovered port names: "+ Arrays.stream(portNames).reduce("", (a, b) -> a + b + " "));

            discoveredModule = true;

            return;
        }

        if (!discoveredModule) {
            throw new VerilogFormatException("Badly structured verilog file. Unable to find module.");
        }

        if (tokens[0].trim().equals("endmodule")) {
            log("Finished parsing module.");
        } else if (tokens[0].trim().equals("wire")) {
            GateType gateType = GateType.valueOf(tokens[0].trim().toUpperCase());

            // Wires could have spaces after, so take all after wire keyword.
            for (String wire : line.substring(5).split(",")) {
                String gateName = wire.trim();

                if (portDefinitionLookup.containsKey(gateName)) {
                    portDefinitionLookup.get(gateName);
                    log("Created new "+Gate.fromName(gateName).getType()+" wire: "+gateName, SimulatorLogLevel.VERBOSE);
                    continue;
                }

                Gate gateDescription;
                try {
                    gateDescription = new Gate(gateName, gateType, -1);
                } catch (InvalidNameException e) {
                    throw new VerilogFormatException(e.getMessage() + " : {"+gateName+"}");
                }

                log("Created new "+gateDescription.getType()+": "+gateName, SimulatorLogLevel.VERBOSE);
            }
        } else {

            String gateName = tokens[1].trim();
            GateType gateType = GateType.valueOf(tokens[0].trim().toUpperCase().replace("1", "")); // DFF1 is still a DFF.

            Gate gateDescription;
            try {
                gateDescription = new Gate(
                        gateName,
                        gateType,
                        (gateType.equals(GateType.DFF) || gateType.equals(GateType.INPUT)) ? 0 : -1
                );
            } catch (InvalidNameException e) {
                throw new VerilogFormatException(e.getMessage());
            }

            if (gateType.equals(GateType.INPUT) || gateType.equals(GateType.OUTPUT)) {
                if (!portDefinitionLookup.containsKey(gateName)) {
                    throw new VerilogFormatException("A gate defined as an "+gateType+" needs to be in the module port description.");
                }

                if (gateType.equals(GateType.INPUT)) {
                    inputNames.put(gateName, true);
                }else{
                    outputNames.put(gateName, true);
                }
            }else{
                // Gates that aren't input output or wire must have at least 3 space-separated tokens.
                if (tokens.length < 3) {
                    throw new VerilogFormatException("Invalid gate token line.", lineNumber);
                }

                // Since a gate's fan set can include spaces, take any token from index 2 to the end and combine.
                // This can be made more efficient but for ease it is written this way.
                String fan = Arrays.stream(Arrays.copyOfRange(tokens, 2, tokens.length))
                        .reduce("", (a, b) -> a + b + " ").trim();

                // If the fan isn't enclosed by parenthesis, something is wrong.
                if (fan.charAt(0) != '(' || fan.charAt(fan.length() - 1) != ')') {
                    System.out.println(line);
                    throw new VerilogFormatException("A gate's ports must be enclosed by parenthesis.", lineNumber);
                }

                // Break the connection list by commas and remove spaces.
                String[] connections = fan.substring(1, fan.length() - 1).replace(" ", "").split(",");
                // If a gate has no connections, it must be invalid.
                if (connections.length == 0) {
                    throw new VerilogFormatException("Invalid gate fan configuration.");
                }

                String outputGateName = connections[0];

                // If this gate's output wire is in the output lookup table, mark it on the gate level as such.
                if (outputNames.containsKey(outputGateName)) {
                    gateDescription.setAsOutput();
                }

                // For each input in the fan, add to the gate. It is easy to optimize away wires later.
                for (int i = 1; i < connections.length; i++) {
                    Gate input = Gate.fromName(connections[i]);
                    if (input != null) {
                        gateDescription.addFan(GateFanType.FANIN, input);
                    }else {
                        throw new VerilogFormatException("Input of "+gateName+" is connected to a non-existent wire "+connections[i]+".");
                    }

                    input.addFan(GateFanType.FANOUT, gateDescription);
                }

                // Add the output to the gate.
                Gate output = Gate.fromName(connections[0]);
                if (output != null) {
                    gateDescription.addFan(GateFanType.FANOUT, output);
                    output.addFan(GateFanType.FANIN, gateDescription);
                } else {
                    throw new VerilogFormatException("Output of "+gateName+" is connected to a non-existent wire "+connections[0]+".");
                }

                // Queue the removal of the fan in for this gate; The whole design must be loaded to optimize the wires away.
                if (gateDescription.getType() == GateType.DFF) {
                    defanGates.add(gateDescription);
                }
            }

            log("Created new " + gateDescription.getType() + " named "+gateName+". Inputs "+gateDescription.getFanString(GateFanType.FANIN, false) + " Outputs "+gateDescription.getFanString(GateFanType.FANOUT, false), SimulatorLogLevel.VERBOSE);
        }
    }

    public void loadVerilogFile(String fromPath) {
        try {
            String content = new String(Files.readAllBytes(Paths.get(fromPath)));

            String[] lines = content.split(";");
            int tokenLineNumber = 0;
            for (String line : lines) {
                parseTokenLine(line.trim(), tokenLineNumber++);
            }

            this.verifyLoad();
            this.updateGateTree();

            this.log("==== VERILOG LOAD STATS ====");
            this.log("Total Gates or Wires Created: " + Gate.getAutoIncrement());
            this.log("Total Wires Removed Through Optimization: " + Gate.getOptimizedGates());

            for (int i = 0; i <= Gate.getMaxGateLevel(); i++) {
                this.log("Gates at level "+i+":" + Gate.getGateCountAtLevel(i));
            }

            for (int i = 0; i <= Gate.getMaxGateLevel(); i++) {
                this.log("Gate list at level "+i+":" + Arrays.toString(Gate.getGatesAtLevel(i).toArray(new Gate[0])));
            }

            this.log("Inputs: ", SimulatorLogLevel.VERBOSE);
            for (String name : this.inputNames.keySet()) {
                this.log("\t"+name, SimulatorLogLevel.VERBOSE);
            }

            this.log("Outputs: ", SimulatorLogLevel.VERBOSE);
            for (String name : this.outputNames.keySet()) {
                this.log("\t"+name, SimulatorLogLevel.VERBOSE);
            }

            this.log("Input Fan", SimulatorLogLevel.VERBOSE);
            for (String name : this.inputNames.keySet()) {
                Gate input = Gate.fromName(name);
                this.log("\t"+input.getFanString(GateFanType.FANIN), SimulatorLogLevel.VERBOSE);
                this.log("\t"+input.getFanString(GateFanType.FANOUT), SimulatorLogLevel.VERBOSE);
            }

            this.verifyLevelTree();

        } catch (IOException | VerilogFormatException | InvalidGateException e) {
            e.printStackTrace();
        }
    }

    /**
     *  Removes wires from fan trees to improve performance. Updates levels.
     */
    public void updateGateTree() throws VerilogFormatException {

        Set<Integer> visited = new HashSet<>();
        Set<Integer> failures = new HashSet<>();

        Queue<ArrayList<Gate>> processQueue = new ArrayDeque<>();
        Queue<Gate> sourceDequeue = new ArrayDeque<>();

        // First up, process all of the inputs. This maps them to their gates.
        processQueue.add((ArrayList) inputNames.keySet().stream().map(a -> Gate.fromName(a)).collect(Collectors.toList()));

        while (processQueue.size() > 0) {
            ArrayList<Gate> process = processQueue.poll();
            this.log("Running process group with width : " + process.size() + " sourced from "+sourceDequeue.poll());
            System.out.println(Arrays.toString(process.toArray()));
            for (Gate g : process) {
                if (visited.contains(g.getId())) {
                    continue;
                }

                this.log(g.getFanString(GateFanType.FANIN), SimulatorLogLevel.VERBOSE);
                this.log(g.getFanString(GateFanType.FANOUT), SimulatorLogLevel.VERBOSE);

                try {
                    g.optimize(); // As part of the level updates, optimize away wires. Doing it in the level loop combines looping tasks. O(N^2) vs O(N).
                } catch (InvalidGateException e) {
                    this.log("Optimization error.", SimulatorLogLevel.WARNING);
                }

                this.log(g.getFanString(GateFanType.FANIN), SimulatorLogLevel.VERBOSE);
                this.log(g.getFanString(GateFanType.FANOUT), SimulatorLogLevel.VERBOSE);

                // If the level update was successful, we don't need to visit this gate again.
                if (g.updateLevel()) {
                    this.log("Level update for " + g.getName() + " was successful : " + g.getLevel(), SimulatorLogLevel.VERBOSE);
                    visited.add(g.getId());
                    failures.remove(g.getId());
                }else if (g.getType() != GateType.WIRE) {
                    failures.add(g.getId());
                }

                if (g.getRawFan(GateFanType.FANOUT).size() > 0) {
                    processQueue.add(g.getRawFan(GateFanType.FANOUT));
                    sourceDequeue.add(g);
                } else {
                    this.log("Fanout ended at "+g);
                }
            }
        }

        if (failures.size() > 0) {
            this.log("There are still uninitialized levels in your module.", SimulatorLogLevel.ERROR);
            System.out.println(failures.size());
            //throw new VerilogFormatException("Unable to initialize levels.");
        }

        this.log("Visited "+visited.size() + " gates during level adjustment and optimization.");
    }

    /**
     * Checks the simulator for load errors and load warnings.
     *
     * @throws VerilogFormatException If the load has a problem.
     */
    public void verifyLoad() throws VerilogFormatException {
        if (portNames.length != outputNames.size() + inputNames.size()) {
            throw new VerilogFormatException("Not all ports were set up as an input or an output.");
        }

        for (String name : this.inputNames.keySet()) {
            Gate input = Gate.fromName(name);
            if (input.getFanSize(GateFanType.FANOUT) == 0) {
                this.log("Input #"+input.getId()+"{"+input.getName()+"} is unconnected.", SimulatorLogLevel.WARNING);
            }
        }
    }

    public void verifyLevelTree() {
        for (Gate g : Gate.getValidGates()) {
            this.log(g + " : " + g.validateLevelPath(), SimulatorLogLevel.VERBOSE);
        }
    }

    private void dwl(String line, FileWriter writer) throws IOException{
        String modifiedString = line.replaceAll("\\s{2,}", " ");
        System.out.print(modifiedString);
        writer.write(modifiedString);
    }

    public void downloadCompiledVerilog(String toPath) {
        File downloadFile = new File(toPath);

        try {
            FileWriter writer = new FileWriter(downloadFile);
            for (Gate gate : Gate.getValidGates()) {
                StringBuilder k = new StringBuilder();
                k.append(gate.getType().toString() + " " + (gate.getType() == GateType.OUTPUT) + " " + gate.getLevel() + " ");
                k.append(gate.getFanSize(GateFanType.FANIN) + " ");
                k.append(gate.getFanString(GateFanType.FANIN, false) + " ");
                k.append(gate.getFanSize(GateFanType.FANOUT) + " ");
                k.append(gate.getFanString(GateFanType.FANOUT, false) + " ");
                k.append(gate.getName() + "\n");

                dwl(k.toString(), writer);
            }

            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
