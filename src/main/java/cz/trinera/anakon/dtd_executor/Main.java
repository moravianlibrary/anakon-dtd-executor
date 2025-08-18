package cz.trinera.anakon.dtd_executor;

import org.apache.commons.cli.*;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {

    private static final String OPT_CONFIG_FILE = "config_file";

    public static void main(String[] args) throws Exception {

        //TODO: remove this in production
        if (true) {
            System.out.println("Running ReflectionSampleProcess...");
            runReflectionSampleProcess();
            return;
        }

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
        run();
    }

    public static void run() throws Exception {
        //System.out.println("Running AnakOn DTD Executor...");
        //Config config = Config.instanceOf();
        //System.out.println(config);
        new ProcessExecutor().start();
    }

    private static void runReflectionSampleProcess() throws Exception {
        // Cesta k jar souboru
        URL jarUrl = new URL("file:///Users/martinrehanek/TrineraProjects/KomplexniValidator/anakon-dtd-executor/build/libs/anakon-dtd-executor-1.6.1.jar");

        // Vytvoření classloaderu
        try (URLClassLoader loader = new URLClassLoader(new URL[]{jarUrl}, Process.class.getClassLoader())) {

            // Načtení konkrétní třídy
            Class<?> cls = Class.forName("cz.trinera.anakon.dtd_executor.ReflectionSampleProcess", true, loader);

            // Vytvoření instance
            Object instance = cls.getDeclaredConstructor().newInstance();

            System.out.println("Načtená třída: " + instance.getClass().getName());

            // Získání metody 'run' s odpovídajícími parametry
            Method method = cls.getMethod(
                    "run",
                    UUID.class,
                    String.class,
                    String.class,
                    Path.class,
                    AtomicBoolean.class
            );
            //nastavení parametrů
            UUID id = UUID.randomUUID();
            String type = "myType";
            String inputData = "nějaký vstup";
            Path outputPath = Path.of("output.txt");
            AtomicBoolean cancelRequested = new AtomicBoolean(false);

            // Volání metody
            method.invoke(instance, id, type, inputData, outputPath, cancelRequested);
            System.out.println("Metoda run byla spuštěna.");

        }
    }
}