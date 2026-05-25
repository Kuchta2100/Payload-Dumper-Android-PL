mod engine;
mod helper;
mod payload;
mod reader;

use std::{
    collections::HashMap,
    fs::File,
    io::{BufReader, Read as _},
    panic,
    path::PathBuf,
    sync::{
        Arc, Mutex, OnceLock, RwLock,
        atomic::{AtomicI32, AtomicU8},
    },
};

use jni::{
    EnvUnowned, jni_mangle,
    objects::{JByteBuffer, JClass, JString},
    sys::{jboolean, jbyteArray, jint, jstring},
};
use sha2::{Digest as _, Sha256};
use tokio::sync::Semaphore;

use crate::{
    engine::part_manifest::PartManifest,
    helper::{
        constants::{BLOCK_SIZE, status},
        errors::{AppError, AppResult},
    },
    payload::{Payload, PayloadDumper},
};

struct Session {
    payload: Payload,
    dumper: Mutex<PayloadDumper>,
    download_session: Mutex<DownloadSession>,
    part_manifest: Option<PartManifest>,
}
struct DownloadSession {
    buffer_ptr: Option<*mut DownloadTask>,
    task_count: u8,
    runtime: tokio::runtime::Runtime,
    active_task: Mutex<HashMap<u8, tokio::task::JoinHandle<Result<(), AppError>>>>,
    errors: Mutex<HashMap<u8, String>>,
    concurrency: Arc<Semaphore>,
}
#[repr(C)]
struct DownloadTask {
    id: AtomicU8,
    abort: AtomicU8,
    status: AtomicU8,
    progress: AtomicI32,
}
unsafe impl Send for DownloadTask {}
unsafe impl Sync for DownloadTask {}
unsafe impl Send for DownloadSession {}
unsafe impl Sync for DownloadSession {}

static ENGINE: OnceLock<RwLock<Option<Session>>> = OnceLock::new();

