package cz.trinera.anakon.dtd_executor;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import cz.trinera.anakon.dtd_executor.dtd_definitions.TestProcess;
import cz.trinera.anakon.dtd_executor.dtd_definitions.UndefinedProcess;

public class ProcessFactory {
    private static final Map<String, Supplier<DtdProcess>> registry = new HashMap<>();

    static {
        registry.put("Test", TestProcess::new);
        // registry.put("Export", ExportProcess::new); // další typy
    }

    public static DtdProcess create(String type) {
        Supplier<DtdProcess> supplier = registry.get(type);
        if (supplier == null) {
            return new UndefinedProcess(); //will still run and then fail gracefully
        }
        return supplier.get();
    }
}
