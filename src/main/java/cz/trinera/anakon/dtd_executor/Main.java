package cz.trinera.anakon.dtd_executor;

import org.apache.commons.cli.*;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {

    private static final String OPT_CONFIG_FILE = "config_file";

    public static void main(String[] args) throws Exception {

        Options options = new Options();

        // Define mandatory 'config' option
        Option optConfig = new Option("c", OPT_CONFIG_FILE, true, "Configuration file (config.properties)");
        optConfig.setRequired(true);
        options.addOption(optConfig);

        // Parse command line arguments
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("java -jar anakon-dtd-executor-VERSION.jar", options);
            System.exit(1);
            return;
        }

        String configFilePath = cmd.getOptionValue(OPT_CONFIG_FILE);
        System.out.println("Initializing configuration...");
        Config.init(new File(configFilePath));

        //TODO: remove this in production
        if (true) {
            System.out.println("Running ReflectionSampleProcess...");
            loadProcesses();
        }

        System.out.println("Processes loaded: " + ProcessFactory.listProcesses());

        run();
    }

    public static void run() throws Exception {
        //System.out.println("Running AnakOn DTD Executor...");
        //Config config = Config.instanceOf();
        //System.out.println(config);
        new ProcessExecutor().start();
    }

    private static void loadProcesses() throws Exception {
        // load dir with jars and configs
        File processDir = new File(Config.instanceOf().getProcessesDir()).getAbsoluteFile();
        if (!processDir.exists() || !processDir.isDirectory()) {
            throw new IllegalArgumentException("Directory does not exist: " + processDir);
        }

        // load jars
        File[] jarFiles = processDir.listFiles((_, name) -> name.endsWith(".jar"));
        assert jarFiles != null;
        URL[] jarUrls = new URL[jarFiles.length];
        for (int i = 0; i < jarFiles.length; i++) {
            jarUrls[i] = jarFiles[i].toURI().toURL();
        }

        // load configs (yamls with processes)
        File[] dynamicConfigFiles = processDir.listFiles((_, name) -> name.endsWith(".yaml"));
        List<DynamicConfig.Process> processConfigs = new ArrayList<>();
        for (File dynamicConfigFile : dynamicConfigFiles) {
            processConfigs.addAll(DynamicConfig.create(dynamicConfigFile).getProcesses());
        }

        // setup class loader
        try (URLClassLoader loader = new URLClassLoader(jarUrls, Process.class.getClassLoader())) {

            for (DynamicConfig.Process processConfig : processConfigs) {
                // load process class
                Class<?> cls = Class.forName(processConfig.getClassName(), true, loader);

                // find run method
                Method method = cls.getMethod(
                        "run",
                        UUID.class,
                        String.class,
                        String.class,
                        Path.class,
                        AtomicBoolean.class
                );
                if (!Modifier.isPublic(method.getModifiers())) {
                    System.out.println("Skipping class " + cls.getName() + ": method run(...) is not public");
                    continue;
                }

                // create instance of process
                Object instance = cls.getDeclaredConstructor().newInstance();

                // register process in factory
                // because Process is @FunctionalInterface we can treat the (id, type, ...) lambda as Process.run(id, type, ...)
                ProcessFactory.registerProcess(processConfig.getType(), ((id, type, inputData, outputPath, cancelRequested) -> {
                    try {
                        // call run method on instance with parameters
                        method.invoke(instance, id, type, inputData, outputPath, cancelRequested);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new RuntimeException("Failed to invoke run on " + cls.getName(), e);
                    }
                }));
            }

        }
    }
}