#[jni_mangle("com.rajmani7584.payloaddumper.nativeHelper.PayloadDumper")]
pub fn init_session(mut e: EnvUnowned, _class: JClass) -> jint {
    let res = e.with_env(|_env| -> Result<_, jni::errors::Error> {
        panic::catch_unwind(|| -> AppResult<()> {
            let mut engine = get_engine()
                .write()
                .map_err(|e| AppError::Other(e.to_string()))?;

            *engine = None;
            Ok(())
        })
        .map_err(|_| AppError::Other("Failed to init new session".to_string()))??;

        // let st = JString::from_str(env, "hello from rust").unwrap();
        // let _ = env.call_method(
        //     class,
        //     jni_str!("logFromRust"),
        //     jni_sig!((str: JString)),
        //     &[JValue::Object(&st)],
        // );

        Ok(0)
    });

    res.resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[jni_mangle("com.rajmani7584.payloaddumper.nativeHelper.PayloadDumper")]
pub fn open_payload(
    mut env_u: EnvUnowned,
    _class: JClass,
    p_type: jint,
    path: JString,
    concurrency: jint,
    buf_size: jint,
) -> jbyteArray {
    let o = env_u.with_env(|env| -> Result<_, jni::errors::Error> {
        let b = panic::catch_unwind(move || -> AppResult<Vec<u8>> {
            let payload = Payload::from_type(p_type as u8, &path.to_string())?;

            let mut dumper = PayloadDumper::with_buf_size(payload.clone(), buf_size as usize)?;
            let b = dumper.get_part_manifest_bytes()?;
            let part_manifest = dumper.get_part_manifest()?;

            let runtime = tokio::runtime::Builder::new_multi_thread()
                .enable_all()
                .build()?;
            let session = Session {
                payload: payload.clone(),
                dumper: Mutex::new(dumper),
                download_session: Mutex::new(DownloadSession {
                    buffer_ptr: None,
                    runtime,
                    active_task: Mutex::new(HashMap::new()),
                    concurrency: Arc::new(Semaphore::new(concurrency as usize)),
                    task_count: 0,
                    errors: Mutex::new(HashMap::new()),
                }),
                part_manifest: Some(part_manifest),
            };

            let mut engine = get_engine()
                .write()
                .map_err(|e| AppError::Other(e.to_string()))?;

            *engine = Some(session);

            Ok(b)
        })
        .map_err(|e| AppError::Other(format!("Rust panicked: {:?}", e)))??;
        env.byte_array_from_slice(&b)
    });

    o.resolve::<jni::errors::ThrowRuntimeExAndDefault>()
        .into_raw()
}

#[jni_mangle("com.rajmani7584.payloaddumper.nativeHelper.PayloadDumper")]
pub fn fetch_header(mut env_u: EnvUnowned, _class: JClass) -> jstring {
    let o = env_u.with_env(|env| -> Result<_, _> {
        let s = panic::catch_unwind(|| -> AppResult<String> {
            let engine = get_engine()
                .read()
                .map_err(|e| AppError::Other(e.to_string()))?;

            let session = engine
                .as_ref()
                .ok_or(AppError::Other("Engine not intialized yet".to_string()))?;

            let header = session
                .dumper
                .lock()
                .map_err(|e| AppError::Other(e.to_string()))?
                .get_header()?;

            let s = format!("{:?}", header);
            Ok(s)
        })
        .map_err(|e| AppError::Other(format!("Rust panicked: {e:?}")))??;

        env.new_string(&s)
    });

    o.resolve::<jni::errors::ThrowRuntimeExAndDefault>()
        .into_raw()
}

#[jni_mangle("com.rajmani7584.payloaddumper.nativeHelper.PayloadDumper")]
pub fn fetch_part_manifest(mut env_u: EnvUnowned, _class: JClass) -> jbyteArray {
    let o = env_u.with_env(|env| -> Result<_, _> {
        let b = panic::catch_unwind(|| -> AppResult<Vec<u8>> {
            let engine = get_engine()
                .read()
                .map_err(|e| AppError::Other(e.to_string()))?;

            let session = engine
                .as_ref()
                .ok_or(AppError::Other("Engine not intialized yet".to_string()))?;

            let manifest = session
                .dumper
                .lock()
                .map_err(|e| AppError::Other(e.to_string()))?
                .get_part_manifest_bytes()?;

            Ok(manifest)
        })
        .map_err(|e| AppError::Other(format!("Rust panicked: {e:?}")))??;

        env.byte_array_from_slice(&b)
    });

    o.resolve::<jni::errors::ThrowRuntimeExAndDefault>()
        .into_raw()
}

#[jni_mangle("com.rajmani7584.payloaddumper.nativeHelper.PayloadDumper")]
pub fn fetch_signatures(mut env_u: EnvUnowned, _class: JClass) -> jbyteArray {
    let o = env_u.with_env(|env| -> Result<_, _> {
        let b = panic::catch_unwind(|| -> AppResult<Vec<u8>> {
            let engine = get_engine()
                .read()
                .map_err(|e| AppError::Other(e.to_string()))?;

            let session = engine
                .as_ref()
                .ok_or(AppError::Other("Engine not intialized yet".to_string()))?;

            let signatures = session
                .dumper
                .lock()
                .map_err(|e| AppError::Other(e.to_string()))?
                .get_signatures_bytes()?;

            Ok(signatures)
        })
        .map_err(|e| AppError::Other(format!("Rust panicked: {e:?}")))??;

        env.byte_array_from_slice(&b)
    });

    o.resolve::<jni::errors::ThrowRuntimeExAndDefault>()
        .into_raw()
}

#[jni_mangle("com.rajmani7584.payloaddumper.nativeHelper.PayloadDumper")]
pub fn bind_buffer(
    mut env_u: EnvUnowned,
    _class: JClass,
    task_count: jint,
    address: JByteBuffer,
) -> jint {
    let o = env_u.with_env(|env| -> Result<_, jni::errors::Error> {
        let b = panic::catch_unwind(|| -> AppResult<i32> {
            let engine = get_engine()
                .read()
                .map_err(|e| AppError::Other(e.to_string()))?;

            let session = engine
                .as_ref()
                .ok_or(AppError::Other("Engine not intialized yet".to_string()))?;

            let mut download_session = session
                .download_session
                .lock()
                .map_err(|e| AppError::Other(e.to_string()))?;

            let buffer = env.get_direct_buffer_address(&address)?;
            let buffer = buffer as *mut DownloadTask;
            download_session.buffer_ptr = Some(buffer);
            download_session.task_count = task_count as u8;

            Ok(0)
        })
        .map_err(|e| AppError::Other(format!("Rust panicked: {e:?}")))??;

        Ok(b)
    });

    o.resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[jni_mangle("com.rajmani7584.payloaddumper.nativeHelper.PayloadDumper")]
pub fn dump(
    mut env_u: EnvUnowned,
    _class: JClass,
    partition_id: jint,
    out: JString,
    verify: jboolean,
) -> jint {
    let o = env_u.with_env(|_env| -> Result<_, jni::errors::Error> {
        let b = panic::catch_unwind(|| -> AppResult<i32> {
            let engine_lock = ENGINE
                .get()
                .ok_or(AppError::Other("Engine not initialized".to_string()))?;

            let session_guard = engine_lock.read().unwrap();
            let session = session_guard
                .as_ref()
                .ok_or(AppError::Other("No active session".to_string()))?;

            let payload = session.payload.clone();

            let download_session = session.download_session.lock().unwrap();

            let raw_ptr = match download_session.buffer_ptr {
                Some(address) => unsafe { address.add(partition_id as usize) },
                None => return Err(AppError::Other("No buffer assigned".to_string())),
            };

            let (header, manifest) = {
                let mut dumper = session
                    .dumper
                    .lock()
                    .map_err(|e| AppError::Other(e.to_string()))?;
                let header = dumper
                    .get_header()
                    .map_err(|e| AppError::Other(e.to_string()))?;
                let manifest = match session.part_manifest.clone() {
                    Some(manifest) => manifest,
                    None => dumper
                        .get_part_manifest()
                        .map_err(|e| AppError::Other(e.to_string()))?,
                };
                (header, manifest)
            };
            let updates = manifest
                .partitions
                .get(partition_id as usize)
                .ok_or(AppError::Other(format!(
                    "No partition at index {partition_id}"
                )))?;

            let block_size = manifest.block_size.unwrap_or(BLOCK_SIZE) as u64;
            let partition = updates.clone();

            let address_numeric = raw_ptr as usize;

            let semaphore = download_session.concurrency.clone();
            let runtime_handle = download_session.runtime.handle().clone();

            let a = unsafe { &*(address_numeric as *mut DownloadTask) };
            a.status
                .store(status::PENDING, std::sync::atomic::Ordering::Relaxed);
            a.progress.store(0, std::sync::atomic::Ordering::Relaxed);
            a.id.store(partition_id as u8, std::sync::atomic::Ordering::Relaxed);
            a.abort.store(0, std::sync::atomic::Ordering::Relaxed);

            drop(download_session);
            drop(session_guard);

            let out = out.to_string();

            let _s: tokio::task::JoinHandle<Result<(), AppError>> =
                runtime_handle.spawn(async move {
                    let task = unsafe { &*(address_numeric as *mut DownloadTask) };
                    let _permit = tokio::select! {
                        permit = semaphore.acquire_owned() => {
                            permit.map_err(|_| AppError::Other("Semaphore closed".to_string()))?
                        }
                        _ = async {
                            loop {
                                if task.abort.load(std::sync::atomic::Ordering::Relaxed) == 1 {
                                    break;
                                }
                                tokio::time::sleep(std::time::Duration::from_millis(50)).await;
                            }
                        } => {
                            task.status
                                .store(status::FAILED, std::sync::atomic::Ordering::Relaxed);
                            return Err(AppError::Other("Cancelled by user".to_string()));
                        }
                    };

                    task.id
                        .store(partition_id as u8, std::sync::atomic::Ordering::Relaxed);
                    task.status
                        .store(status::RUNNING, std::sync::atomic::Ordering::Relaxed);
                    let mut dumper = PayloadDumper::new(payload)?;

                    let out_path = PathBuf::from(&out);

                    match dumper
                        .dump(
                            partition.clone(),
                            block_size,
                            &out_path,
                            header,
                            false,
                            Some(|p| {
                                if task.abort.load(std::sync::atomic::Ordering::Relaxed) == 1 {
                                    return false;
                                }
                                task.progress
                                    .store(p as i32, std::sync::atomic::Ordering::Relaxed);
                                true
                            }),
                        )
                        .await
                    {
                        Ok(_) => {
                            if verify {
                                if let Some(info) = partition.new_partition_info {
                                    task.status.store(
                                        status::VERIFYING,
                                        std::sync::atomic::Ordering::Relaxed,
                                    );
                                    let calculated =
                                        calculate_sha256_hash(&out, block_size as usize)?;
                                    let expected_hash = info.hash();
                                    if calculated != hex::encode(expected_hash) {
                                        task.status.store(
                                            status::VERIFICATION_FAILED,
                                            std::sync::atomic::Ordering::Relaxed,
                                        );
                                        add_error(partition_id, "Hash mismatch".to_string());
                                        return Err(AppError::Other("Hash mismatch".to_string()));
                                    } else {
                                        task.status.store(
                                            status::COMPLETED,
                                            std::sync::atomic::Ordering::Relaxed,
                                        );
                                    }
                                } else {
                                    task.status.store(
                                        status::VERIFICATION_FAILED,
                                        std::sync::atomic::Ordering::Relaxed,
                                    );
                                    add_error(
                                        partition_id,
                                        "Hash not found from metadata".to_string(),
                                    );
                                    return Err(AppError::Other(
                                        "Hash not found from metadata".to_string(),
                                    ));
                                }
                            }

                            task.status
                                .store(status::COMPLETED, std::sync::atomic::Ordering::Relaxed);
                        }
                        Err(e) => {
                            add_error(partition_id, e.to_string());
                            task.status
                                .store(status::FAILED, std::sync::atomic::Ordering::Relaxed);
                        }
                    };

                    drop(_permit);

                    Ok(())
                });
            // let session_guard_reopen = engine_lock.read().unwrap();
            // if let Some(session_reopen) = session_guard_reopen.as_ref() {
            //     let download_session_reopen = session_reopen.download_session.lock().unwrap();
            //     let mut active_task_guard = download_session_reopen.active_task.lock().unwrap();
            //     // active_task_guard.insert(partition_id as u8, s);
            // }

            Ok(0)
        })
        .map_err(|e| AppError::Other(format!("Rust panicked: {e:?}")))??;

        Ok(b)
    });

    o.resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[jni_mangle("com.rajmani7584.payloaddumper.nativeHelper.PayloadDumper")]
pub fn cancel_dump(mut env_u: EnvUnowned, _class: JClass, partition_id: jint) -> jint {
    let o = env_u.with_env(|_env| -> Result<_, jni::errors::Error> {
        let b = panic::catch_unwind(|| -> AppResult<i32> {
            let engine = get_engine()
                .read()
                .map_err(|e| AppError::Other(e.to_string()))?;

            let session = engine
                .as_ref()
                .ok_or(AppError::Other("Engine not intialized yet".to_string()))?;

            let download_session = session
                .download_session
                .lock()
                .map_err(|e| AppError::Other(e.to_string()))?;

            let mut active_task = download_session
                .active_task
                .lock()
                .map_err(|e| AppError::Other(e.to_string()))?;
            if let Some(task) = active_task.remove(&(partition_id as u8)) {
                task.abort();
                Ok(1)
            } else {
                return Err(AppError::Other("No active task found".to_string()));
            }
        })
        .map_err(|e| AppError::Other(format!("Rust panicked: {e:?}")))??;

        Ok(b)
    });

    o.resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[jni_mangle("com.rajmani7584.payloaddumper.nativeHelper.PayloadDumper")]
pub fn fetchDumpError(mut env_u: EnvUnowned, _class: JClass, id: jint) -> jstring {
    let o = env_u.with_env(|env| -> Result<_, jni::errors::Error> {
        let b = panic::catch_unwind(|| -> AppResult<String> {
            let engine = get_engine()
                .read()
                .map_err(|e| AppError::Other(e.to_string()))?;

            let session = engine
                .as_ref()
                .ok_or(AppError::Other("Engine not intialized yet".to_string()))?;

            let download_session = session
                .download_session
                .lock()
                .map_err(|e| AppError::Other(e.to_string()))?;

            let errors = download_session
                .errors
                .lock()
                .map_err(|e| AppError::Other(e.to_string()))?;
            if let Some(error) = errors.get(&(id as u8)) {
                Ok(error.clone())
            } else {
                Ok("".to_string())
            }
        })
        .map_err(|e| AppError::Other(format!("Rust panicked: {e:?}")))??;

        JString::from_str(env, &b)
    });

    o.resolve::<jni::errors::ThrowRuntimeExAndDefault>()
        .into_raw()
}

#[jni_mangle("com.rajmani7584.payloaddumper.nativeHelper.PayloadDumper")]
pub fn calculateHash(
    mut env_u: EnvUnowned,
    _class: JClass,
    path: JString,
    buf_size: jint,
) -> jstring {
    let o = env_u.with_env(|env| -> Result<_, jni::errors::Error> {
        let b = panic::catch_unwind(|| -> AppResult<String> {
            let path = path.to_string();
            calculate_sha256_hash(&path, buf_size as usize)
        })
        .map_err(|e| AppError::Other(format!("Rust panicked: {e:?}")))??;

        JString::from_str(env, &b)
    });

    o.resolve::<jni::errors::ThrowRuntimeExAndDefault>()
        .into_raw()
}

impl Payload {
    fn from_type(p_type: u8, path: &str) -> AppResult<Self> {
        match p_type {
            0 => Ok(Payload::from_file(path)),
            1 => Ok(Payload::from_url(path)),
            _ => Err(AppError::Other("unknown p_type".to_string())),
        }
    }
}

impl From<AppError> for jni::errors::Error {
    fn from(value: AppError) -> Self {
        jni::errors::Error::ParseFailed(value.to_string())
    }
}

fn get_engine() -> &'static RwLock<Option<Session>> {
    ENGINE.get_or_init(|| RwLock::new(None))
}

fn add_error(partition_id: jint, error: String) {
    if let Some(engine_lock) = ENGINE.get() {
        if let Ok(engine) = engine_lock.read() {
            if let Some(session) = engine.as_ref() {
                if let Ok(ds) = session.download_session.lock() {
                    if let Ok(mut errors) = ds.errors.lock() {
                        errors.insert(partition_id as u8, error);
                    }
                }
            }
        }
    }
}

fn calculate_sha256_hash(path: &str, buf_size: usize) -> AppResult<String> {
    let mut hasher = Sha256::new();
    let file = File::open(path)?;
    let mut reader = BufReader::new(file);
    let mut buf = vec![0; buf_size];
    loop {
        let bytes_read = reader.read(&mut buf)?;
        if bytes_read == 0 {
            break;
        }
        hasher.update(&buf[..bytes_read]);
    }
    let new_hash = hex::encode(hasher.finalize());

    Ok(new_hash)
}
