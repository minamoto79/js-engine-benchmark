package jsbench

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Backend 1: rquickjs (Rust binding around QuickJS C code) compiled to a
 * native cdylib using `rustc_codegen_cranelift` (cg_clif), invoked from the
 * JVM via Java 22+'s Foreign Function & Memory API.
 *
 * The Rust↔C glue is what cg_clif emits; QuickJS's C interpreter loop is
 * still compiled by the system C toolchain (clang/gcc) regardless of cg_clif.
 */
class RQuickJsFFMBackend : JsBackend {
    override val name = "rquickjs-ffm"

    private val arena: Arena = Arena.ofShared()
    private val handle: MemorySegment
    private val jsDestroy: MethodHandle
    private val jsCallSieve: MethodHandle

    init {
        val libPath = resolveLib()
        require(libPath.toFile().exists()) {
            "native lib not found at $libPath — build with " +
                "`cd rquickjs-bench && cargo +nightly rustc --release -- -Zcodegen-backend=cranelift`"
        }
        val lookup = SymbolLookup.libraryLookup(libPath, arena)
        val linker = Linker.nativeLinker()

        val jsCreate = linker.downcallHandle(
            lookup.find("js_create").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS)
        )
        jsDestroy = linker.downcallHandle(
            lookup.find("js_destroy").orElseThrow(),
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
        )
        val jsEvalSource = linker.downcallHandle(
            lookup.find("js_eval_source").orElseThrow(),
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG
            )
        )
        jsCallSieve = linker.downcallHandle(
            lookup.find("js_call_sieve").orElseThrow(),
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.JAVA_INT
            )
        )

        handle = jsCreate.invoke() as MemorySegment
        require(!handle.equals(MemorySegment.NULL)) {
            "js_create returned NULL — see stderr for cause"
        }

        // Eval the sieve.js source once so the global `sieve` function exists.
        val sourceBytes = Workload.sieveSource.toByteArray(Charsets.UTF_8)
        Arena.ofConfined().use { tmp ->
            val seg = tmp.allocate(sourceBytes.size.toLong())
            MemorySegment.copy(sourceBytes, 0, seg, ValueLayout.JAVA_BYTE, 0L, sourceBytes.size)
            val rc = jsEvalSource.invoke(handle, seg, sourceBytes.size.toLong()) as Int
            require(rc == 0) { "js_eval_source returned $rc" }
        }
    }

    private fun resolveLib(): Path {
        val override = System.getProperty("rquickjs.native.lib")
        if (override != null) return Paths.get(override)
        val ext = if (System.getProperty("os.name").lowercase().contains("mac")) "dylib" else "so"
        return Paths.get("rquickjs-bench/target/release/librquickjs_bench.$ext")
    }

    override fun sieve(n: Int): Int {
        val r = jsCallSieve.invoke(handle, n) as Int
        require(r != Int.MIN_VALUE) { "js_call_sieve($n) failed (see stderr)" }
        return r
    }

    override fun close() {
        jsDestroy.invoke(handle)
        arena.close()
    }
}
