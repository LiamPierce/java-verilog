
# Java Verilog

This is a verilog parser and virtual machine written in Java. It simulates verilog execution quite efficiently. 

## Usage

The operation of the simulator is broken up into two separate modules. The *Compiler* class is made to read and parse a slightly modified verilog file into a secondary file format.

Below is an example of a fuller adder in the modified verilog file format.

```verilog
module full_adder(A, B, CIN, SUM, COUT);
    input A;
    input B;
    input CIN;
    output SUM;
    output COUT;

    wire S1, C1, C2, C3;

    xor G1 (S1, A, B);
    xor G2 (SUM, S1, CIN);
    
    and G3 (C1, A, B);
    and G4 (C2, A, CIN);
    and G5 (C3, B, CIN);

    or G6 (COUT, C1, C2, C3);
endmodule
```

The compiler verifies that the module is created correctly. Any unconnected outputs are reported and any references to non-existent gates are reported. Finally, the compiler outputs an intermediate "compiled" file in a custom format.

```java
Compiler compiler = new Compiler();
compiler.setLogFile(documentRoot + "/transcripts/" + file + ".compilation.transcript.txt");
compiler.loadVerilogFile(documentRoot + "/external/" + file + ".v.txt");
compiler.downloadCompiledVerilog(documentRoot + "/compilations/" + file + ".compiled.txt");
```

Now, the intermediate file (.compiled.txt) can be loaded into the *Simulator* class for execution.

```java
Gate.resetStatistics(); // Clears gate static variables collected during compilation.

Simulator simulator = new Simulator();
simulator.setLogFile(documentRoot + "/transcripts/" + file + "_"+evaluationMethod.toString().toLowerCase()+".simulation.transcript.txt");
simulator.loadCompiledVerilog(documentRoot + "/compilations/" + file + ".compiled.txt");
simulator.loadSimulationInputs(documentRoot + "/external/" + file + ".test_vec.txt");

simulator.start(GateScheduleMethod.BREADTH, GateEvaluationMethod.TABLE_LOOKUP);
```

## Scheduling Methods

### Naive

Given a list of changed gates, for each gate in the list, this naive scheduling method visits every single gate object in the extended fan out of the changed gate. This is a very inefficient scheduling method but it is bound to update the gate you want updated. For this reason it's very useful for debugging.

### Breadth

This breadth scheduling method takes the list of updated gates from a given frame of simulation. The propagation loop then initializes a gate visit queue with all of these updated gates. Until the gate visit queue is empty, the propagation loop polls a gate object from the queue. With this polled gate, the propagation loop runs the "evaluateState" method on the gate, which determines the gate's new state based on the gate's fan in. If the gate's state changes here, each gate in the current gate's immediate fan out is queued for visit if it hasn't been visited **from this current gate**. If the gate has been visited from the same source gate before it will not be queued again. This prevents looping.

Since this method continues to check gate changes all the way through the propagation, this method will update the minimum number of gates every time.

### Precompute

This was an attempt at checking if the computation of the propagation list was worth precomputing. It just does the naive method but precomputed.

## Evaluation Methods

There are two methods of evaluating the gate's next state from the fan in gates.

### Table Lookup

This method is supposedly the most efficient. It uses a lookup table for every gate that can be looked up.

This lookup table's first index is the gate type, the second index is the first input, and the third and final index is the second input. For gates with more than 2 inputs, this is repeated in a loop.

```java

static final int[][][] LOOKUP_TABLE = {
        {{0, 0, 0}, {0, 1, 2}, {0, 2, 2}}, // AND
        {{0, 1, 2}, {1, 1, 1}, {2, 1, 2}}, // OR
        {{1, 1, 1}, {1, 0, 2}, {1, 2, 2}}, // NAND
        {{1, 0, 2}, {0, 0, 0}, {2, 0, 2}}, // NOR
        {{0, 1, 2}, {1, 0, 2}, {2, 2, 2}}  // XOR
};

```

For DFFs, this evaluation method simply takes the first fan in's state and sets the current gate's state to it.

### Input Scan

The input scanning method loops through each input value and checks the gate type. For certain gate types, if there's an unknown, the gate state must be unknown. A lookup table is still used along with an inversion table.

