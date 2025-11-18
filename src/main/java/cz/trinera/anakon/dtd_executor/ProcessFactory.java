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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProcessFactory {

    public static cz.trinera.anakon.dtd_executor.dtd_definitions.Process load(String type) throws Exception {
        File processDefinitionDir = Config.Utils.getExistingReadableDir(Config.instanceOf().getProcessesDefinitionDir());
        List<DynamicConfig.Process> processDefinitions = loadProcessesFromDynamicConfig();
        DynamicConfig.Process processDefinition = findProcess(type, processDefinitions);
        if (processDefinition == null) {
            throw new RuntimeException("Definition for process '" + type + "' not found");
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
        ClassAndLoader cl = loadClass(jarUrls, processDefinition);
        Class<?> cls = cl.cls;
        URLClassLoader loader = cl.loader;
        System.out.println("Process definition loaded: " + processDefinition.getClassName());

        // create instance of process
        Object processImplementationInstance = cls.getDeclaredConstructor().newInstance();
        Method method = findRunMethod(cls);

        return runProcess(processImplementationInstance, method, loader);
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

    static final class ClassAndLoader {
        final Class<?> cls;
        final URLClassLoader loader;

        ClassAndLoader(Class<?> cls, URLClassLoader loader) {
            this.cls = cls;
            this.loader = loader;
        }
    }

    private static ClassAndLoader loadClass(URL[] jarUrls, DynamicConfig.Process process) {
        URLClassLoader loader = new URLClassLoader(jarUrls,
                cz.trinera.anakon.dtd_executor.dtd_definitions.Process.class.getClassLoader()
        );
        try {
            Class<?> cls = Class.forName(process.getClassName(), true, loader);
            return new ClassAndLoader(cls, loader);
        } catch (ClassNotFoundException e) {
            try {
                loader.close();
            } catch (IOException ignored) {
            }
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

    private static cz.trinera.anakon.dtd_executor.dtd_definitions.Process runProcess(Object instance, Method method, URLClassLoader loader) {
        return (id, type, inputData, logFile, outputDir, configFile, cancelRequested) -> {
            Thread t = Thread.currentThread();
            ClassLoader prev = t.getContextClassLoader();
            t.setContextClassLoader(loader);
            try {
                method.invoke(instance, id, type, inputData, logFile, outputDir, configFile, cancelRequested);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to invoke run on " + instance.getClass().getName(), e);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Exception) {
                    throw (Exception) cause;
                } else if (cause instanceof Error) {
                    throw (Error) cause;
                } else {
                    throw new RuntimeException(cause);
                }
            } finally {
                t.setContextClassLoader(prev);
                try {
                    loader.close();
                } catch (IOException ignored) {
                }
            }
        };
    }

}
