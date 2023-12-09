package simulator;

import javax.naming.InvalidNameException;
import java.util.*;

public class Gate {

    static final int[][] CI_TABLE = {
            {0,0},{1,0},{0,1},{1,1}
    };

    static final int[] NOT_TABLE = {1, 0, 2};
    static final int[][][] LOOKUP_TABLE = {
            {{0, 0, 0}, {0, 1, 2}, {0, 2, 2}}, // AND
            {{0, 1, 2}, {1, 1, 1}, {2, 1, 2}}, // OR
            {{1, 1, 1}, {1, 0, 2}, {1, 2, 2}}, // NAND
            {{1, 0, 2}, {0, 0, 0}, {2, 0, 2}}, // NOR
            {{0, 1, 2}, {1, 0, 2}, {2, 2, 2}}  // XOR
    };

    private static int autoIncrement = 0;
    private static int optimizedGates = 0;
    private static int maxGateLevel = 0;

    private static long totalEvaluationTime = 0;
    private static long totalEvaluations = 0;

    private static Map<String, Gate> gateLookup = new HashMap<>();
    private static Map<Integer, List<Gate>> gateLevelLookup = new HashMap<>(); // Made as map for ease.

    public static ArrayList<Gate> validGates = new ArrayList<>();

    private int id;
    private String name;
    private GateType type;
    private int level;

    private int state;
    private boolean isOutput;

    private ArrayList<Gate> fanIn = new ArrayList<>();
    private ArrayList<Gate> fanOut = new ArrayList<>();

    private Gate next = null;

    public static int getAutoIncrement() {
        return Gate.autoIncrement;
    }

    public static int getOptimizedGates() {
        return Gate.optimizedGates;
    }

    public static int getMaxGateLevel() {
        return Gate.maxGateLevel;
    }

    public static int getGateCountAtLevel(int level) {
        return Gate.gateLevelLookup.get(level).size();
    }

    public static ArrayList<Gate> getGatesAtLevel(int level) {
        return (ArrayList<Gate>) Gate.gateLevelLookup.get(level);
    }

    public static ArrayList<Gate> getValidGates() {
        return Gate.validGates;
    }

    public static void resetStatistics() {
        Gate.autoIncrement = 0;
        Gate.optimizedGates = 0;
        Gate.maxGateLevel = 0;

        Gate.gateLookup = new HashMap<>();
        Gate.gateLevelLookup = new HashMap<>(); // Made as map for ease.

        Gate.validGates = new ArrayList<>();
    }

    public static long averageEvaluationTime(){
        return totalEvaluations == 0 ? 0 : totalEvaluationTime / totalEvaluations;
    }

    public static Gate fromName(String name) {
        return gateLookup.get(name);
    }

    public Gate(String name, GateType type, int level) throws InvalidNameException {
        this.id = Gate.autoIncrement++;

        this.name = name;
        this.type = type;
        this.isOutput = false;
        this.state = 2;

        this.setLevel(level);

        Gate.maxGateLevel = Math.max(this.level, Gate.maxGateLevel);

        if (Gate.gateLookup.containsKey(name)) {
            throw new InvalidNameException("Cannot have duplicate gate or wire names.");
        }

        Gate.gateLookup.put(name, this);
        Gate.validGates.add(this);
    }

    public String getName() {
        return this.name;
    }

    public GateType getType() {
        return this.type;
    }

    public int getLevel() {
        return this.level;
    }

    public void setLevel(int level) {
        this.level = level;

        if (level > Gate.maxGateLevel){
            Gate.maxGateLevel = level;
        }

        if (level != -1){
            if (!Gate.gateLevelLookup.containsKey(level)) {
                Gate.gateLevelLookup.put(level, new ArrayList<Gate>());
            }

            Gate.gateLevelLookup.get(level).add(this);
        }
    }

    public int getId() {
        return this.id;
    }

    public void setState(int state) {
        this.state = state;
    }

    public int getState() {
        return this.state;
    }

    private void tableLookupEvaluation() {
        int firstFanState = this.fanIn.get(0).getState();

        int typeValue = (this.type.getValue());
        if (typeValue >= 0) {
            int intermediateValue = firstFanState;

            for (int i = 1; i < this.fanIn.size(); i++) {
                int secondFanState = this.fanIn.get(i).getState();
                intermediateValue = Gate.LOOKUP_TABLE[typeValue][intermediateValue][secondFanState];
            }

            this.setState(intermediateValue);
        } else if (this.type == GateType.DFF) {
            this.setState(firstFanState);
        } else if (this.type == GateType.NOT) {
            this.setState(Gate.NOT_TABLE[firstFanState]);
        }

    }