```java

static final int[][] CI_TABLE = {
        {0,0},{1,0},{0,1},{1,1}
};
static final int[] NOT_TABLE = {1, 0, 2};

```

#### For AND, OR, NAND, NOR gates

If the gate's input matches the first index of the gate's CI table entry, the XOR between the first and second indexes is returned.

```java
this.setState(Gate.CI_TABLE[typeValue][0] ^ Gate.CI_TABLE[typeValue][1]);
```

Otherwise, the inversion table value of the same is returned.

```java
this.setState(Gate.NOT_TABLE[Gate.CI_TABLE[typeValue][0] ^ Gate.CI_TABLE[typeValue][1]]);
```

#### For DFF

For DFFs, this evaluation method simply takes the first fan in's state and sets the current gate's state to it.

#### For XOR

An XOR state variable is set to true when a 1 is read. If another 1 is read and the XOR state is true, the gate is 0. If, by the end, the XOR state variable is true, then there was only one 1 input and the state is set to 1. Otherwise, the state is set to 0.

### Hybrid

After writing this report once, I noticed that for circuits with a large number of unknowns, the input scan was always better. So I implemented a hybrid evaluation which chooses between table lookup and input scan based on the current state of the first previous gate.

As the next section will show, even with the added check, this hybrid evaluation is significantly faster than either the table lookup or input scan evaluation methods.

### Native

## Simple Benchmarking

All benchmarks were done using the "Breadth" scheduling method since it is the best scheduling method included in this project. 

This benchmark is meant only to give an idea of the timing and isn't averaged over a large number of runs.

To benchmark this code appropriately, I used *System.nanoTime()*. A benchmarked block of code is shown below.

```java

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

long gateFrameDuration = System.nanoTime() - frameStartTime;
frameStatistics.add("Gate update took " + gateFrameDuration + " ns  ("+(gateFrameDuration / 1E6) + " ms)");
frameStatistics.add(lastScheduleGateCount + " gates scheduled");

```

Blocks of code separated by logging are always benchmarked individually. This is done to make sure that logging code - code that can easily be disabled or optimized - is not included in the actual execution time for the gates. It is not meaningful to look at how long something takes to print to console & write to a file at the same time on a very dynamic system.

On two different ISCAS benchmark circuits, S27 and S35, the results for the *Input Scan* and *Table Lookup* evaluation methods are shown below.

Every group of benchmark was run at the same type to keep things as consistent as possible with system load etc.

### S27 

The results from the S27 circuit are interesting. They show that the Input Scan method is more efficient by a large margin. 

The total execution time for the S27 input scan is 90932 ns (0.090932 ms). The total execution time for the S27 table lookup is 219206 ns (0.219206 ms).
The hybrid evaluation approach took just 29577 ns (0.029577 ms).

For this small circuit, the execution time for the table lookup method is almost exactly twice that of the input scan method.

#### INPUT SCAN
```
[INFO][@2023-12-09T06:11:58.209250Z] Statistics printout: 
[INFO][@2023-12-09T06:11:58.209260Z] ====== Frame Statistics (SimTime = 0) ======
[INFO][@2023-12-09T06:11:58.209268Z] Gate update took 15909 ns  (0.015909 ms)
[INFO][@2023-12-09T06:11:58.209275Z] 10 gates scheduled
[INFO][@2023-12-09T06:11:58.209282Z] State update took 6378 ns (0.006378 ms)
[INFO][@2023-12-09T06:11:58.209289Z] 2 gates scheduled from state update
[INFO][@2023-12-09T06:11:58.209296Z] ====== Frame Statistics (SimTime = 1) ======
[INFO][@2023-12-09T06:11:58.209304Z] Gate update took 3085 ns  (0.003085 ms)
[INFO][@2023-12-09T06:11:58.209311Z] 2 gates scheduled
[INFO][@2023-12-09T06:11:58.209319Z] State update took 9073 ns (0.009073 ms)
[INFO][@2023-12-09T06:11:58.209325Z] 5 gates scheduled from state update
[INFO][@2023-12-09T06:11:58.209332Z] ====== Frame Statistics (SimTime = 2) ======
[INFO][@2023-12-09T06:11:58.209339Z] Gate update took 16463 ns  (0.016463 ms)
[INFO][@2023-12-09T06:11:58.209346Z] 7 gates scheduled
[INFO][@2023-12-09T06:11:58.209354Z] State update took 4606 ns (0.004606 ms)
[INFO][@2023-12-09T06:11:58.209360Z] 2 gates scheduled from state update
[INFO][@2023-12-09T06:11:58.209367Z] ====== Frame Statistics (SimTime = 3) ======
[INFO][@2023-12-09T06:11:58.209374Z] Gate update took 17946 ns  (0.017946 ms)
[INFO][@2023-12-09T06:11:58.209381Z] 14 gates scheduled
[INFO][@2023-12-09T06:11:58.209388Z] State update took 6127 ns (0.006127 ms)
[INFO][@2023-12-09T06:11:58.209395Z] 4 gates scheduled from state update
[INFO][@2023-12-09T06:11:58.209402Z] ====== Frame Statistics (SimTime = 4) ======
[INFO][@2023-12-09T06:11:58.209410Z] Gate update took 6902 ns  (0.006902 ms)
[INFO][@2023-12-09T06:11:58.209417Z] 7 gates scheduled
[INFO][@2023-12-09T06:11:58.209424Z] State update took 4443 ns (0.004443 ms)
[INFO][@2023-12-09T06:11:58.209431Z] 2 gates scheduled from state update
[INFO][@2023-12-09T06:11:58.209441Z] [INPUT_SCAN] Total execution time 90932 ns (0.090932 ms).
```

