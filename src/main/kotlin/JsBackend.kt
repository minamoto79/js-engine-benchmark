package jsbench

import java.nio.file.Files
import java.nio.file.Path

/**
 * Common contract for the four JS backends.
 *
 * Each backend evaluates the same `sieve.js` workload (Sieve of Eratosthenes)
 * and exposes one operation: count primes < n. Output is compared across
 * backends — sieve(1_000_000) must equal 78498 everywhere — that's the
 * cross-variant correctness check.
 */
interface JsBackend : AutoCloseable {
    /** Human-readable name for logging. */
    val name: String

    /** Count primes strictly less than n. */
    fun sieve(n: Int): Int
}

object Workload {
    private const val RESOURCE = "/js/sieve.js"

    /** sieve.js source — bundled in the jar at /js/sieve.js. */
    val sieveSource: String by lazy {
        val override = System.getProperty("jsbench.sieve.js")
        if (override != null) return@lazy Files.readString(Path.of(override))
        val stream = Workload::class.java.getResourceAsStream(RESOURCE)
            ?: error("$RESOURCE not found on classpath")
        stream.use { it.readAllBytes().toString(Charsets.UTF_8) }
    }

    /** Default benchmark size — sieve(1_000_000) returns 78498. */
    const val N = 1_000_000
    const val EXPECTED = 78498
}
