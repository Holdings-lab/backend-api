from __future__ import annotations

import json
import os
from abc import ABC, abstractmethod
from typing import Any
from urllib import error as urllib_error
from urllib import request as urllib_request


def _extract_json_text(raw_text: str) -> dict[str, Any]:
    text = raw_text.strip()
    if text.startswith("```"):
        text = text.strip("`")
        if text.lower().startswith("json"):
            text = text[4:]
        text = text.strip()
    return json.loads(text)


class BaseHttpLlmClient(ABC):
    provider_name = "base"

    def __init__(self, model_name: str, timeout_sec: float = 25.0):
        self.model_name = model_name
        self.timeout_sec = timeout_sec

    @abstractmethod
    def generate_json(self, system_prompt: str, user_prompt: str, *, temperature: float = 0.2) -> dict[str, Any]:
        raise NotImplementedError

    def _post_json(self, url: str, headers: dict[str, str], body: dict[str, Any]) -> dict[str, Any]:
        payload = json.dumps(body, ensure_ascii=False).encode("utf-8")
        request_obj = urllib_request.Request(url, data=payload, headers=headers, method="POST")
        try:
            with urllib_request.urlopen(request_obj, timeout=self.timeout_sec) as response:
                response_text = response.read().decode("utf-8", errors="replace")
        except urllib_error.HTTPError as error:
            detail = error.read().decode("utf-8", errors="replace") if error.fp else str(error)
            raise RuntimeError(f"{self.provider_name} API error: {detail}") from error
        except Exception as error:
            raise RuntimeError(f"{self.provider_name} API request failed: {error}") from error

        try:
            return json.loads(response_text)
        except Exception as error:
            raise RuntimeError(f"{self.provider_name} API returned invalid JSON") from error


class RoutedLlmClient(BaseHttpLlmClient):
    provider_name = "routed"

    def __init__(self, provider_name: str, model_name: str, api_key: str, timeout_sec: float = 25.0):
        super().__init__(model_name=model_name, timeout_sec=timeout_sec)
        self.provider_name = provider_name
        self.api_key = api_key

    def generate_json(self, system_prompt: str, user_prompt: str, *, temperature: float = 0.2) -> dict[str, Any]:
        if not self.api_key:
            raise RuntimeError(f"{self.provider_name.upper()} API key is required")

        if self.provider_name == "anthropic":
            return self._call_claude_api(system_prompt, user_prompt, temperature=temperature)
        return self._call_gemini_api(system_prompt, user_prompt, temperature=temperature)

    def _call_gemini_api(self, system_prompt: str, user_prompt: str, *, temperature: float = 0.2) -> dict[str, Any]:
        model_candidates = [self.model_name]
        for fallback_model in ("gemini-2.0-flash", "gemini-1.5-flash-002", "gemini-1.5-pro"):
            if fallback_model not in model_candidates:
                model_candidates.append(fallback_model)

        body = {
            "systemInstruction": {"parts": [{"text": system_prompt}]},
            "contents": [{"role": "user", "parts": [{"text": user_prompt}]}],
            "generationConfig": {
                "temperature": temperature,
                "responseMimeType": "application/json",
            },
        }

        last_error: Exception | None = None
        for model_name in model_candidates:
            url = (
                f"https://generativelanguage.googleapis.com/v1beta/models/"
                f"{model_name}:generateContent?key={self.api_key}"
            )
            try:
                payload = self._post_json(url, {"Content-Type": "application/json"}, body)
                candidates = payload.get("candidates") or []
                if not candidates:
                    raise RuntimeError("gemini API returned no candidates")
                parts = (candidates[0].get("content") or {}).get("parts") or []
                text = "".join(str(part.get("text", "")) for part in parts if isinstance(part, dict))
                if not text:
                    raise RuntimeError("gemini API returned empty content")
                if model_name != self.model_name:
                    self.model_name = model_name
                return _extract_json_text(text)
            except Exception as error:
                last_error = error
                error_text = str(error)
                if "404" not in error_text and "NOT_FOUND" not in error_text and "not found" not in error_text.lower():
                    break

        if last_error is not None:
            raise last_error

        raise RuntimeError("gemini API call failed")

    def _call_claude_api(self, system_prompt: str, user_prompt: str, *, temperature: float = 0.2) -> dict[str, Any]:
        body = {
            "model": self.model_name,
            "max_tokens": 1024,
            "temperature": temperature,
            "system": system_prompt,
            "messages": [{"role": "user", "content": user_prompt}],
        }
        payload = self._post_json(
            "https://api.anthropic.com/v1/messages",
            {
                "Content-Type": "application/json",
                "x-api-key": self.api_key,
                "anthropic-version": "2023-06-01",
            },
            body,
        )
        content = payload.get("content") or []
        text = "".join(str(part.get("text", "")) for part in content if isinstance(part, dict))
        if not text:
            raise RuntimeError("anthropic API returned empty content")
        return _extract_json_text(text)
    
    def health_check(self) -> dict[str, Any]:
        """Lightweight health check that attempts a minimal JSON generation.
        Returns {'ok': True, 'provider': ..., 'model': ...} on success or {'ok': False, 'error': ...}.
        """
        try:
            # Use very small temperature and a short user prompt
            system = "Health check: respond with a JSON object {'ok': true}."
            user = '{"ping": "ping"}'
            # Use underlying generator which will raise on failures
            _ = self.generate_json(system, user, temperature=0.0)
            return {"ok": True, "provider": self.provider_name, "model": self.model_name}
        except Exception as e:
            return {"ok": False, "provider": self.provider_name, "model": self.model_name, "error": str(e)}


def build_llm_client() -> BaseHttpLlmClient:
    provider = os.getenv("LLM_PROVIDER", "gemini").strip().lower()
    timeout_sec = float(os.getenv("LLM_TIMEOUT_SEC", "25"))

    if provider == "anthropic":
        return RoutedLlmClient(
            provider_name="anthropic",
            api_key=os.getenv("ANTHROPIC_API_KEY", ""),
            model_name=os.getenv("ANTHROPIC_MODEL", "claude-3.5-haiku-20241022"),
            timeout_sec=timeout_sec,
        )

    return RoutedLlmClient(
        provider_name="gemini",
        api_key=os.getenv("GEMINI_API_KEY", ""),
        model_name=os.getenv("GEMINI_MODEL", "gemini-1.5-flash"),
        timeout_sec=timeout_sec,
    )