#### TABLE LOOKUP

```
[INFO][@2023-12-09T06:11:55.471829Z] Statistics printout: 
[INFO][@2023-12-09T06:11:55.471876Z] ====== Frame Statistics (SimTime = 0) ======
[INFO][@2023-12-09T06:11:55.471919Z] Gate update took 75927 ns  (0.075927 ms)
[INFO][@2023-12-09T06:11:55.471942Z] 10 gates scheduled
[INFO][@2023-12-09T06:11:55.471964Z] State update took 12999 ns (0.012999 ms)
[INFO][@2023-12-09T06:11:55.471988Z] 2 gates scheduled from state update
[INFO][@2023-12-09T06:11:55.472011Z] ====== Frame Statistics (SimTime = 1) ======
[INFO][@2023-12-09T06:11:55.472034Z] Gate update took 7146 ns  (0.007146 ms)
[INFO][@2023-12-09T06:11:55.472092Z] 2 gates scheduled
[INFO][@2023-12-09T06:11:55.472139Z] State update took 14518 ns (0.014518 ms)
[INFO][@2023-12-09T06:11:55.472179Z] 5 gates scheduled from state update
[INFO][@2023-12-09T06:11:55.472271Z] ====== Frame Statistics (SimTime = 2) ======
[INFO][@2023-12-09T06:11:55.472313Z] Gate update took 23128 ns  (0.023128 ms)
[INFO][@2023-12-09T06:11:55.472352Z] 7 gates scheduled
[INFO][@2023-12-09T06:11:55.472377Z] State update took 8670 ns (0.00867 ms)
[INFO][@2023-12-09T06:11:55.472419Z] 2 gates scheduled from state update
[INFO][@2023-12-09T06:11:55.472445Z] ====== Frame Statistics (SimTime = 3) ======
[INFO][@2023-12-09T06:11:55.472486Z] Gate update took 24000 ns  (0.024 ms)
[INFO][@2023-12-09T06:11:55.472514Z] 14 gates scheduled
[INFO][@2023-12-09T06:11:55.472582Z] State update took 31949 ns (0.031949 ms)
[INFO][@2023-12-09T06:11:55.472608Z] 4 gates scheduled from state update
[INFO][@2023-12-09T06:11:55.472637Z] ====== Frame Statistics (SimTime = 4) ======
[INFO][@2023-12-09T06:11:55.472663Z] Gate update took 12302 ns  (0.012302 ms)
[INFO][@2023-12-09T06:11:55.472687Z] 7 gates scheduled
[INFO][@2023-12-09T06:11:55.472713Z] State update took 8567 ns (0.008567 ms)
[INFO][@2023-12-09T06:11:55.472742Z] 2 gates scheduled from state update
[INFO][@2023-12-09T06:11:55.472821Z] [TABLE_LOOKUP] Total execution time 219206 ns (0.219206 ms).
```

#### HYBRID

