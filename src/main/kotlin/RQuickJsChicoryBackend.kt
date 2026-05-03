package jsbench

import com.dylibso.chicory.runtime.ExportFunction
import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.runtime.Store
import com.dylibso.chicory.wasi.WasiOptions
import com.dylibso.chicory.wasi.WasiPreview1
import jsbench.aot.rquickjs.RQuickJsModule

/**
 * Backend 5: rquickjs (the same Rust binding around QuickJS C source used by
 * `rquickjs-ffm`) compiled to wasm32-wasip1 via wasi-sdk + LLVM and AOT'd into
 * JVM bytecode by `chicory-compiler-maven-plugin` at build time.
 *
 * Sits on the same layer-cake side of the table as `quickjs4j`: same QuickJS
 * C interpreter at the bottom, reached through a different upstream binding
 * and a different wasm toolchain. Two interpreters deep, JVM bytecode at the
 * top.
 *
 * The wasm artifact exports the same four `js_*` functions as the cdylib
 * version plus an `alloc`/`dealloc` pair (Vec-trick), needed because the
 * source string has to live in the wasm linear memory before
 * `js_eval_source` can read it.
 */
class RQuickJsChicoryBackend : JsBackend {
    override val name = "rquickjs-chicory"

    private val wasi: WasiPreview1
    private val instance: Instance
    private val handle: Int
    private val alloc: ExportFunction
    private val dealloc: ExportFunction
    private val jsCallSieve: ExportFunction
    private val jsDestroy: ExportFunction

    init {
        val module = RQuickJsModule.load()
        val opts = WasiOptions.builder().build()
        wasi = WasiPreview1.builder().withOptions(opts).build()
        val store = Store()
        store.addFunction(*wasi.toHostFunctions())
        instance = store.instantiate("rquickjs") { imports ->
            Instance.builder(module)
                .withImportValues(imports)
                .withMachineFactory(RQuickJsModule::create)
                .build()
        }

        alloc = instance.export("alloc")
        dealloc = instance.export("dealloc")
        val jsCreate = instance.export("js_create")
        val jsEvalSource = instance.export("js_eval_source")
        jsCallSieve = instance.export("js_call_sieve")
        jsDestroy = instance.export("js_destroy")

        handle = jsCreate.apply()[0].toInt()
        require(handle != 0) { "js_create returned NULL — see stderr" }

        val sourceBytes = Workload.sieveSource.toByteArray(Charsets.UTF_8)
        val srcPtr = alloc.apply(sourceBytes.size.toLong())[0].toInt()
        require(srcPtr != 0) { "alloc(${sourceBytes.size}) returned NULL" }
        try {
            instance.memory().write(srcPtr, sourceBytes)
            val rc = jsEvalSource.apply(
                handle.toLong(), srcPtr.toLong(), sourceBytes.size.toLong()
            )[0].toInt()
            require(rc == 0) { "js_eval_source returned $rc" }
        } finally {
            dealloc.apply(srcPtr.toLong(), sourceBytes.size.toLong())
        }
    }

    override fun sieve(n: Int): Int {
        val r = jsCallSieve.apply(handle.toLong(), n.toLong())[0].toInt()
        require(r != Int.MIN_VALUE) { "js_call_sieve($n) failed (see stderr)" }
        return r
    }

    override fun close() {
        try {
            jsDestroy.apply(handle.toLong())
        } finally {
            wasi.close()
        }
    }
}
