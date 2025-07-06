package cz.trinera.anakon.dtd_executor;

import org.apache.commons.cli.*;

import java.io.File;
import java.io.IOException;

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
        run();
    }

    public static void run() throws Exception {
        System.out.println("Running AnakOn DTD Executor...");
        Config config = Config.instanceOf();
        //System.out.println(config);
        new ProcessExecutor().start();
    }
}