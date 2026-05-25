#[derive(Debug)]
pub enum AppError {
    Io(std::io::Error),
    JniError(jni::errors::Error),
    Other(String),
}

impl std::fmt::Display for AppError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::Io(err) => err.fmt(f),
            Self::JniError(err) => write!(f, "Jni Error: {}", err),
            Self::Other(err) => write!(f, "Error: {}", err),
        }
    }
}

impl From<String> for AppError {
    fn from(value: String) -> Self {
        Self::Other(value)
    }
}

impl From<std::io::Error> for AppError {
    fn from(value: std::io::Error) -> Self {
        Self::Io(value)
    }
}

impl std::error::Error for AppError {}
impl From<jni::errors::Error> for AppError {
    fn from(value: jni::errors::Error) -> Self {
        Self::JniError(value)
    }
}

pub type AppResult<T> = std::result::Result<T, AppError>;
