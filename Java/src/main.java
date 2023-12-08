import simulator.*;
import simulator.Compiler;
import simulator.GateEvaluationMethod;

public class main {

    public static void main(String[] args) {
        System.out.println("Starting gate simulator...");

//
        Compiler compilerOne = new Compiler();
        compilerOne.loadVerilogFile("/Users/lpierce/Documents/Case Western/ECSE318/homework_6_wsp11/external/S27.v.txt");
        compilerOne.downloadCompiledVerilog("/Users/lpierce/Documents/Case Western/ECSE318/homework_6_wsp11/S27.compiled.txt");

        Gate.resetStatistics();

        Simulator simulatorOne = new Simulator();
        simulatorOne.loadCompiledVerilog("/Users/lpierce/Documents/Case Western/ECSE318/homework_6_wsp11/S27.compiled.txt");
        simulatorOne.loadSimulationInputs("/Users/lpierce/Documents/Case Western/ECSE318/homework_6_wsp11/external/S27.test_vec.txt");

//        Simulator simulatorOne = new Simulator();
//        simulatorOne.loadCompiledVerilog("/Users/lpierce/Documents/Case Western/ECSE318/homework_6_wsp11/parity.compiled.txt");
//        simulatorOne.loadSimulationInputs("/Users/lpierce/Documents/Case Western/ECSE318/homework_6_wsp11/external/parity.test_vec.txt");
        //simulatorOne.start(GateEvaluationMethod.INPUT_SCAN);
        simulatorOne.start(GateScheduleMethod.BREADTH, GateEvaluationMethod.TABLE_LOOKUP);

//        Simulator simulatorOne = new Simulator();
//        simulatorOne.loadCompiledVerilog("/Users/lpierce/Documents/Case Western/ECSE318/homework_6_wsp11/S27.compiled.txt");
//        simulatorOne.loadSimulationInputs("/Users/lpierce/Documents/Case Western/ECSE318/homework_6_wsp11/external/S27.test_vec.txt");
//        //simulatorOne.start(GateEvaluationMethod.INPUT_SCAN);
//        simulatorOne.start(GateEvaluationMethod.TABLE_LOOKUP);

        //        Compiler compilerTwo = new Compiler();
        //        compilerTwo.loadVerilogFile("/Users/lpierce/Documents/Case Western/ECSE318/homework_6_wsp11/external/S35.v.txt");
        //        compilerTwo.downloadCompiledVerilog("/Users/lpierce/Documents/Case Western/ECSE318/homework_6_wsp11/S35.compiled.txt");




    }

}
