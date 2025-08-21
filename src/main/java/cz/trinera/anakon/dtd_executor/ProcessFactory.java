package cz.trinera.anakon.dtd_executor;

import cz.trinera.anakon.dtd_executor.dtd_definitions.UndefinedProcess;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProcessFactory {

    public static Process load(String type) throws Exception {
        File processDir = getProcessDirectory(Config.instanceOf().getProcessesDir());

        URL[] jarUrls = loadJars(processDir);

        List<DynamicConfig.Process> processes = loadProcessesFromDynamicConfig();

        DynamicConfig.Process process = findProcess(type, processes);
        if (process == null) {
            return new UndefinedProcess(); //will still run and then fail gracefully
        }
        Class<?> cls = loadClass(jarUrls, process);

        System.out.println("Class loaded: " + process.getClassName());

        Method method = findRunMethod(cls);

        // create instance of process
        Object instance = cls.getDeclaredConstructor().newInstance();

        return makeProcess(instance, method);
    }

    private static File getProcessDirectory(String processesDir) {
        File processDir = new File(processesDir).getAbsoluteFile();
        if (!processDir.exists() || !processDir.isDirectory()) {
            throw new IllegalArgumentException("Directory does not exist: " + processDir);
        }
        return processDir;
    }

    private static URL[] loadJars(File processDir) throws MalformedURLException {
        File[] jarFiles = processDir.listFiles((_, name) -> name.endsWith(".jar"));
        assert jarFiles != null;
        URL[] jarUrls = new URL[jarFiles.length];
        for (int i = 0; i < jarFiles.length; i++) {
            jarUrls[i] = jarFiles[i].toURI().toURL();
        }
        return jarUrls;
    }

    private static List<DynamicConfig.Process> loadProcessesFromDynamicConfig() throws IOException {
        String dynamicConfigFile = Config.instanceOf().getDynamicConfigFile();
        DynamicConfig dynamicConfig = DynamicConfig.create(new File(dynamicConfigFile));
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
        try (URLClassLoader loader = new URLClassLoader(jarUrls, Process.class.getClassLoader())) {
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
                Path.class,
                AtomicBoolean.class
        );
        if (!Modifier.isPublic(method.getModifiers())) {
            throw new RuntimeException( cls.getName() + ": method run(...) is not public");
        }
        return method;
    }

    private static Process makeProcess(Object instance, Method method) {
        // because Process is @FunctionalInterface we can treat the (id, type, ...) lambda as Process.run(id, type, ...)
        return (id, type, inputData, outputPath, cancelRequested) -> {
            try {
                System.out.println("Process loaded: " + instance.getClass().getName());
                // call run method on instance with parameters
                method.invoke(instance, id, type, inputData, outputPath, cancelRequested);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException("Failed to invoke run on " + instance.getClass().getName(), e);
            }
        };
    }

}
