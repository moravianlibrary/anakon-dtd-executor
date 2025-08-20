package cz.trinera.anakon.dtd_executor;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Interface for a DTD process that can be executed by the executor.
 */
@FunctionalInterface
public interface Process {

    void run(UUID id, String type, String inputData, Path outputPath, AtomicBoolean cancelRequested) throws Exception;

    enum State {
        CREATED, //never set to this by executor, only by anakon_backend
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELED, // Process was cancelled by user
    }

}
