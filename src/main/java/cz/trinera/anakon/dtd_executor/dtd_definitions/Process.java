package cz.trinera.anakon.dtd_executor.dtd_definitions;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Interface for a DTD process that can be executed by the executor.
 */
@FunctionalInterface
public interface Process {

    void run(UUID id, String type, String inputData, Path outputPath, AtomicBoolean cancelRequested) throws Exception;

}
