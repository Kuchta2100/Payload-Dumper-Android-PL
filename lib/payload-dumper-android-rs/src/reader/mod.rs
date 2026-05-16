pub(crate) mod zip_reader;

use std::{
    fs::File,
    io::{self, BufReader, Read, Seek},
    thread::sleep,
};

use ureq::Agent;

use crate::helper::errors::{AppError, AppResult};

const BUF_SIZE: usize = 256 * 1024;
const MAX_RETRIES: usize = 3;

pub(crate) struct Reader {
    inner: Box<dyn PayloadReader>,
    base_offset: u64,
    size: Option<u64>,
    position: u64,
}

impl Reader {
    pub fn from_file(path: &str) -> AppResult<Self> {
        Ok(Self {
            inner: Box::new(LocalPayloadReader::new(path)?),
            base_offset: 0,
            size: None,
            position: 0,
        })
    }

    pub fn from_url(url: &str) -> AppResult<Self> {
        Ok(Self {
            inner: Box::new(RemotePayloadReader::new(url)?),
            base_offset: 0,
            size: None,
            position: 0,
        })
    }
}
impl Reader {
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

impl Read for Reader {
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

impl Seek for Reader {
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

#[allow(dead_code)]
pub(crate) trait PayloadReader: Read + Seek + Send + Sync {
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
}

impl PayloadReader for Reader {
    fn len(&self) -> Option<u64> {
        self.size.or_else(|| self.inner.len())
    }
}

pub(crate) struct LocalPayloadReader {
    file: BufReader<File>,
    len: u64,
}

impl LocalPayloadReader {
    pub(crate) fn new(path: &str) -> AppResult<Self> {
        let file = File::open(path)?;
        let len = file.metadata()?.len();
        Ok(Self {
            file: BufReader::with_capacity(BUF_SIZE, file),
            len,
        })
    }
}

impl Read for LocalPayloadReader {
    fn read(&mut self, buf: &mut [u8]) -> std::io::Result<usize> {
        self.file.read(buf)
    }
}

impl Seek for LocalPayloadReader {
    fn seek(&mut self, pos: std::io::SeekFrom) -> std::io::Result<u64> {
        self.file.seek(pos)
    }
}

impl PayloadReader for LocalPayloadReader {
    fn len(&self) -> Option<u64> {
        Some(self.len)
    }
}

pub(crate) struct RemotePayloadReader {
    url: String,
    agent: Agent,
    response_reader: Option<Box<dyn Read + Send + Sync>>,
    offset: u64,
    total_size: Option<u64>,
    buffer: Vec<u8>,
    buffer_start: u64,
    buffer_pos: usize,
    buffer_len: usize,
    retries: usize,
    eof: bool,
}

impl RemotePayloadReader {
    fn new(url: impl Into<String>) -> AppResult<Self> {
        let agent = ureq::Agent::config_builder()
            .timeout_connect(Some(std::time::Duration::from_secs(10)))
            .timeout_recv_response(Some(std::time::Duration::from_secs(30)))
            .timeout_recv_body(Some(std::time::Duration::from_secs(30)))
            .build()
            .new_agent();

        let mut reader = Self {
            url: url.into(),
            agent,
            response_reader: None,
            offset: 0,
            total_size: None,
            buffer: vec![0; BUF_SIZE],
            buffer_start: 0,
            buffer_pos: 0,
            buffer_len: 0,
            retries: 0,
            eof: false,
        };

        reader.connect()?;

        Ok(reader)
    }

    fn connect(&mut self) -> AppResult<()> {
        let mut req = self.agent.get(&self.url);

        if self.offset > 0 {
            req = req.header("Range", &format!("bytes={}-", self.offset));
        }

        let response = req.call().map_err(|e| AppError::Io(e.into_io()))?;

        let status = response.status();

        let _ = match status.as_u16() {
            200 | 206 => {}
            _ => {
                return Err(AppError::Other(format!(
                    "Http error: {}",
                    status.canonical_reason().unwrap_or("Unknown")
                )));
            }
        };

        if self.offset > 0 && status.as_u16() != 206 {
            return Err(AppError::Other("Range not supported!".to_string()));
        }

        let headers = response.headers();
        if let Some(content_range) = headers.get("Content-Range") {
            if let Some(total) = content_range
                .to_str()
                .map_err(|_| AppError::Other("Can't get content range".to_string()))?
                .split("/")
                .nth(1)
            {
                if let Ok(v) = total.parse::<u64>() {
                    self.total_size = Some(v);
                }
            }
        } else if let Some(content_length) = headers.get("Content-Length") {
            if let Ok(len) = content_length
                .to_str()
                .map_err(|_| AppError::Other("Can't get content length".to_string()))?
                .parse::<u64>()
            {
                self.total_size = Some(len);
            }
        }
        let reader = response.into_body().into_reader();
        self.response_reader = Some(Box::new(reader));

        self.buffer_pos = 0;
        self.buffer_len = 0;

        Ok(())
    }

