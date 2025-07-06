package cz.trinera.anakon.dtd_executor;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public interface DtdProcess {

    void run(UUID id, String type, String inputData, Path outputPath, AtomicBoolean cancelRequested) throws Exception;

}
