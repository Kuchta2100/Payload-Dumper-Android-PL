use std::io::{Read, Seek, SeekFrom};

use crate::{
    helper::errors::{AppError, AppResult},
    read_be,
    reader::{PayloadReader, Reader, zip_reader::ZipReader},
};

const HEADER_SIZE: usize = 24;
const PAYLOAD_HEADER_MAGIC: &str = "CrAU";
const BRILLO_MAJOR_PAYLOAD_VERSION: u64 = 2;

pub enum Payload {
    File(String),
    Url(String),
}

#[derive(Debug, Clone, Copy)]
pub(crate) struct PayloadHeader {
    pub(crate) version: u64,
    pub(crate) manifest_len: u64,
    pub(crate) signature_len: u32,
}

impl PayloadHeader {
    pub(crate) fn from_bytes(mut buffer: &[u8]) -> AppResult<PayloadHeader> {
        let mut header = PayloadHeader {
            version: 0,
            manifest_len: 0,
            signature_len: 0,
        };

        let buf = read_be!(buffer, [u8; 4]);
        let magic = str::from_utf8(&buf)
            .map_err(|e| AppError::Other(format!("Invalid payload magic `{}`", e)))?;

        if magic != PAYLOAD_HEADER_MAGIC {
            return Err(AppError::Other(format!(
                "Invalid payload magic `{}`",
                magic
            )));
        }

        header.version = read_be!(buffer, u64);

        if header.version != BRILLO_MAJOR_PAYLOAD_VERSION {
            return Err(AppError::Other("Unsupported payload version".into()));
        }

        header.manifest_len = read_be!(buffer, u64);
        header.signature_len = read_be!(buffer, u32);

        Ok(header)
    }

    pub(crate) fn size() -> u64 {
        24
    }

    pub(crate) fn metadata_size(&self) -> u64 {
        Self::size() + self.manifest_len
    }

    pub(crate) fn data_offset(&self) -> u64 {
        self.signature_len as u64 + self.metadata_size()
    }
}

pub struct PayloadDumper {
    reader: Reader,
}

impl PayloadDumper {
    pub fn new(payload: Payload) -> AppResult<Self> {
        let mut reader = match payload {
            Payload::File(path) => Reader::from_file(&path)?,
            Payload::Url(url) => Reader::from_url(&url)?,
        };

        let mut buf = [0u8; 4];
        let _ = reader.read(&mut buf);
        let len = reader.len();

        if buf == *b"CrAU" {
            reader = reader.with_offset(0, len)?;
        } else if buf == *b"PK\x03\x04" {
            let mut zip_reader = ZipReader::new(&mut reader);
            let offset = zip_reader.payload_offset()?;
            reader = reader.with_offset(offset, len)?;
        } else {
            return Err(AppError::Other("Invalid payload header".into()));
        }

        Ok(Self { reader })
    }
    pub fn get_header(&mut self) -> AppResult<PayloadHeader> {
        let mut buf = vec![0u8; HEADER_SIZE];
        self.reader.seek(SeekFrom::Start(0))?;
        self.reader.read(&mut buf)?;
        PayloadHeader::from_bytes(&buf)
    }
    // pub fn get_manifest(&mut self) -> AppResult<Vec<u8>> {
    //     let header = self.get_header()?;
    //     let mut buf = vec![0u8; header.manifest_len as usize];
    //     self.reader.seek(SeekFrom::Start(24))?;
    //     self.reader.read(&mut buf)?;
    //     Ok(buf)
    // }
    pub fn get_signature(&mut self) -> AppResult<Vec<u8>> {
        let header = self.get_header()?;
        let mut buf = vec![0u8; header.signature_len as usize];
        self.reader
            .seek(SeekFrom::Start(24 + header.manifest_len))?;
        self.reader.read(&mut buf)?;
        Ok(buf)
    }
    pub fn get_manifest_bytes(&mut self) -> AppResult<Vec<u8>> {
        let header = self.get_header()?;
        let mut buf = vec![0u8; header.manifest_len as usize];
        self.reader.read(&mut buf)?;
        Ok(buf)
    }
}