```
[INFO][@2023-12-09T06:12:00.507194Z] Statistics printout: 
[INFO][@2023-12-09T06:12:00.507204Z] ====== Frame Statistics (SimTime = 0) ======
[INFO][@2023-12-09T06:12:00.507211Z] Gate update took 4116 ns  (0.004116 ms)
[INFO][@2023-12-09T06:12:00.507217Z] 10 gates scheduled
[INFO][@2023-12-09T06:12:00.507223Z] State update took 1838 ns (0.001838 ms)
[INFO][@2023-12-09T06:12:00.507230Z] 2 gates scheduled from state update
[INFO][@2023-12-09T06:12:00.507236Z] ====== Frame Statistics (SimTime = 1) ======
[INFO][@2023-12-09T06:12:00.507242Z] Gate update took 918 ns  (9.18E-4 ms)
[INFO][@2023-12-09T06:12:00.507249Z] 2 gates scheduled
[INFO][@2023-12-09T06:12:00.507255Z] State update took 2811 ns (0.002811 ms)
[INFO][@2023-12-09T06:12:00.507261Z] 5 gates scheduled from state update
[INFO][@2023-12-09T06:12:00.507267Z] ====== Frame Statistics (SimTime = 2) ======
[INFO][@2023-12-09T06:12:00.507273Z] Gate update took 1906 ns  (0.001906 ms)
[INFO][@2023-12-09T06:12:00.507279Z] 7 gates scheduled
[INFO][@2023-12-09T06:12:00.507285Z] State update took 1367 ns (0.001367 ms)
[INFO][@2023-12-09T06:12:00.507291Z] 2 gates scheduled from state update
[INFO][@2023-12-09T06:12:00.507297Z] ====== Frame Statistics (SimTime = 3) ======
[INFO][@2023-12-09T06:12:00.507303Z] Gate update took 4043 ns  (0.004043 ms)
[INFO][@2023-12-09T06:12:00.507309Z] 14 gates scheduled
[INFO][@2023-12-09T06:12:00.507316Z] State update took 9504 ns (0.009504 ms)
[INFO][@2023-12-09T06:12:00.507322Z] 4 gates scheduled from state update
[INFO][@2023-12-09T06:12:00.507328Z] ====== Frame Statistics (SimTime = 4) ======
[INFO][@2023-12-09T06:12:00.507334Z] Gate update took 1714 ns  (0.001714 ms)
[INFO][@2023-12-09T06:12:00.507340Z] 7 gates scheduled
[INFO][@2023-12-09T06:12:00.507346Z] State update took 1360 ns (0.00136 ms)
[INFO][@2023-12-09T06:12:00.507352Z] 2 gates scheduled from state update
[INFO][@2023-12-09T06:12:00.507359Z] [HYBRID] Total execution time 29577 ns (0.029577 ms).
```

### S35

Running S35, which has far more gates, makes the picture look very different. The total execution time for the input scanning method is 791426687 ns (791.426687 ms). The total execution time for the table lookup method is 554672542 ns (554.672542 ms). The hybrid method completed in 40923563 ns (440.923563 ms), which is a significant improvement over both.

The table lookup is significantly more efficient when dealing with a larger number of gates. I believe the table lookup performed worse for S27 because it is slower to solve unknowns in a circuit than the input scanning method. If S35 had more unknowns, the input scanning method would be faster.

#### INPUT SCAN

