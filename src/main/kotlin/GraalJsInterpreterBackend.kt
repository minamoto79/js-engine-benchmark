package jsbench

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Engine
import org.graalvm.polyglot.Value

/**
 * GraalJS with Truffle compilation explicitly disabled — the JS workload runs
 * through Truffle's AST/bytecode interpreter only, no PE/JIT. Mirror of
 * GraalWasmInterpreterBackend in wasm-engine-benchmark.
 */
class GraalJsInterpreterBackend : JsBackend {
    override val name = "graaljs-interp"

    private val engine: Engine = run {
        val builder = Engine.newBuilder("js").allowExperimentalOptions(true)
        // engine.Compilation is only registered when the optimized Truffle
        // runtime (libgraal/jargraal) is loaded. On stock OpenJDK the fallback
        // runtime kicks in and the option doesn't exist — but that runtime is
        // already interpreter-only, so the flag is moot there.
        try {
            builder.option("engine.Compilation", "false").build()
        } catch (_: IllegalArgumentException) {
            Engine.newBuilder("js").allowExperimentalOptions(true).build()
        }
    }
    private val context: Context = Context.newBuilder("js")
        .engine(engine)
        .build()
    private val sieveFn: Value

    init {
        context.eval("js", Workload.sieveSource)
        sieveFn = context.getBindings("js").getMember("sieve")
            ?: error("sieve function not found in evaluated JS context")
    }

    override fun sieve(n: Int): Int = sieveFn.execute(n).asInt()

    override fun close() {
        context.close()
        engine.close()
    }
}
