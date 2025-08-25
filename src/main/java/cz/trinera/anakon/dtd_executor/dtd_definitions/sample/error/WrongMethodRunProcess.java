package cz.trinera.anakon.dtd_executor.dtd_definitions.sample.error;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

public class WrongMethodRunProcess {

    public void run(String type, String inputData, File logFile, File outputDir, AtomicBoolean cancelRequested) throws Exception {
        // This method is missing the UUID id parameter
        System.out.println("    Running " + WrongMethodRunProcess.class.getName() + "...");
    }
}
