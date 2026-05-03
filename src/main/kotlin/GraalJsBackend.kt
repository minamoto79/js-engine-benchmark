package jsbench

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value

class GraalJsBackend : JsBackend {
    override val name = "graaljs"

    private val context: Context = Context.newBuilder("js")
        .allowHostAccess(org.graalvm.polyglot.HostAccess.ALL)
        .build()
    private val sieveFn: Value

    init {
        context.eval("js", Workload.sieveSource)
        sieveFn = context.getBindings("js").getMember("sieve")
            ?: error("sieve function not found in evaluated JS context")
    }

    override fun sieve(n: Int): Int = sieveFn.execute(n).asInt()

    override fun close() = context.close()
}
