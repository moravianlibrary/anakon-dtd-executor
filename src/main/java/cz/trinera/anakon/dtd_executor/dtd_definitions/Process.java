package cz.trinera.anakon.dtd_executor.dtd_definitions;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Interface for a DTD process that can be executed by the executor.
 */
@FunctionalInterface
public interface Process {

    void run(UUID id, String type, String inputData, File logFile, AtomicBoolean cancelRequested) throws Exception;

}