```
[...]
[INFO][@2023-12-09T06:12:00.501644Z] ====== Frame Statistics (SimTime = 253) ======
[INFO][@2023-12-09T06:12:00.501669Z] Gate update took 1432659 ns  (1.432659 ms)
[INFO][@2023-12-09T06:12:00.501677Z] 8159 gates scheduled
[INFO][@2023-12-09T06:12:00.501684Z] State update took 1524909 ns (1.524909 ms)
[INFO][@2023-12-09T06:12:00.501715Z] 8770 gates scheduled from state update
[INFO][@2023-12-09T06:12:00.501745Z] ====== Frame Statistics (SimTime = 254) ======
[INFO][@2023-12-09T06:12:00.501752Z] Gate update took 1270353 ns  (1.270353 ms)
[INFO][@2023-12-09T06:12:00.501759Z] 5623 gates scheduled
[INFO][@2023-12-09T06:12:00.501768Z] State update took 1614792 ns (1.614792 ms)
[INFO][@2023-12-09T06:12:00.501791Z] 9454 gates scheduled from state update
[INFO][@2023-12-09T06:12:00.501822Z] ====== Frame Statistics (SimTime = 255) ======
[INFO][@2023-12-09T06:12:00.501829Z] Gate update took 806041 ns  (0.806041 ms)
[INFO][@2023-12-09T06:12:00.501837Z] 5220 gates scheduled
[INFO][@2023-12-09T06:12:00.501843Z] State update took 1536271 ns (1.536271 ms)
[INFO][@2023-12-09T06:12:00.501869Z] 9639 gates scheduled from state update
[INFO][@2023-12-09T06:12:00.501899Z] ====== Frame Statistics (SimTime = 256) ======
[INFO][@2023-12-09T06:12:00.501906Z] Gate update took 797339 ns  (0.797339 ms)
[INFO][@2023-12-09T06:12:00.501914Z] 5476 gates scheduled
[INFO][@2023-12-09T06:12:00.501922Z] State update took 1760656 ns (1.760656 ms)
[INFO][@2023-12-09T06:12:00.501947Z] 10499 gates scheduled from state update
[INFO][@2023-12-09T06:12:00.501963Z] [INPUT_SCAN] Total execution time 791426687 ns (791.426687 ms).

```

#### TABLE LOOKUP

```
[...]
[INFO][@2023-12-09T06:11:58.202638Z] ====== Frame Statistics (SimTime = 253) ======
[INFO][@2023-12-09T06:11:58.202645Z] Gate update took 936952 ns  (0.936952 ms)
[INFO][@2023-12-09T06:11:58.202652Z] 5511 gates scheduled
[INFO][@2023-12-09T06:11:58.202659Z] State update took 354202 ns (0.354202 ms)
[INFO][@2023-12-09T06:11:58.202666Z] 1758 gates scheduled from state update
[INFO][@2023-12-09T06:11:58.202673Z] ====== Frame Statistics (SimTime = 254) ======
[INFO][@2023-12-09T06:11:58.202680Z] Gate update took 482033 ns  (0.482033 ms)
[INFO][@2023-12-09T06:11:58.202687Z] 2593 gates scheduled
[INFO][@2023-12-09T06:11:58.202694Z] State update took 295097 ns (0.295097 ms)
[INFO][@2023-12-09T06:11:58.202701Z] 1674 gates scheduled from state update
[INFO][@2023-12-09T06:11:58.202708Z] ====== Frame Statistics (SimTime = 255) ======
[INFO][@2023-12-09T06:11:58.202715Z] Gate update took 410881 ns  (0.410881 ms)
[INFO][@2023-12-09T06:11:58.202722Z] 2516 gates scheduled
[INFO][@2023-12-09T06:11:58.202729Z] State update took 289889 ns (0.289889 ms)
[INFO][@2023-12-09T06:11:58.202736Z] 1688 gates scheduled from state update
[INFO][@2023-12-09T06:11:58.202743Z] ====== Frame Statistics (SimTime = 256) ======
[INFO][@2023-12-09T06:11:58.202750Z] Gate update took 428734 ns  (0.428734 ms)
[INFO][@2023-12-09T06:11:58.202758Z] 2630 gates scheduled
[INFO][@2023-12-09T06:11:58.202765Z] State update took 368616 ns (0.368616 ms)
[INFO][@2023-12-09T06:11:58.202772Z] 1818 gates scheduled from state update
[INFO][@2023-12-09T06:11:58.202806Z] [TABLE_LOOKUP] Total execution time 554672542 ns (554.672542 ms).
```

#### HYBRID

