package cz.trinera.anakon.dtd_executor;

import cz.trinera.anakon.dtd_executor.dtd_definitions.UndefinedProcess;

public class ProcessFactory {
    String type;
    Process process;

    public void registerProcess(String name, Process process) {
        this.type = type;
        this.process = process;
    }

    public String getType() {
        return type;
    }

    public Process getProcess() {
        return process;
    }

    public Process create(String type) {
        Process process = getProcess();
        if (process == null) {
            return new UndefinedProcess(); //will still run and then fail gracefully
        }
        return process;
    }
}
