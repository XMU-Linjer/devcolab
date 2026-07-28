from dataclasses import dataclass
from pathlib import PurePosixPath
from typing import Any

SUPPORTED_CODE_EXTENSIONS = {
    ".java": "Java",
    ".kt": "Kotlin",
    ".kts": "Kotlin",
    ".py": "Python",
    ".ts": "TypeScript",
    ".tsx": "TypeScript",
    ".js": "JavaScript",
    ".jsx": "JavaScript",
    ".vue": "Vue",
    ".go": "Go",
    ".c": "C",
    ".h": "C",
    ".cc": "C++",
    ".cpp": "C++",
    ".hpp": "C++",
    ".cs": "C#",
    ".rs": "Rust",
    ".sh": "Shell",
    ".bash": "Shell",
    ".ps1": "PowerShell",
}
TEXT_NON_CODE_EXTENSIONS = {
    ".md",
    ".txt",
    ".json",
    ".yaml",
    ".yml",
    ".xml",
    ".sql",
    ".toml",
    ".ini",
    ".conf",
    ".properties",
    ".lock",
}
BINARY_EXTENSIONS = {
    ".png",
    ".jpg",
    ".jpeg",
    ".gif",
    ".webp",
    ".ico",
    ".pdf",
    ".zip",
    ".jar",
    ".war",
    ".class",
    ".exe",
    ".dll",
    ".so",
    ".bin",
    ".woff",
    ".woff2",
}
SKIPPED_DIRECTORY_SEGMENTS = {
    ".git",
    "node_modules",
    "vendor",
    "dist",
    "build",
    "target",
    "coverage",
    ".idea",
    ".vscode",
    "__pycache__",
    ".venv",
    "generated",
    "logs",
    ".data",
}


@dataclass(frozen=True)
class ClassifiedFile:
    file_path: str
    classification: str
    language: str | None
    size_bytes: int
    deleted: bool = False
    file_name: str = ""
    extension: str = ""
    is_generated: bool = False

    @property
    def eligible(self) -> bool:
        return self.classification == "SUPPORTED_CODE"


def classify_file(
    item: dict[str, Any],
    *,
    max_size_bytes: int,
    deleted: bool = False,
) -> ClassifiedFile:
    path = str(item.get("filePath", "")).replace("\\", "/")
    pure = PurePosixPath(path)
    extension = pure.suffix.lower()
    size = max(0, int(item.get("sizeBytes") or 0))
    language = item.get("language") or SUPPORTED_CODE_EXTENSIONS.get(extension)
    segments = {segment.lower() for segment in pure.parts[:-1]}
    generated = "generated" in segments or any(
        segment.endswith("-generated") for segment in segments
    )
    vendor = bool(segments & (SKIPPED_DIRECTORY_SEGMENTS - {"generated"}))
    if generated:
        classification = "GENERATED_SKIPPED"
    elif vendor:
        classification = "VENDOR_SKIPPED"
    elif (
        bool(item.get("binaryFile"))
        or item.get("readable") is False
        or extension in BINARY_EXTENSIONS
    ):
        classification = "BINARY_SKIPPED"
    elif extension in SUPPORTED_CODE_EXTENSIONS:
        if size > max_size_bytes:
            classification = "OVERSIZED_SKIPPED"
        else:
            classification = "SUPPORTED_CODE"
    elif extension in TEXT_NON_CODE_EXTENSIONS or pure.name.lower() in {
        "dockerfile",
        "makefile",
        "nginx.conf",
    }:
        classification = "TEXT_NON_CODE_SKIPPED"
    else:
        classification = "UNSUPPORTED_EXTENSION_SKIPPED"
    return ClassifiedFile(
        path, classification, language, size, deleted,
        pure.name, extension, generated,
    )
