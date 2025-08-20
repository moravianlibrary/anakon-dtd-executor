package cz.trinera.anakon.dtd_executor;

import cz.trinera.anakon.dtd_executor.dtd_definitions.UndefinedProcess;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProcessFactory {
    private static final Map<String, Process> registry = new HashMap<>();

    public static void registerProcess(String name, Process process) {
        registry.put(name, process);
    }

    public static Set<String> listProcesses() {
        return registry.keySet();
    }

    public static Process create(String type) {
        Process process = registry.get(type);
        if (process == null) {
            return new UndefinedProcess(); //will still run and then fail gracefully
        }
        return process;
    }
}
