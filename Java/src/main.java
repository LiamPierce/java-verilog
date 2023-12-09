import simulator.*;
import simulator.Compiler;
import simulator.GateEvaluationMethod;

public class main {

    public static void main(String[] args) {
        System.out.println("Starting gate simulator...");

        String documentRoot = args.length > 0 ? args[0] : "/Users/lpierce/Documents/Case Western/ECSE318/homework_6_wsp11";
        System.out.println("Loading from path : " + documentRoot);
        String[] fileNames = {"S27", "S27", "S35"};
        GateEvaluationMethod[] evaluationBenchmarks = {GateEvaluationMethod.TABLE_LOOKUP, GateEvaluationMethod.INPUT_SCAN, GateEvaluationMethod.HYBRID};

        for (GateEvaluationMethod evaluationMethod : evaluationBenchmarks) {
            for (String file : fileNames) {
                Gate.resetStatistics();

                Compiler compiler = new Compiler();
                compiler.setLogFile(documentRoot + "/transcripts/" + file + ".compilation.transcript.txt");
                compiler.loadVerilogFile(documentRoot + "/external/" + file + ".v.txt");
                compiler.downloadCompiledVerilog(documentRoot + "/compilations/" + file + ".compiled.txt");

                Gate.resetStatistics();

                Simulator simulator = new Simulator();
                simulator.setLogFile(documentRoot + "/transcripts/" + file + "_"+evaluationMethod.toString().toLowerCase()+".simulation.transcript.txt");
                simulator.loadCompiledVerilog(documentRoot + "/compilations/" + file + ".compiled.txt");
                simulator.loadSimulationInputs(documentRoot + "/external/" + file + ".test_vec.txt");

                simulator.start(GateScheduleMethod.BREADTH, evaluationMethod);
            }
        }

    }

}
