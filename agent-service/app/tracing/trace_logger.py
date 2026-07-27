from collections.abc import Awaitable, Callable
from datetime import UTC, datetime
from time import perf_counter
from typing import Any


async def traced(
    state: dict[str, Any],
    node: str,
    tool: str | None,
    operation: Callable[[], Awaitable[Any]],
    input_size: int = 0,
) -> Any:
    started_at = datetime.now(UTC)
    started = perf_counter()
    success = False
    error_code = None
    output_size = 0
    try:
        result = await operation()
        output_size = len(str(result))
        success = True
        return result
    except Exception as exc:
        error_code = getattr(exc, "code", exc.__class__.__name__)
        raise
    finally:
        state.setdefault("trace_events", []).append(
            {
                "runId": state["run_id"],
                "node": node,
                "tool": tool,
                "startedAt": started_at.isoformat(),
                "endedAt": datetime.now(UTC).isoformat(),
                "durationMs": round((perf_counter() - started) * 1000),
                "success": success,
                "errorCode": error_code,
                "inputSize": input_size,
                "outputSize": output_size,
                "budget": {
                    "toolCallsUsed": state.get("tool_call_count", 0),
                    "codeCharsUsed": state.get("code_chars_used", 0),
                },
            }
        )
