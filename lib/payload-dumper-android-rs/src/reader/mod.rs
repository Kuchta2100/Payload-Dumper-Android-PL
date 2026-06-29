mod local_reader;
pub(crate) mod macros;
mod remote_reader;
pub(crate) mod zip_reader;

use std::io::{self, Read, Seek, SeekFrom};

use crate::{
    helper::{
        constants::{PAYLOAD_HEADER_MAGIC, ZIP_HEADER_MAGIC},
        errors::{AppError, AppResult},
    },
    payload::Payload,
    reader::{
        local_reader::LocalPayloadReader, remote_reader::RemotePayloadReader, zip_reader::ZipReader,
    },
};

pub(crate) struct PayloadReader {
    inner: Box<dyn Reader>,
    base_offset: u64,
    size: Option<u64>,
    position: u64,
}

#[allow(dead_code)]
pub(crate) trait Reader: Read + Seek + Send + Sync {
    fn len(&self) -> Option<u64>;
    fn position(&mut self) -> AppResult<u64> {
        Ok(self.stream_position()?)
    }
    fn is_eof(&mut self) -> AppResult<bool> {
        match self.len() {
            Some(len) => Ok(self.position()? >= len),
            None => Ok(false),
        }
    }
    fn is_http(&self) -> Option<bool>;
}

impl Reader for PayloadReader {
    fn len(&self) -> Option<u64> {
        self.size.or_else(|| self.inner.len())
    }
    fn is_http(&self) -> Option<bool> {
        self.inner.is_http()
    }
}

impl PayloadReader {
    pub fn new(payload: Payload, buf_size: usize) -> AppResult<Self> {
        match payload {
            Payload::File(path) => Self::from_file(&path, buf_size),
            Payload::Url(url) => Self::from_url(&url, buf_size),
        }
    }
    fn from_file(path: &str, buf_size: usize) -> AppResult<Self> {
        Ok(Self {
            inner: Box::new(LocalPayloadReader::new(path, buf_size)?),
            base_offset: 0,
            size: None,
            position: 0,
        })
    }

    fn from_url(url: &str, buf_size: usize) -> AppResult<Self> {
        Ok(Self {
            inner: Box::new(RemotePayloadReader::new(url, buf_size)?),
            base_offset: 0,
            size: None,
            position: 0,
        })
    }

    pub(crate) fn calculate_offset(mut self) -> AppResult<Self> {
        let mut buf = [0u8; 4];
        self.seek(SeekFrom::Start(0))?;
        self.read(&mut buf)?;
        let len = self.len();

        if buf == PAYLOAD_HEADER_MAGIC {
            return self.with_offset(0, len);
        }

        if buf == ZIP_HEADER_MAGIC {
            let mut zip_reader = ZipReader::new(&mut self);
            let offset = zip_reader.payload_offset()?;
            return self.with_offset(offset, len);
        }

        Err(AppError::Other("Invalid payload header".into()))
    }
}
impl PayloadReader {
    pub fn with_offset(mut self, base_offset: u64, size: Option<u64>) -> AppResult<Self> {
        self.inner.seek(std::io::SeekFrom::Start(base_offset))?;
        Ok(Self {
            inner: self.inner,
            base_offset,
            size,
            position: 0,
        })
    }
}

impl Read for PayloadReader {
    fn read(&mut self, buf: &mut [u8]) -> io::Result<usize> {
        let max_read = match self.size {
            Some(size) => {
                let remain = size.saturating_sub(self.position);

                if remain == 0 {
                    return Ok(0);
                }
                remain.min(buf.len() as u64) as usize
            }
            None => buf.len(),
        };
        let n = self.inner.read(&mut buf[..max_read])?;
        self.position += n as u64;
        Ok(n)
    }
}

impl Seek for PayloadReader {
    fn seek(&mut self, pos: std::io::SeekFrom) -> io::Result<u64> {
        let target = match pos {
            std::io::SeekFrom::Start(v) => v,

            std::io::SeekFrom::Current(v) => {
                let target = self.position as i64 + v;

                if target < 0 {
                    return Err(io::Error::new(io::ErrorKind::InvalidInput, "negative seek"));
                }

                target as u64
            }

            std::io::SeekFrom::End(v) => {
                let size = self
                    .size
                    .ok_or_else(|| io::Error::new(io::ErrorKind::Other, "unknown size"))?;

                let target = size as i64 + v;

                if target < 0 {
                    return Err(io::Error::new(io::ErrorKind::InvalidInput, "negative seek"));
                }

                target as u64
            }
        };

        if let Some(size) = self.size {
            if target > size {
                return Err(io::Error::new(
                    io::ErrorKind::UnexpectedEof,
                    "seek beyond range",
                ));
            }
        }
        let absolute = self.base_offset + target;

        self.inner.seek(std::io::SeekFrom::Start(absolute))?;
        self.position = target;
        Ok(target)
    }
}
