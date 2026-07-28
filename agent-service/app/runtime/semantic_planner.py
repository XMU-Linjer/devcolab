from __future__ import annotations

import hashlib
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from pathlib import PurePosixPath
from typing import Any
from uuid import NAMESPACE_URL, UUID, uuid5

SEMANTIC_KINDS = {
    "FRONTEND_API_CLIENT",
    "BACKEND_REST_API",
    "BUSINESS_SERVICE",
    "SECURITY",
    "DATA_ACCESS",
    "WORKER_PROCESS",
    "INTEGRATION",
    "INFRASTRUCTURE_CODE",
    "GENERIC_MODULE",
}


@dataclass(frozen=True)
class ProjectFile:
    id: UUID
    file_path: str
    language: str
    size_bytes: int
    package_name: str | None = None
    module_key: str | None = None
    layer_hint: str | None = None
    role_hints: tuple[str, ...] = ()
    import_keys: tuple[str, ...] = ()
    exported_symbols: tuple[str, ...] = ()
    top_level_symbols: tuple[str, ...] = ()


@dataclass(frozen=True)
class UnitFile:
    job_file_id: UUID
    file_path: str
    role: str
    relevance_reason: str
    ordinal: int


@dataclass(frozen=True)
class UnitDocument:
    document_id: UUID
    relationship: str
    source: str
    ordinal: int


@dataclass(frozen=True)
class PlannedSemanticUnit:
    id: UUID
    semantic_key: str
    display_name: str
    semantic_kind: str
    primary_directory: str
    language_set: tuple[str, ...]
    estimated_size_bytes: int
    grouping_reasons: tuple[str, ...]
    unit_fingerprint: str
    files: tuple[UnitFile, ...]
    documents: tuple[UnitDocument, ...] = field(default_factory=tuple)

    @property
    def primary_paths(self) -> tuple[str, ...]:
        return tuple(item.file_path for item in self.files if item.role == "PRIMARY")


