import jsbench.GraalJsBackend
import jsbench.GraalJsInterpreterBackend
import jsbench.JsBackend
import jsbench.QuickJs4JBackend
import jsbench.RQuickJsFFMBackend
import jsbench.Workload

fun main(args: Array<String>) {
    val variant = args.firstOrNull() ?: "graaljs"
    val backend: JsBackend = when (variant) {
        "rquickjs-ffm" -> RQuickJsFFMBackend()
        "graaljs" -> GraalJsBackend()
        "graaljs-interp" -> GraalJsInterpreterBackend()
        "quickjs4j" -> QuickJs4JBackend()
        else -> error(
            "unknown variant '$variant' (expected: rquickjs-ffm | " +
                "graaljs | graaljs-interp | quickjs4j)"
        )
    }
    backend.use {
        println("js-engine-benchmark: backend=${it.name}")
        val n = Workload.N
        val expected = Workload.EXPECTED
        val got = it.sieve(n)
        val verdict = if (got == expected) "OK" else "MISMATCH (expected $expected)"
        println("sieve($n) = $got  $verdict")
    }
}