```
[INFO][@2023-12-09T06:12:02.184436Z] ====== Frame Statistics (SimTime = 253) ======
[INFO][@2023-12-09T06:12:02.184441Z] Gate update took 822714 ns  (0.822714 ms)
[INFO][@2023-12-09T06:12:02.184446Z] 5511 gates scheduled
[INFO][@2023-12-09T06:12:02.184451Z] State update took 324299 ns (0.324299 ms)
[INFO][@2023-12-09T06:12:02.184455Z] 1758 gates scheduled from state update
[INFO][@2023-12-09T06:12:02.184460Z] ====== Frame Statistics (SimTime = 254) ======
[INFO][@2023-12-09T06:12:02.184465Z] Gate update took 438537 ns  (0.438537 ms)
[INFO][@2023-12-09T06:12:02.184470Z] 2593 gates scheduled
[INFO][@2023-12-09T06:12:02.184475Z] State update took 287960 ns (0.28796 ms)
[INFO][@2023-12-09T06:12:02.184480Z] 1674 gates scheduled from state update
[INFO][@2023-12-09T06:12:02.184484Z] ====== Frame Statistics (SimTime = 255) ======
[INFO][@2023-12-09T06:12:02.184489Z] Gate update took 410038 ns  (0.410038 ms)
[INFO][@2023-12-09T06:12:02.184494Z] 2516 gates scheduled
[INFO][@2023-12-09T06:12:02.184499Z] State update took 267352 ns (0.267352 ms)
[INFO][@2023-12-09T06:12:02.184504Z] 1688 gates scheduled from state update
[INFO][@2023-12-09T06:12:02.184509Z] ====== Frame Statistics (SimTime = 256) ======
[INFO][@2023-12-09T06:12:02.184513Z] Gate update took 407776 ns  (0.407776 ms)
[INFO][@2023-12-09T06:12:02.184518Z] 2630 gates scheduled
[INFO][@2023-12-09T06:12:02.184523Z] State update took 320235 ns (0.320235 ms)
[INFO][@2023-12-09T06:12:02.184527Z] 1818 gates scheduled from state update
[INFO][@2023-12-09T06:12:02.184534Z] [HYBRID] Total execution time 440923563 ns (440.923563 ms).
```

### Benchmarking Conclusion

The Input Scan evaluation method is faster for circuits with a lot of gates without known states.
The Table Lookup evaluation method is faster for circuits with a lot of gates with known states.
**The Hybrid evaluation is faster for both.**

## Simulator Output

### S27

```
[INFO][@2023-12-09T06:11:58.208921Z]  ===== STATE REPORT [SimTime = 0] =====
[INFO][@2023-12-09T06:11:58.208955Z] INPUT : G0[0] G1[0] G2[0] G3[0] 
[INFO][@2023-12-09T06:11:58.208969Z] STATE : XG1[2] XG2[2] XG3[2] 
[INFO][@2023-12-09T06:11:58.208980Z] OUTPUT : G17[2] 
[INFO][@2023-12-09T06:11:58.209003Z]  ===== STATE REPORT [SimTime = 1] =====
[INFO][@2023-12-09T06:11:58.209019Z] INPUT : G0[0] G1[0] G2[1] G3[0] 
[INFO][@2023-12-09T06:11:58.209029Z] STATE : XG1[0] XG2[2] XG3[2] 
[INFO][@2023-12-09T06:11:58.209039Z] OUTPUT : G17[2] 
[INFO][@2023-12-09T06:11:58.209076Z]  ===== STATE REPORT [SimTime = 2] =====
[INFO][@2023-12-09T06:11:58.209092Z] INPUT : G0[0] G1[1] G2[0] G3[0] 
[INFO][@2023-12-09T06:11:58.209103Z] STATE : XG1[0] XG2[2] XG3[0] 
[INFO][@2023-12-09T06:11:58.209111Z] OUTPUT : G17[2] 
[INFO][@2023-12-09T06:11:58.209145Z]  ===== STATE REPORT [SimTime = 3] =====
[INFO][@2023-12-09T06:11:58.209160Z] INPUT : G0[1] G1[0] G2[0] G3[0] 
[INFO][@2023-12-09T06:11:58.209170Z] STATE : XG1[0] XG2[2] XG3[1] 
[INFO][@2023-12-09T06:11:58.209179Z] OUTPUT : G17[1] 
[INFO][@2023-12-09T06:11:58.209202Z]  ===== STATE REPORT [SimTime = 4] =====
[INFO][@2023-12-09T06:11:58.209217Z] INPUT : G0[1] G1[1] G2[1] G3[1] 
[INFO][@2023-12-09T06:11:58.209227Z] STATE : XG1[1] XG2[0] XG3[1] 
[INFO][@2023-12-09T06:11:58.209236Z] OUTPUT : G17[1] 
```

### S35

The output for the S35 circuit is in the transcripts folder and is too large to include here.