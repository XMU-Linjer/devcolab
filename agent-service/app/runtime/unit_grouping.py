from collections import defaultdict
from pathlib import PurePosixPath
from typing import Any
from uuid import NAMESPACE_URL, UUID, uuid5

from app.runtime.file_classification import ClassifiedFile
from app.schemas.jobs import AnalysisUnit


def build_analysis_units(
    files: list[ClassifiedFile],
    bindings_by_path: dict[str, list[dict[str, Any]]],
    *,
    source_type: str,
    max_files: int,
    max_deleted: int,
    max_units: int,
) -> list[AnalysisUnit]:
    eligible = {item.file_path: item for item in files if item.eligible}
    parents = {path: path for path in eligible}

    def find(path: str) -> str:
        while parents[path] != path:
            parents[path] = parents[parents[path]]
            path = parents[path]
        return path

    def union(left: str, right: str) -> None:
        left_root, right_root = find(left), find(right)
        if left_root != right_root:
            parents[max(left_root, right_root)] = min(left_root, right_root)

    document_paths: dict[str, list[str]] = defaultdict(list)
    for path in sorted(eligible):
        for binding in bindings_by_path.get(path, []):
            document_paths[str(binding["documentId"])].append(path)
    for paths in document_paths.values():
        for path in paths[1:]:
            union(paths[0], path)

    bound_components: dict[str, list[str]] = defaultdict(list)
    unbound_groups: dict[tuple[str, str], list[str]] = defaultdict(list)
    for path, item in sorted(eligible.items()):
        if bindings_by_path.get(path):
            bound_components[find(path)].append(path)
        else:
            unbound_groups[(_module_directory(path), item.language or "Unknown")].append(path)

    units: list[AnalysisUnit] = []
    for paths in bound_components.values():
        units.extend(
            _split_component(
                paths, eligible, bindings_by_path, source_type, max_files, max_deleted, True
            )
        )
    for paths in unbound_groups.values():
        units.extend(
            _split_component(
                paths, eligible, bindings_by_path, source_type, max_files, max_deleted, False
            )
        )
    units.sort(key=lambda unit: (unit.primaryDirectory, unit.filePaths, unit.deletedPaths))
    if len(units) > max_units:
        raise ValueError("UNIT_LIMIT_EXCEEDED")
    return units


def _split_component(
    paths: list[str],
    files: dict[str, ClassifiedFile],
    bindings: dict[str, list[dict[str, Any]]],
    source_type: str,
    max_files: int,
    max_deleted: int,
    bound: bool,
) -> list[AnalysisUnit]:
    readable_count = sum(not files[path].deleted for path in paths)
    deleted_count = sum(files[path].deleted for path in paths)
    buckets: dict[tuple[str, str], list[str]] = defaultdict(list)
    if bound and readable_count <= max_files and deleted_count <= max_deleted:
        first = min(paths)
        buckets[(_module_directory(first), "BOUND_COMPONENT")] = sorted(paths)
    else:
        for path in sorted(paths):
            item = files[path]
            buckets[(_module_directory(path), item.language or "Unknown")].append(path)
    result: list[AnalysisUnit] = []
    for (directory, _language), bucket in sorted(buckets.items()):
        readable = [path for path in bucket if not files[path].deleted]
        deleted = [path for path in bucket if files[path].deleted]
        readable_chunks = [
            readable[index : index + max_files] for index in range(0, len(readable), max_files)
        ] or [[]]
        deleted_chunks = [
            deleted[index : index + max_deleted] for index in range(0, len(deleted), max_deleted)
        ] or [[]]
        chunks = max(len(readable_chunks), len(deleted_chunks))
        for index in range(chunks):
            current = (readable_chunks[index] if index < len(readable_chunks) else []) + (
                deleted_chunks[index] if index < len(deleted_chunks) else []
            )
            current_bindings = [binding for path in current for binding in bindings.get(path, [])]
            file_paths = sorted(path for path in current if not files[path].deleted)
            deleted_paths = sorted(path for path in current if files[path].deleted)
            reasons = [
                "SHARED_BOUND_DOCUMENT" if bound else "SAME_MODULE_DIRECTORY",
                "SAME_LANGUAGE",
            ]
            if len(bucket) > max_files:
                reasons.append("SIZE_LIMIT_SPLIT")
            signature = "|".join([source_type, *file_paths, "--", *deleted_paths])
            result.append(
                AnalysisUnit(
                    unitId=uuid5(NAMESPACE_URL, signature),
                    sourceType=source_type,
                    filePaths=file_paths,
                    deletedPaths=deleted_paths,
                    boundDocumentIds=sorted(
                        {UUID(str(item["documentId"])) for item in current_bindings},
                        key=str,
                    ),
                    bindingIds=sorted(
                        {UUID(str(item["bindingId"])) for item in current_bindings},
                        key=str,
                    ),
                    primaryDirectory=directory,
                    languageSet=sorted({files[path].language or "Unknown" for path in current}),
                    estimatedSizeBytes=sum(files[path].size_bytes for path in file_paths),
                    groupingReasons=reasons,
                )
            )
    return result


def _module_directory(path: str) -> str:
    parent = PurePosixPath(path).parent.as_posix()
    return "" if parent == "." else parent
