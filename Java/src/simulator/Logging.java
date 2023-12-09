package simulator;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class Logging {

    public int maxLogLevel = 1;
    private static FileWriter logFileWriter = null;

    protected void log(Object x, SimulatorLogLevel logLevel) {
        if (logLevel.getValue() <= maxLogLevel) {
            String line = "["+logLevel.toString()+"][@"+ Instant.now().truncatedTo(ChronoUnit.MICROS)+"] "+x.toString();
            System.out.println(line);

            if (logFileWriter != null) {
                try {
                    logFileWriter.write(line+"\n");
                    logFileWriter.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    protected void lognpl(Object x, SimulatorLogLevel logLevel) {
        if (logLevel.getValue() <= maxLogLevel) {
            String line = x.toString();
            System.out.print(line);

            if (logFileWriter != null) {
                try {
                    logFileWriter.write(line);
                    logFileWriter.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    protected void log(Object x) {
        this.log(x, SimulatorLogLevel.INFO);
    }

    /**
     * Set a file to log the transcript to.
     *
     * @param toPath the file to write the log to.
     */
    public void setLogFile(String toPath) {
        if (this.logFileWriter != null) {
            try {
                this.logFileWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        File downloadFile = new File(toPath);

        try {
            this.logFileWriter = new FileWriter(downloadFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