def build_semantic_units(
    files: list[ProjectFile],
    bindings_by_path: dict[str, list[dict[str, Any]]],
    *,
    job_id: UUID,
    revision: str,
    max_primary_files: int,
    max_supporting_files: int,
    max_total_files: int,
    max_units: int,
) -> list[PlannedSemanticUnit]:
    by_path = {item.file_path: item for item in files}
    bound_documents: dict[UUID, set[str]] = defaultdict(set)
    for path in sorted(by_path):
        for binding in bindings_by_path.get(path, []):
            document_id = UUID(str(binding["documentId"]))
            bound_documents[document_id].add(path)

    drafts: list[
        tuple[str, list[str], list[str], UUID | None, list[UUID], list[str]]
    ] = []
    for document_id, document_paths in sorted(
        bound_documents.items(), key=lambda item: str(item[0])
    ):
        groups = _stable_chunks(
            sorted(document_paths),
            by_path,
            max_primary_files,
        )
        for group in groups:
            kinds = [_semantic_kind(by_path[path]) for path in group]
            kind = _dominant_kind(kinds)
            reasons = ["SHARED_BOUND_DOCUMENT", "EXISTING_BINDING"]
            if len(groups) > 1:
                reasons.append("SIZE_LIMIT_SPLIT")
            supporting = _supporting_files(
                group,
                by_path,
                max_supporting_files,
                max_total_files,
            )
            related_documents = sorted(
                {
                    UUID(str(binding["documentId"]))
                    for path in group
                    for binding in bindings_by_path.get(path, [])
                },
                key=str,
            )
            drafts.append(
                (
                    kind,
                    group,
                    supporting,
                    document_id,
                    related_documents,
                    reasons,
                )
            )

    unbound = [
        item for item in files
        if not bindings_by_path.get(item.file_path)
    ]
    role_groups: dict[tuple[str, str, str], list[str]] = defaultdict(list)
    for item in unbound:
        kind = _semantic_kind(item)
        module = item.module_key or _module_key(item.file_path)
        feature = _feature_key(item)
        role_groups[(module, feature, kind)].append(item.file_path)

    for (_module, feature, kind), paths in sorted(role_groups.items()):
        for primary in _stable_chunks(sorted(paths), by_path, max_primary_files):
            supporting = _supporting_files(
                primary,
                by_path,
                max_supporting_files,
                max_total_files,
            )
            reasons = ["SAME_MODULE", "ROLE_COMPATIBLE"]
            if feature:
                reasons.append("SAME_FEATURE")
            drafts.append((kind, primary, supporting, None, [], reasons))

    units: list[PlannedSemanticUnit] = []
    seen: set[tuple[str, tuple[str, ...], str]] = set()
    for kind, primary, supporting, target_document, documents, reasons in drafts:
        primary = sorted(dict.fromkeys(primary))
        supporting = [
            path for path in sorted(dict.fromkeys(supporting))
            if path not in primary
        ][: max(0, max_total_files - len(primary))]
        if not primary:
            continue
        identity = (kind, tuple(primary), str(target_document or ""))
        if identity in seen:
            continue
        seen.add(identity)
        fingerprint = _fingerprint(
            kind, primary, documents, revision, target_document=target_document
        )
        directory = _common_directory(primary)
        semantic_key = f"{kind.lower()}:{directory or 'root'}:{fingerprint[:12]}"
        unit_files = [
            UnitFile(
                by_path[path].id,
                path,
                "PRIMARY",
                _primary_reason(by_path[path], kind),
                index,
            )
            for index, path in enumerate(primary, 1)
        ]
        unit_files.extend(
            UnitFile(
                by_path[path].id,
                path,
                _supporting_role(by_path[path], kind),
                "DIRECT_IMPORT_OR_SHARED_INFRASTRUCTURE",
                len(unit_files) + index,
            )
            for index, path in enumerate(supporting, 1)
        )
        ordered_documents = (
            [target_document]
            + [
                document_id for document_id in sorted(documents, key=str)
                if document_id != target_document
            ]
            if target_document is not None
            else []
        )
        unit_documents = tuple(
            UnitDocument(
                document_id,
                "BOUND" if document_id == target_document else "RELATED_BOUND",
                "EXISTING_BINDING",
                index,
            )
            for index, document_id in enumerate(ordered_documents, 1)
        )
        all_paths = primary + supporting
        units.append(
            PlannedSemanticUnit(
                id=uuid5(NAMESPACE_URL, f"devcollab:{job_id}:{fingerprint}"),
                semantic_key=semantic_key,
                display_name=_display_name(kind, directory, primary),
                semantic_kind=kind,
                primary_directory=directory,
                language_set=tuple(sorted({by_path[path].language for path in all_paths})),
                estimated_size_bytes=sum(by_path[path].size_bytes for path in all_paths),
                grouping_reasons=tuple(dict.fromkeys(reasons)),
                unit_fingerprint=fingerprint,
                files=tuple(unit_files),
                documents=unit_documents,
            )
        )
    units.sort(
        key=lambda unit: (
            unit.semantic_kind,
            unit.primary_directory,
            unit.primary_paths,
            unit.unit_fingerprint,
        )
    )
    if len(units) > max_units:
        raise ValueError("UNIT_LIMIT_EXCEEDED")
    return units


def overlapping_file_count(units: list[PlannedSemanticUnit]) -> int:
    appearances = Counter(
        item.file_path
        for unit in units
        for item in unit.files
    )
    return sum(count > 1 for count in appearances.values())


def _semantic_kind(item: ProjectFile) -> str:
    roles = {value.upper() for value in item.role_hints}
    path = item.file_path.lower()
    name = PurePosixPath(path).name.lower()
    if roles & {"SECURITY", "FILTER", "AUTH_FILTER", "AUTH_CONFIGURATION"} or any(
        token in name for token in ("security", "authenticationfilter", "jwtfilter")
    ):
        return "SECURITY"
    if roles & {"REST_CONTROLLER", "CONTROLLER"} or name.endswith("controller.java"):
        return "BACKEND_REST_API"
    if roles & {"FRONTEND_API_CLIENT", "API_CLIENT", "HTTP_CLIENT"} or (
        ("/api/" in path or name.endswith(("api.ts", "api.js")))
        and item.language in {"TypeScript", "JavaScript", "Vue"}
    ):
        return "FRONTEND_API_CLIENT"
    if roles & {"REPOSITORY", "DATA_ACCESS"} or name.endswith(
        ("repository.java", "dao.java")
    ):
        return "DATA_ACCESS"
    if roles & {"WORKER", "CONSUMER", "SCHEDULER"} or any(
        token in name for token in ("worker", "consumer", "scheduler")
    ):
        return "WORKER_PROCESS"
    if roles & {"SERVICE", "APPLICATION_SERVICE"} or name.endswith("service.java"):
        return "BUSINESS_SERVICE"
    if roles & {"INTEGRATION", "GATEWAY", "CLIENT"} or any(
        token in name for token in ("gateway", "client")
    ):
        return "INTEGRATION"
    if roles & {"CONFIG", "CONFIGURATION", "INFRASTRUCTURE"} or any(
        token in path for token in ("/config/", "/infrastructure/", "docker", "deploy/")
    ):
        return "INFRASTRUCTURE_CODE"
    return "GENERIC_MODULE"


