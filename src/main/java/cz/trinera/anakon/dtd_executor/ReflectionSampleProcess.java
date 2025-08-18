package cz.trinera.anakon.dtd_executor;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

//TODO: remove this class in production
public class ReflectionSampleProcess {

    public void run(UUID id, String type, String inputData, Path outputPath, AtomicBoolean cancelRequested) throws Exception {
        System.out.println("    Running ReflectionSampleProcess...");
        System.out.println("    ID: " + id);
        System.out.println("    Process type: " + type);
        System.out.println("    Input data: " + inputData);
        System.out.println("    Output path: " + outputPath);
        System.out.println("    Cancel requested: " + cancelRequested.get());
    }
}
