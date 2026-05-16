#[derive(Debug)]
pub enum AppError {
    Io(std::io::Error),
    Other(String),
}

impl std::fmt::Display for AppError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::Io(err) => err.fmt(f),
            Self::Other(err) => write!(f, "Error: {}", err),
        }
    }
}

impl From<std::io::Error> for AppError {
    fn from(value: std::io::Error) -> Self {
        Self::Io(value)
    }
}

impl std::error::Error for AppError {}
impl From<jni::errors::Error> for AppError {
    fn from(_value: jni::errors::Error) -> Self {
        Self::Other("Jni Error".to_string())
    }
}

impl From<String> for AppError {
    fn from(value: String) -> Self {
        Self::Other(value)
    }
}

pub type AppResult<T> = std::result::Result<T, AppError>;