    fn refill_buffer(&mut self) -> AppResult<()> {
        if self.eof {
            return Ok(());
        }

        if self.response_reader.is_none() {
            self.connect()?;
        }

        loop {
            let reader = self
                .response_reader
                .as_mut()
                .ok_or_else(|| AppError::Other("Missing response reader".to_string()))?;

            match reader.read(&mut self.buffer) {
                Ok(0) => {
                    if let Some(total) = self.total_size {
                        if self.offset >= total {
                            self.eof = true;
                            return Ok(());
                        }
                    }
                    self.retry_reconnect()?;
                }
                Ok(n) => {
                    self.buffer_start = self.offset;
                    self.buffer_pos = 0;
                    self.buffer_len = n;
                    self.offset += n as u64;
                    self.retries = 0;

                    return Ok(());
                }
                Err(e) => {
                    if is_retryable(&e) {
                        self.retry_reconnect()?;
                    } else {
                        return Err(AppError::Io(e));
                    }
                }
            }
        }
    }

    fn retry_reconnect(&mut self) -> AppResult<()> {
        self.response_reader = None;

        self.retries += 1;

        if self.retries > MAX_RETRIES {
            return Err(AppError::Other("max retries exceeded".to_string()));
        }

        let delay = std::time::Duration::from_millis(500 * self.retries as u64);

        sleep(delay);

        self.connect()
    }
}

impl Read for RemotePayloadReader {
    fn read(&mut self, buf: &mut [u8]) -> io::Result<usize> {
        if buf.is_empty() {
            return Ok(0);
        }

        if self.eof {
            return Ok(0);
        }

        let mut written = 0;

        while written < buf.len() {
            let available = self.buffer_len.saturating_sub(self.buffer_pos);

            if available == 0 {
                self.refill_buffer()
                    .map_err(|e| io::Error::new(io::ErrorKind::Other, e))?;

                if self.eof {
                    break;
                }

                continue;
            }

            let remaining = buf.len() - written;

            let to_copy = available.min(remaining);

            let slice = &self.buffer[self.buffer_pos..self.buffer_pos + to_copy];

            buf[written..written + to_copy].copy_from_slice(slice);

            self.buffer_pos += to_copy;

            written += to_copy;
        }

        Ok(written)
    }
}

impl Seek for RemotePayloadReader {
    fn seek(&mut self, pos: std::io::SeekFrom) -> io::Result<u64> {
        let current = self.buffer_start + self.buffer_pos as u64;

        let new_offset = match pos {
            std::io::SeekFrom::Start(offset) => offset,
            std::io::SeekFrom::End(offset) => {
                if let Some(total) = self.total_size {
                    let target = total as i64 + offset;
                    if target < 0 {
                        return Err(io::Error::new(
                            io::ErrorKind::InvalidData,
                            "Negative offset underflow",
                        ));
                    }

                    target as u64
                } else {
                    return Err(io::Error::new(io::ErrorKind::InvalidData, "Unknown length"));
                }
            }
            std::io::SeekFrom::Current(offset) => {
                let offset = offset as i64;
                if offset >= 0 {
                    current + offset as u64
                } else {
                    current.checked_sub(offset.abs() as u64).ok_or_else(|| {
                        io::Error::new(io::ErrorKind::InvalidData, "Negative offset underflow")
                    })?
                }
            }
        };

        if self.buffer_start <= new_offset
            && new_offset < self.buffer_start + self.buffer_len as u64
        {
            self.buffer_pos = (new_offset - self.buffer_start) as usize;

            return Ok(new_offset);
        }
        self.response_reader = None;

        self.offset = new_offset;

        self.buffer_pos = 0;
        self.buffer_len = 0;

        self.eof = false;

        Ok(new_offset)
    }
}

impl PayloadReader for RemotePayloadReader {
    fn len(&self) -> Option<u64> {
        self.total_size
    }
}

fn is_retryable(e: &io::Error) -> bool {
    matches!(
        e.kind(),
        io::ErrorKind::TimedOut
            | io::ErrorKind::UnexpectedEof
            | io::ErrorKind::ConnectionReset
            | io::ErrorKind::ConnectionAborted
            | io::ErrorKind::BrokenPipe
            | io::ErrorKind::Interrupted
    )
}

#[macro_export]
macro_rules! read_le {
    ($b:expr, u16) => {{ u16::from_le_bytes($b.try_into().expect("Can't get u16")) }};
    ($b:expr, u32) => {{ u32::from_le_bytes($b.try_into().expect("Can't get u32")) }};
    ($b:expr, u64) => {{ u64::from_le_bytes($b.try_into().expect("Can't get u64")) }};
}

#[macro_export]
macro_rules! read_be {
    ($r:expr, u64) => {{
        let mut buf = [0; 8];
        $r.read_exact(&mut buf)?;
        u64::from_be_bytes(buf)
    }};
    ($r:expr, u32) => {{
        let mut buf = [0; 4];
        $r.read_exact(&mut buf)?;
        u32::from_be_bytes(buf)
    }};
    ($r:expr, u16) => {{
        let mut buf = [0; 2];
        $r.read_exact(&mut buf)?;
        u16::from_be_bytes(buf)
    }};
    ($r:expr, [u8; $n:expr]) => {{
        let mut buf = [0; $n];
        $r.read_exact(&mut buf)?;
        buf
    }};
}
