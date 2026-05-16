use crate::{
    engine::chromeos_update_engine::DeltaArchiveManifest,
    helper::errors::{AppError, AppResult},
    payload::Payload,
};

mod engine;
mod helper;
mod payload;
mod reader;

fn main() -> AppResult<()> {
    let payload = Payload::File("/home/rajmani/otas/payload.bin".to_string());

    // let payload = Payload::Url(
    //     "http://10.196.59.174:3000/Project_Infinity-X-3.8-nabu-14.03.2026-GAPPS-UNOFFICIAL.zip"
    //         .to_string(),
    // );
    let mut dumper = payload::PayloadDumper::new(payload)?;

    let b = dumper.get_manifest_bytes()?;

    let manifest: DeltaArchiveManifest =
        prost::Message::decode(&*b).map_err(|e| AppError::Other(e.to_string()))?;

    dbg!(manifest.apex_info);
    Ok(())
}
