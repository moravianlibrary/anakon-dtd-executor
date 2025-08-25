package cz.trinera.anakon.dtd_executor.dtd_definitions;

import cz.trinera.anakon.dtd_executor.Process;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProcessDefinitionErrorProcess implements Process {


    @Override
    public void run(UUID id, String type, String inputData, Path outputPath, AtomicBoolean cancelRequested) throws Exception {
        //input data is stack trace of the error


    }
}
