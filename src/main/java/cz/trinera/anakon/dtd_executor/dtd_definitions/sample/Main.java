package cz.trinera.anakon.dtd_executor.dtd_definitions.sample;

import cz.trinera.anakon.dtd_executor.dtd_definitions.sample.test.TestProcess;

public class Main {

    public static void main(String[] args) {
        System.out.println("Hello from anakon-dtd-sample-processes!");
        try {
            //test if dependencies are OK (on com.fasterxml.jackson)
            TestProcess testProcess = new TestProcess();
            testProcess.run(null, null, null, null, null, null, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
