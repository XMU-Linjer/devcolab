class BudgetExceededError(RuntimeError):
    pass


class ToolCallLimitExceededError(BudgetExceededError):
    pass


def reserve_tool_call(current: int, maximum: int) -> int:
    if current >= maximum:
        raise ToolCallLimitExceededError("MCP tool call limit exceeded")
    return current + 1
