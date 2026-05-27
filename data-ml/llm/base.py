from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Protocol


@dataclass(frozen=True)
class LlmMessage:
    role: str
    content: str


class LlmApiService(Protocol):
    provider_name: str
    model_name: str

    def generate_json(self, system_prompt: str, user_prompt: str, *, temperature: float = 0.2) -> dict[str, Any]:
        ...
