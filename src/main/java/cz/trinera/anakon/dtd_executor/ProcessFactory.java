package cz.trinera.anakon.dtd_executor;

import cz.trinera.anakon.dtd_executor.dtd_definitions.Process;
import cz.trinera.anakon.dtd_executor.dtd_definitions.UndefinedProcess;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProcessFactory {

    public static cz.trinera.anakon.dtd_executor.dtd_definitions.Process load(String type) throws Exception {
        File processDefinitionDir = Config.Utils.getExistingReadableDir(Config.instanceOf().getProcessesDefinitionDir());
        List<DynamicConfig.Process> processDefinitions = loadProcessesFromDynamicConfig();
        DynamicConfig.Process processDefinition = findProcess(type, processDefinitions);
        if (processDefinition == null) {
            return new UndefinedProcess(); //will still run and then fail gracefully
        }

        //we don't want to load all the jars in the directory and randomly pick classes from them
        //URL[] jarUrls = lookForExistingJars(processDefinitionDir);
        String jarFileName = processDefinition.getJarName() == null ? type + ".jar" : processDefinition.getJarName();
        System.out.println("Loading process definition for type '" + type + "' from " + jarFileName);
        File jarFile = new File(processDefinitionDir, jarFileName);
        if (!jarFile.exists() || !jarFile.isFile() || !jarFile.canRead()) {
            throw new RuntimeException("Jar file for process type '" + type + "' not found or not readable: " + jarFile.getAbsolutePath());
        }
        //load only the required jar
        URL[] jarUrls = new URL[]{jarFile.toURI().toURL()};

        Class<?> cls = loadClass(jarUrls, processDefinition);

        System.out.println("Process definition loaded: " + processDefinition.getClassName());

        Method method = findRunMethod(cls);

        // create instance of process
        Object processImplementationInstance = cls.getDeclaredConstructor().newInstance();

        return runProcess(processImplementationInstance, method);
    }


    private static URL[] lookForExistingJars(File processDefinitionDir) throws MalformedURLException {
        File[] jarFiles = processDefinitionDir.listFiles((file, name) -> name.endsWith(".jar"));
        assert jarFiles != null;
        URL[] jarUrls = new URL[jarFiles.length];
        for (int i = 0; i < jarFiles.length; i++) {
            jarUrls[i] = jarFiles[i].toURI().toURL();
        }
        return jarUrls;
    }

    private static List<DynamicConfig.Process> loadProcessesFromDynamicConfig() throws IOException {
        File dynamicConfigFile = Config.Utils.getExistingReadableFile(Config.instanceOf().getDynamicConfigFile());
        DynamicConfig dynamicConfig = DynamicConfig.create(dynamicConfigFile);
        return dynamicConfig.getProcesses();
    }

    private static DynamicConfig.Process findProcess(String processType, List<DynamicConfig.Process> processes) {
        for (DynamicConfig.Process process : processes) {
            if (process.getType().equals(processType)) {
                return process;
            }
        }
        return null;
    }

    private static Class<?> loadClass(URL[] jarUrls, DynamicConfig.Process process) throws IOException {
        try (URLClassLoader loader = new URLClassLoader(jarUrls, cz.trinera.anakon.dtd_executor.dtd_definitions.Process.class.getClassLoader())) {
            return Class.forName(process.getClassName(), true, loader);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(".jar for class not found: " + process.getClassName(), e);
        }
    }

    private static Method findRunMethod(Class<?> cls) throws NoSuchMethodException {
        Method method = cls.getMethod(
                "run",
                UUID.class,
                String.class,
                String.class,
                File.class,
                File.class,
                File.class,
                AtomicBoolean.class
        );
        if (!Modifier.isPublic(method.getModifiers())) {
            throw new RuntimeException(cls.getName() + ": method run(...) is not public");
        }
        return method;
    }

    private static Process runProcess(Object instance, Method method) {
        // because Process is @FunctionalInterface we can treat the (id, type, ...) lambda as Process.run(id, type, ...)
        return (id, type, inputData, logFile, outputDir, configFile, cancelRequested) -> {
            try {
                // call run method on instance with parameters
                method.invoke(instance, id, type, inputData, logFile, outputDir, configFile, cancelRequested);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to invoke run on " + instance.getClass().getName(), e);
            } catch (InvocationTargetException e) {
                throw (Exception) e.getCause();
            }
        };
    }

}
