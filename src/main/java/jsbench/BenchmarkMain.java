package jsbench;

import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;

public class BenchmarkMain {
    public static void main(String[] args) throws Exception {
        new Runner(new OptionsBuilder()
                .include(JsBench.class.getSimpleName())
                .build())
                .run();
    }
}