    private void inputScanEvaluation() {

        boolean unknownValue = false;
        boolean xorState = false;

        int typeValue = (this.type == GateType.NOT ? GateType.NAND : this.type).getValue();

        for(int i = 0; i < this.fanIn.size(); i++) {
            int fanInValue = this.fanIn.get(i).getState();

            if (typeValue >= GateType.AND.getValue() && typeValue < GateType.XOR.getValue()){
                if (fanInValue == Gate.CI_TABLE[typeValue][0]) {
                    this.setState(Gate.CI_TABLE[typeValue][0] ^ Gate.CI_TABLE[typeValue][1]);
                    return;
                } else if (fanInValue == 2) { // If the value is unknown...
                    unknownValue = true;
                }

            } else if (typeValue == GateType.DFF.getValue()) {
                this.setState(fanInValue);
                return;
            } else if (typeValue == GateType.XOR.getValue()) {
                if (fanInValue == 2) {
                    this.setState(2);
                    return;
                } else if (fanInValue == 1) {
                    if (xorState) {
                        this.setState(0);
                        return;
                    } else {
                        xorState = true;
                    }
                }
            }
        }

        if (unknownValue) {
            this.setState(2);
            return;
        }

        if (typeValue == GateType.XOR.getValue()) {
            this.setState(xorState ? 1 : 0);
            return;
        }

        this.setState(Gate.NOT_TABLE[Gate.CI_TABLE[typeValue][0] ^ Gate.CI_TABLE[typeValue][1]]);

    }

    public void evaluateState(GateEvaluationMethod evaluationMethod) {

        if (this.getType() == GateType.INPUT) {
            return;
        }

        if (evaluationMethod == GateEvaluationMethod.TABLE_LOOKUP) {
            this.tableLookupEvaluation();
        } else if (evaluationMethod == GateEvaluationMethod.INPUT_SCAN) {
            this.inputScanEvaluation();
        }

        totalEvaluations += 1;
    }

    public boolean isOutput() {
        return this.isOutput;
    }

    public void setAsOutput() {
        this.isOutput = true;
    }

    /**
     * This method returns a traversable linked list of every gate in the extended fan out of this gate.
     *
     * @return A linkedlist of Gates that fan from this one. It will be ordered from level 0 to level maxlevel.
     */
    public LinkedList<Gate> getPropagationPath() {
        LinkedList<Gate> path = new LinkedList<>();
        Set<String> visited = new HashSet<>();

        Queue<Gate> gateProcessQueue = new ArrayDeque<>();
        gateProcessQueue.add(this);

        while (gateProcessQueue.size() > 0) {

            Gate currentGate = gateProcessQueue.poll();
            path.add(currentGate);

            for (Gate f : currentGate.getRawFan(GateFanType.FANOUT)) {

                // Check to see if this gate has been visited from this exact path. This prevents infinite loops.
                if (!visited.contains(currentGate.getId() + "=>" + f.getId())) {
                    gateProcessQueue.add(f);
                    visited.add(currentGate.getId() + "=>" + f.getId());
                }
            }
        }

        return path;
    }

    /**
     * Returns the driver gate for a wire or DFF.
     *
     * @return The single gate that drives this wire.
     */
    public Gate getWireDriver(){
        if (this.fanIn.size() > 0){
            return this.fanIn.get(0);
        }

        return null;
    }

    public int getFanSize(GateFanType fanType) {
        return ((fanType == GateFanType.FANIN ? this.fanIn : this.fanOut)).size();
    }

    public void replaceFan(GateFanType fanType, Gate replace, Gate with){
        if (fanType == GateFanType.FANOUT){
            System.out.print("Old Fan Out : " + getFanString(GateFanType.FANOUT, false));
            fanOut.remove(replace);
            if (with != null) {
                fanOut.add(with);
            }
            System.out.println(", New Fan Out : " + getFanString(GateFanType.FANOUT, false));

        }else{
            System.out.print("Old Fan In : " + getFanString(GateFanType.FANIN,false));

            fanIn.remove(replace);
            if (with != null) {
                fanIn.add(with);
            }

            System.out.println(", New Fan In : " + getFanString(GateFanType.FANIN, false));
        }
    }

    public void addFan(GateFanType fanType, Gate gateToInsert) throws InvalidGateException {
        if (gateToInsert == null) {
            System.out.println("Gate is null.");
        }

        switch (fanType) {
            case FANIN:
                fanIn.add(gateToInsert);

                if (this.type == GateType.WIRE && fanIn.size() > 1){
                    throw new InvalidGateException("A wire #"+this.id+"{"+this.name+"} is being driven by two or more sources.");
                }

                break;
            case FANOUT:
                fanOut.add(gateToInsert);
                break;
        }
    }

    public ArrayList<Gate> getRawFan(GateFanType fanType) {
        return fanType == GateFanType.FANIN ? fanIn : fanOut;
    }

    public void setRawFan(GateFanType fanType, ArrayList<Gate> fan) {
        if (fanType == GateFanType.FANIN) {
            this.fanIn = fan;
        } else {
            this.fanOut = fan;
        }
    }

