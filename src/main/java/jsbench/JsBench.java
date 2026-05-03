package jsbench;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 10, time = 5)
@Measurement(iterations = 10, time = 10)
@Fork(value = 1, jvmArgsAppend = {"--enable-native-access=ALL-UNNAMED"})
public class JsBench {

    private JsBackend rquickjsFfm;
    private JsBackend rquickjsChicory;
    private JsBackend graaljs;
    private JsBackend graaljsInterp;
    private JsBackend quickjs4j;
    private int n;

    @Setup(Level.Trial)
    public void setup() {
        rquickjsFfm = new RQuickJsFFMBackend();
        rquickjsChicory = new RQuickJsChicoryBackend();
        graaljs = new GraalJsBackend();
        graaljsInterp = new GraalJsInterpreterBackend();
        quickjs4j = new QuickJs4JBackend();
        n = Workload.N;
    }

    @TearDown(Level.Trial)
    public void tearDown() throws Exception {
        rquickjsFfm.close();
        rquickjsChicory.close();
        graaljs.close();
        graaljsInterp.close();
        quickjs4j.close();
    }

    @Benchmark public void rquickjsFfm(Blackhole bh) { bh.consume(rquickjsFfm.sieve(n)); }
    @Benchmark public void rquickjsChicory(Blackhole bh) { bh.consume(rquickjsChicory.sieve(n)); }
    @Benchmark public void graaljs(Blackhole bh) { bh.consume(graaljs.sieve(n)); }
    @Benchmark public void graaljsInterp(Blackhole bh) { bh.consume(graaljsInterp.sieve(n)); }
    @Benchmark public void quickjs4j(Blackhole bh) { bh.consume(quickjs4j.sieve(n)); }
}
