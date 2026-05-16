mod engine;
mod helper;
mod payload;
mod reader;

use std::sync::{Mutex, OnceLock, RwLock};

use jni::{
    EnvUnowned, jni_mangle,
    objects::{JClass, JString},
    sys::{jbyteArray, jint, jstring},
};

use crate::{
    helper::errors::{AppError, AppResult},
    payload::{Payload, PayloadDumper},
};

struct Session {
    dumper: Mutex<PayloadDumper>,
}
static ENGINE: OnceLock<RwLock<Option<Session>>> = OnceLock::new();

#[jni_mangle("com.rajmani7584.payloaddumper.nativeHelper.PayloadDumper")]
pub fn init_session(mut e: EnvUnowned, _class: JClass, p_type: jint, path: JString) -> jbyteArray {
    let res = e.with_env(|env| -> Result<_, _> {
        let payload = Payload::from_type(p_type as u8, &path.to_string())?;

        let session = Session {
            dumper: Mutex::new(PayloadDumper::new(payload)?),
        };

        let b = session
            .dumper
            .lock()
            .map_err(|e| AppError::Other(e.to_string()))?
            .get_manifest_bytes()?;

        let mut engine = get_engine()
            .write()
            .map_err(|e| AppError::Other(e.to_string()))?;

        *engine = Some(session);

        env.byte_array_from_slice(&b)
    });

    res.resolve::<jni::errors::ThrowRuntimeExAndDefault>()
        .into_raw()
}

#[jni_mangle("com.rajmani7584.payloaddumper.nativeHelper.PayloadDumper")]
pub fn fetch_header(mut env_u: EnvUnowned, _class: JClass) -> jstring {
    let o = env_u.with_env(|env| -> Result<_, _> {
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

        env.new_string(&s)
    });

    o.resolve::<jni::errors::ThrowRuntimeExAndDefault>()
        .into_raw()
}

impl Payload {
    fn from_type(p_type: u8, path: &str) -> AppResult<Self> {
        match p_type {
            0 => Ok(Payload::File(path.to_string())),
            1 => Ok(Payload::Url(path.to_string())),
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