def _supporting_files(
    primary: list[str],
    by_path: dict[str, ProjectFile],
    max_supporting: int,
    max_total: int,
) -> list[str]:
    if max_supporting <= 0 or len(primary) >= max_total:
        return []
    import_tokens = {
        token
        for path in primary
        for token in by_path[path].import_keys
        if token
    }
    primary_module = {by_path[path].module_key or _module_key(path) for path in primary}
    candidates: list[tuple[int, str]] = []
    for path, item in by_path.items():
        if path in primary:
            continue
        score = 0
        normalized_path = path.removesuffix(PurePosixPath(path).suffix).replace("/", ".")
        stem = PurePosixPath(path).stem
        if any(
            token == normalized_path
            or token.endswith(f".{stem}")
            or token.endswith(f"/{stem}")
            for token in import_tokens
        ):
            score += 10
        if (item.module_key or _module_key(path)) in primary_module:
            score += 2
        if _semantic_kind(item) in {"SECURITY", "INTEGRATION", "INFRASTRUCTURE_CODE"}:
            score += 1
        if score:
            candidates.append((-score, path))
    limit = min(max_supporting, max_total - len(primary))
    return [path for _score, path in sorted(candidates)[:limit]]


def _stable_chunks(
    paths: list[str],
    by_path: dict[str, ProjectFile],
    limit: int,
) -> list[list[str]]:
    ordered = sorted(
        paths,
        key=lambda path: (
            by_path[path].module_key or _module_key(path),
            _semantic_kind(by_path[path]),
            path,
        ),
    )
    return [ordered[index:index + limit] for index in range(0, len(ordered), limit)]


def _feature_key(item: ProjectFile) -> str:
    package = item.package_name or ""
    if package:
        package_parts = package.split(".")
        return ".".join(package_parts[: min(5, len(package_parts))])
    path_parts = PurePosixPath(item.file_path).parts
    lowered = [value.lower() for value in path_parts]
    for marker in ("src", "app", "components", "views", "modules", "features"):
        if marker in lowered:
            index = lowered.index(marker)
            return "/".join(path_parts[index:index + 3])
    return "/".join(path_parts[:-1][-2:])


def _module_key(path: str) -> str:
    parts = PurePosixPath(path).parts
    if not parts:
        return ""
    if parts[0].lower() == "src":
        return "src"
    return parts[0]


def _common_directory(paths: list[str]) -> str:
    parents = [PurePosixPath(path).parent.parts for path in paths]
    common: list[str] = []
    for parts in zip(*parents, strict=False):
        if len(set(parts)) != 1:
            break
        common.append(parts[0])
    return "/".join(common)


def _dominant_kind(kinds: list[str]) -> str:
    counts = Counter(kinds)
    priority = [
        "SECURITY", "BACKEND_REST_API", "FRONTEND_API_CLIENT",
        "BUSINESS_SERVICE", "DATA_ACCESS", "WORKER_PROCESS",
        "INTEGRATION", "INFRASTRUCTURE_CODE", "GENERIC_MODULE",
    ]
    return min(counts, key=lambda kind: (-counts[kind], priority.index(kind)))


def _fingerprint(
    kind: str,
    primary: list[str],
    documents: list[UUID],
    revision: str,
    *,
    target_document: UUID | None,
) -> str:
    payload = "\n".join(
        [
            kind,
            revision,
            f"target:{target_document or ''}",
            *sorted(primary),
            "--documents--",
            *sorted(map(str, documents)),
        ]
    )
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def _primary_reason(item: ProjectFile, kind: str) -> str:
    return f"{kind}:{item.layer_hint or 'ROLE_INFERRED'}"


def _supporting_role(item: ProjectFile, kind: str) -> str:
    item_kind = _semantic_kind(item)
    if item_kind == "SECURITY":
        return "SECURITY_RELATED"
    if item_kind == "FRONTEND_API_CLIENT" or (
        kind == "BACKEND_REST_API" and item_kind == "INTEGRATION"
    ):
        return "API_CONTRACT"
    return "DEPENDENCY"


def _display_name(kind: str, directory: str, primary: list[str]) -> str:
    label = kind.replace("_", " ").title()
    subject = directory or PurePosixPath(primary[0]).stem
    return f"{label}: {subject}"
