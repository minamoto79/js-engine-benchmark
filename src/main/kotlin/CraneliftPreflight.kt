package jsbench

import com.dylibso.chicory.runtime.ExportFunction
import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.runtime.Store
import com.dylibso.chicory.wasi.WasiOptions
import com.dylibso.chicory.wasi.WasiPreview1
import jsbench.aot.cranelift.CraneliftModule

/**
 * Runs the Chicory-AOT-compiled `cranelift.wasm` (Cranelift codegen library
 * targeting wasm32-wasip1) and asks it to emit machine code for the host
 * triple. Used by RQuickJsFFMBackend.init as a preflight: verifies the full
 * Cranelift→wasm32→Chicory-AOT→JVM pipeline before the benchmark exercises
 * the cg_clif-built rquickjs cdylib over FFM.
 *
 * The wasm artifact is the same `cranelift.wasm` shipped by the sibling
 * `wasm-engine-benchmark` project — re-AOT'd here by this project's own
 * chicory-compiler-maven-plugin invocation so the FFM path in this benchmark
 * has the same "Cranelift-inside-the-JVM-via-wasm" decoration.
 *
 * **Note on scope.** `cranelift.wasm` exports `compile_add(triple, out)` which
 * builds a hardcoded `add(i32,i32) -> i32` CLIF function and compiles it for
 * the requested target. It does NOT translate arbitrary wasm input — that
 * would require either pinning to the deprecated `cranelift-wasm` 0.112 or
 * hand-rolling a wasm→CLIF translator with `wasmparser`.
 */
object CraneliftPreflight {

    /**
     * Detect the host's Cranelift target triple from JVM system properties.
     */
    fun hostTriple(): String {
        val arch = when (val a = System.getProperty("os.arch").lowercase()) {
            "aarch64", "arm64" -> "aarch64"
            "x86_64", "amd64" -> "x86_64"
            else -> error("unsupported os.arch=$a")
        }
        val osName = System.getProperty("os.name").lowercase()
        val osPart = when {
            osName.contains("mac") || osName.contains("darwin") -> "apple-darwin"
            osName.contains("windows") -> "pc-windows-msvc"
            osName.contains("linux") -> "unknown-linux-gnu"
            else -> error("unsupported os.name=$osName")
        }
        return "$arch-$osPart"
    }

    data class Result(val triple: String, val objectBytes: Int)

    fun runForHost(): Result = runFor(hostTriple())

    fun runFor(triple: String): Result {
        val module = CraneliftModule.load()
        val opts = WasiOptions.builder().build()
        val wasi = WasiPreview1.builder().withOptions(opts).build()
        val store = Store()
        store.addFunction(*wasi.toHostFunctions())
        val instance: Instance = store.instantiate("cranelift") { imports ->
            Instance.builder(module)
                .withImportValues(imports)
                .withMachineFactory(CraneliftModule::create)
                .build()
        }
        try {
            val alloc: ExportFunction = instance.export("alloc")
            val dealloc: ExportFunction = instance.export("dealloc")
            val compileAdd: ExportFunction = instance.export("compile_add")

            val tripleBytes = triple.toByteArray(Charsets.UTF_8)
            val outCap = 64 * 1024

            val triplePtr = alloc.apply(tripleBytes.size.toLong())[0].toInt()
            val outPtr = alloc.apply(outCap.toLong())[0].toInt()
            try {
                instance.memory().write(triplePtr, tripleBytes)
                val written = compileAdd.apply(
                    triplePtr.toLong(), tripleBytes.size.toLong(),
                    outPtr.toLong(), outCap.toLong()
                )[0].toInt()
                require(written > 0) {
                    "cranelift-on-wasm compile_add($triple) returned $written"
                }
                return Result(triple, written)
            } finally {
                dealloc.apply(triplePtr.toLong(), tripleBytes.size.toLong())
                dealloc.apply(outPtr.toLong(), outCap.toLong())
            }
        } finally {
            wasi.close()
        }
    }
}
