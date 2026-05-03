# CLAUDE.md

This file provides guidance to Claude Code when working with this repository.

## What this project is

`js-engine-benchmark` benchmarks four ways of running **JavaScript** inside a JVM, all driven from one Kotlin/JMH harness using the same workload (Sieve of Eratosthenes, `sieve(1_000_000)` = 78498):

1. **`rquickjs-ffm`** — `rquickjs` Rust binding around QuickJS C source, compiled to a native cdylib using `rustc_codegen_cranelift` (cg_clif), called from the JVM via Java 22+ Foreign Function & Memory (FFM) API.
2. **`graaljs`** — GraalVM JavaScript via the Polyglot API, with Truffle JIT enabled (libgraal). Requires Oracle GraalVM 25 or another JVMCI-equipped JDK; on stock OpenJDK silently falls back to interpreter (see `wasm-engine-benchmark/CLAUDE.md` for the same calibration trap on GraalWasm).
3. **`graaljs-interp`** — GraalJS with `engine.Compilation=false`. Truffle interpreter only, no PE/JIT.
4. **`quickjs4j`** — `io.roastedroot:quickjs4j` (QuickJS-compiled-to-wasm running under Chicory's build-time AOT). The wasm artifact + Chicory runtime are pulled transitively.

This is a sibling project to `../wam-engine-benchmark` (the wasm-engine benchmark). They share methodology and JMH config; here the workload is JS rather than wasm.

## Build & run

- Build: `mvn compile` (drives `cargo +nightly rustc -- -Zcodegen-backend=cranelift` for backend 1)
- Smoke test single backend: `mvn exec:java -Dexec.args=graaljs` (or `rquickjs-ffm`, `graaljs-interp`, `quickjs4j`)
- Run JMH:
  ```
  mvn compile dependency:build-classpath -Dmdep.outputFile=target/cp.txt
  java --enable-native-access=ALL-UNNAMED \
    -cp "$(cat target/cp.txt):target/classes" jsbench.BenchmarkMain
  ```
- **JDK requirement**: Oracle GraalVM 25 (`25+37-LTS-jvmci-b01` or newer) for GraalJS to actually JIT. Stock OpenJDK gives the `engine.Compilation=false` numbers regardless of which backend you call.
- **Rust toolchain**: nightly + `rustup component add rustc-codegen-cranelift-preview` (for cg_clif).

## Sources

- rquickjs: https://github.com/DelSkayn/rquickjs
- rustc_codegen_cranelift: https://github.com/rust-lang/rustc_codegen_cranelift
- GraalJS: https://www.graalvm.org/latest/reference-manual/js/
- QuickJs4J: https://github.com/roastedroot/quickjs4j (NB: under `roastedroot` org, not Dylibso)