    /**
     * Optimizes away wires by checking for wires in fanIn and replacing them with source.
     * Then it replaces the wire in the fanOut of the source with the current gate.
     */
    public void optimize() throws InvalidGateException {

        if (this.getType() == GateType.INPUT || this.getType() == GateType.WIRE) {
            return;
        }

        ArrayList<Gate> optimizedFanIn = new ArrayList<>();

        for (Gate g : fanIn) {
            // If the input to this gate is a wire and the wire is connected backwards, optimize it away.
            if (g.getType() == GateType.WIRE && g.getFanSize(GateFanType.FANIN) > 0) {

                // Find the gate that feeds this wire. It can only be one. This will be the new input.
                Gate source = g.getWireDriver();
                System.out.println("Wire "+g+" is connected from output " + source);
                optimizedFanIn.add(source);

                // The source still fans to the wire. Let's replace the wire with the current gate in the fan out.
                // This will make the old source point its output to the destination of the wire, which is the current gate.
                // If the current gate is a DFF, the fan out of the wire driver should not contain the DFF.
                source.replaceFan(GateFanType.FANOUT, g, (this.getType() == GateType.DFF ? null : this));

                // But other wires can connect to the wire the source was outputting to. The fan out and in will have to
                // reflect the larger list. Replace wire with source in fan in, add future gate f to source.
                for (Gate f : g.getRawFan(GateFanType.FANOUT)) {
                    if (f == this) {
                        continue;
                    }

                    f.replaceFan(GateFanType.FANIN, g, source);

                    // Once again, if the forward gate is a DFF the source gate should not have a fan to it.
                    if (f.getType() != GateType.DFF) {
                        source.addFan(GateFanType.FANOUT, f);
                    }
                }

                Gate.optimizedGates++;
                Gate.validGates.remove(g);

            } else {
                optimizedFanIn.add(g);
            }
        }

        this.fanIn = optimizedFanIn;
    }

    /**
     * Figures out if the current gate's level can be determined. Updates it if possible.
     */
    public boolean updateLevel() {
        if (this.getType() == GateType.INPUT || this.getType() == GateType.DFF){
            return true;
        }

        int minLevel = Integer.MAX_VALUE;
        boolean hasUnknowns = false;

        for (Gate g : fanIn) {
            int gateLevel = g.getLevel();

            // We can't update the level if it has unknowns unless the minimum level is 0,
            // in which case we know it's connected to a source.
            if (gateLevel == -1){
                //System.out.println("\t"+g.getName()+": "+g.getLevel());
                hasUnknowns = true;
            }

            if (gateLevel != -1 && gateLevel <= minLevel){
                minLevel = gateLevel;
            }
        }

        // Either there was nothing or there was a known gate but it wasn't 0 and others weren't yet known.
        if (minLevel == Integer.MAX_VALUE || hasUnknowns && minLevel != 0){
            return false;
        }

        this.setLevel(minLevel + 1);
        return true;
    }

    /**
     * Get the minimum level child from the fan in.
     *
     * @return the child at the mininum level if valid or null if invalid.
     */
    private Gate minimumLevelChild(){
        int minLevel = Integer.MAX_VALUE;
        Gate minLevelGate = null;
        boolean hasUnknowns = false;

        for (Gate g : fanIn) {
            int gateLevel = g.getLevel();

            // We can't update the level if it has unknowns unless the minimum level is 0,
            // in which case we know it's connected to a source.
            if (gateLevel == -1){
                //System.out.println("\t"+g.getName()+": "+g.getLevel());
                hasUnknowns = true;
            }

            if (gateLevel != -1 && gateLevel <= minLevel){
                minLevel = gateLevel;
                minLevelGate = g;
            }
        }

        // Either there was nothing or there was a known gate but it wasn't 0 and others weren't yet known.
        if (minLevel == Integer.MAX_VALUE || hasUnknowns && minLevel != 0){
            return null;
        }

        return minLevelGate;
    }

    /**
     * A function for testing, it walks backwards through the fan reports along the minimum level path.
     */
    public String validateLevelPath(){
        Gate minimumLevelChild = this.minimumLevelChild();
        if (this.level == 0) {
            return this.toString();
        }

        if (minimumLevelChild == null) {
            System.out.println("error.... why is "+this+" fan in null? B");
            return this.toString();
        }

        return this + ", " + minimumLevelChild.validateLevelPath();
    }

    public String getFanString(GateFanType fanType, boolean hasHeader) {
        String fan = ((hasHeader ? "Gate#"+this.id+"{"+this.name+"} "+fanType.toString()+": " : "") + Arrays
                .stream((fanType == GateFanType.FANIN ? this.fanIn : this.fanOut).toArray())
                .reduce("", (a, b) -> a + ((Gate) b).getName() + " "));
        return fan.length() > 0 ? fan.substring(0, fan.length() - 1) : "";
    }

    public String getFanString(GateFanType fanType) {
        return this.getFanString(fanType, true);
    }

    @Override
    public String toString() {
        return "{"+this.type+" "+this.name+"}["+this.id+"]["+this.level+"]";
    }

}
