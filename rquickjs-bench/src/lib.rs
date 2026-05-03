//! RQuickJS-based JS engine, exposed via a tiny C ABI for FFM consumption.
//!
//! Exports:
//! - `js_create()` -> *mut JsHandle
//! - `js_destroy(handle)`
//! - `js_eval_source(handle, src_ptr, src_len) -> i32`  // 0 on success, -1 on error
//! - `js_call_sieve(handle, n) -> i32`                  // returns sieve(n)
//!
//! Wasm-only extras (built only for `wasm32-wasip1`):
//! - `alloc(size) -> *mut u8`
//! - `dealloc(ptr, size)`
//!
//! These two are needed by the Chicory-AOT'd backend, which has to write the
//! source string into the wasm module's linear memory before calling
//! `js_eval_source`. The cdylib (FFM) build doesn't need them — FFM allocates
//! native memory through `Arena` instead.

use std::sync::Mutex;

use rquickjs::{Context, Function, Runtime};

pub struct JsHandle {
    inner: Mutex<Inner>,
}

struct Inner {
    _rt: Runtime,
    ctx: Context,
}

fn build() -> rquickjs::Result<JsHandle> {
    let rt = Runtime::new()?;
    let ctx = Context::full(&rt)?;
    Ok(JsHandle { inner: Mutex::new(Inner { _rt: rt, ctx }) })
}

#[no_mangle]
pub extern "C" fn js_create() -> *mut JsHandle {
    match build() {
        Ok(h) => Box::into_raw(Box::new(h)),
        Err(e) => {
            eprintln!("js_create failed: {e}");
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub unsafe extern "C" fn js_destroy(handle: *mut JsHandle) {
    if !handle.is_null() {
        drop(Box::from_raw(handle));
    }
}

/// Evaluate JS source for side effects (e.g. defining a global function).
/// Returns 0 on success, -1 on error.
#[no_mangle]
pub unsafe extern "C" fn js_eval_source(
    handle: *mut JsHandle,
    src_ptr: *const u8,
    src_len: usize,
) -> i32 {
    let Some(h) = handle.as_ref() else { return -1 };
    let bytes = std::slice::from_raw_parts(src_ptr, src_len);
    let Ok(src) = std::str::from_utf8(bytes) else { return -1 };

    let guard = h.inner.lock().unwrap();
    let res = guard.ctx.with(|ctx| -> rquickjs::Result<()> {
        let _: rquickjs::Value = ctx.eval(src)?;
        Ok(())
    });
    match res {
        Ok(()) => 0,
        Err(e) => {
            eprintln!("js_eval_source: {e}");
            -1
        }
    }
}

/// Allocate `size` bytes of scratch space inside the wasm linear memory and
/// return a pointer to it. Used by the Chicory-AOT'd backend to write the JS
/// source string before passing its address to `js_eval_source`.
#[cfg(target_arch = "wasm32")]
#[no_mangle]
pub extern "C" fn alloc(size: usize) -> *mut u8 {
    let mut buf: Vec<u8> = Vec::with_capacity(size);
    let ptr = buf.as_mut_ptr();
    std::mem::forget(buf);
    ptr
}

/// Free a buffer previously returned by `alloc`. The size must match the
/// original allocation's `size` argument (Vec layout is reconstructed from it).
#[cfg(target_arch = "wasm32")]
#[no_mangle]
pub unsafe extern "C" fn dealloc(ptr: *mut u8, size: usize) {
    drop(Vec::from_raw_parts(ptr, 0, size));
}

/// Call the previously-defined `sieve(n)` global. Returns its i32 result, or
/// `i32::MIN` on error (so the caller can distinguish from a real -1 return).
#[no_mangle]
pub unsafe extern "C" fn js_call_sieve(handle: *mut JsHandle, n: i32) -> i32 {
    let Some(h) = handle.as_ref() else { return i32::MIN };
    let guard = h.inner.lock().unwrap();
    let res = guard.ctx.with(|ctx| -> rquickjs::Result<i32> {
        let globals = ctx.globals();
        let sieve: Function = globals.get("sieve")?;
        let v: i32 = sieve.call((n,))?;
        Ok(v)
    });
    match res {
        Ok(v) => v,
        Err(e) => {
            eprintln!("js_call_sieve: {e}");
            i32::MIN
        }
    }
}
