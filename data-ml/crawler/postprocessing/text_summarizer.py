from __future__ import annotations

import json
import os
import time
from typing import Any
from urllib import error as urllib_error
from urllib import request as urllib_request


DEFAULT_SUMMARY_CHAR_LIMIT = 10_000
DEFAULT_SUMMARY_OUTPUT_CHAR_LIMIT = int(os.getenv("LLM_SUMMARY_OUTPUT_CHAR_LIMIT", "1200"))
DEFAULT_TIMEOUT_SEC = float(os.getenv("LLM_TIMEOUT_SEC", "25"))
DEFAULT_RETRY_COUNT = int(os.getenv("LLM_RETRY_COUNT", "2"))


def llm_summarize(text: str, limit_chars: int = DEFAULT_SUMMARY_CHAR_LIMIT) -> str:
    """
    공급자 중립 공개 함수.
    내부적으로 선택된 LLM 공급자를 호출해 요약 문자열을 생성한다.
    """
    source_text = (text or "").strip()
    if not source_text:
        return ""

    if len(source_text) <= limit_chars:
        return source_text

    output_limit = max(200, min(int(limit_chars), int(DEFAULT_SUMMARY_OUTPUT_CHAR_LIMIT)))

    payload = {
        "instruction": (
            "You are a careful summarization assistant.\n"
            "Summarize the document in Korean using only explicitly stated facts.\n"
            "Write the final summary in Korean, even if the source text is in another language.\n"
            "Avoid certainty, fear language, and investment advice.\n"
            "Output only summary text."
        ),
        "input": source_text,
        "maxChars": int(limit_chars),
    }
    # Deprecated for callers that only want summary string: delegate to new helper
    summary, meta = llm_summarize_with_meta(text, limit_chars=limit_chars)
    return summary


def llm_summarize_with_meta(text: str, limit_chars: int = DEFAULT_SUMMARY_CHAR_LIMIT) -> tuple[str, dict]:
    """Return (summary, meta) where meta contains status ('ok'|'fallback') and optional 'error'."""
    source_text = (text or "").strip()
    if not source_text:
        return "", {"status": "empty"}

    if len(source_text) <= limit_chars:
        return source_text, {"status": "short"}

    payload = {
        "instruction": (
            "You are a careful summarization assistant.\n"
            "Summarize the document in Korean using only explicitly stated facts.\n"
            "Write the final summary in Korean, even if the source text is in another language.\n"
            "Avoid certainty, fear language, and investment advice.\n"
            "Output only summary text."
        ),
        "input": source_text,
        "maxChars": int(limit_chars),
    }

    output_limit = max(200, min(int(limit_chars), int(DEFAULT_SUMMARY_OUTPUT_CHAR_LIMIT)))

    try:
        result = _call_selected_llm(payload)
        summary = _extract_summary_text(result)
        if summary:
            return summary[:output_limit].rstrip(), {"status": "ok"}
        # fall through to fallback
    except Exception as e:
        err = str(e)
        # continue to fallback but include error in meta
        fallback = _fallback_summary_text(source_text, output_limit=output_limit)
        return fallback, {"status": "fallback", "error": err}

    # No exception but empty result
    fallback = _fallback_summary_text(source_text, output_limit=output_limit)
    return fallback, {"status": "fallback", "error": "empty_response"}


def generate_article_insight(article: dict[str, Any]) -> dict[str, Any]:
    """
    공급자 중립 공개 함수.
    정책 기사 기반 JSON 인사이트를 생성한다.
    """
    article_payload = article or {}
    body_text = str(article_payload.get("body") or article_payload.get("bodySummary") or "").strip()
    if not body_text:
        return {
            "summary": "",
            "keywords": [],
            "assetImpacts": [],
            "tone": "neutral",
        }

    prompt = {
        "instruction": (
            "Return a single valid JSON object only.\n"
            "No markdown code fence.\n"
            "Write summary, reason, and tone fields in Korean.\n"
            "Avoid certainty, fear language, and investment advice.\n"
            "Use analytical tone such as '분석됩니다', '주목됩니다'.\n"
            "Schema: {summary:string, keywords:string[], assetImpacts:[{asset:string,direction:string,confidence:number,reason:string}], tone:string}"
        ),
        "article": article_payload,
    }
    result = _call_selected_llm(prompt)
    normalized = result if isinstance(result, dict) else {}
    return {
        "summary": str(normalized.get("summary") or "").strip(),
        "keywords": list(normalized.get("keywords") or []),
        "assetImpacts": list(normalized.get("assetImpacts") or []),
        "tone": str(normalized.get("tone") or "neutral").strip() or "neutral",
    }


def summarize_to_under_limit(text: str, limit_chars: int = DEFAULT_SUMMARY_CHAR_LIMIT) -> str:
    """
    하위 호환 래퍼.
    기존 호출부 영향 없이 새 공개 함수로 위임한다.
    """
    return llm_summarize(text, limit_chars=limit_chars)


def _call_selected_llm(payload: dict[str, Any]) -> dict[str, Any] | str:
    provider = os.getenv("LLM_PROVIDER", "gemini").strip().lower()
    retry_count = max(0, DEFAULT_RETRY_COUNT)

    last_error: Exception | None = None
    for attempt in range(retry_count + 1):
        try:
            if provider == "anthropic":
                return _call_claude_api(payload)
            return _call_gemini_api(payload)
        except Exception as error:  # pragma: no cover - network path
            last_error = error
            if attempt < retry_count:
                time.sleep(min(2.0, 0.4 * (attempt + 1)))
                continue
            raise RuntimeError(f"LLM provider call failed: {error}") from error

    if last_error is not None:
        raise RuntimeError(f"LLM provider call failed: {last_error}")
    raise RuntimeError("LLM provider call failed")


def _call_gemini_api(payload: dict[str, Any]) -> dict[str, Any] | str:
    api_key = os.getenv("GEMINI_API_KEY", "").strip()
    model = os.getenv("GEMINI_MODEL", "gemini-2.0-flash").strip()
    if not api_key:
        raise RuntimeError("GEMINI_API_KEY is required")

    body = {
        "systemInstruction": {"parts": [{"text": str(payload.get("instruction") or "").strip()}]},
        "contents": [{"role": "user", "parts": [{"text": json.dumps(payload, ensure_ascii=False)}]}],
        "generationConfig": {
            "temperature": 0.2,
            "responseMimeType": "application/json",
        },
    }
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={api_key}"
    try:
        response = _post_json(url, {"Content-Type": "application/json"}, body)
    except Exception as error:
        error_text = str(error)
        fallback_models = ["gemini-2.0-flash", "gemini-1.5-flash-002", "gemini-1.5-pro"]
        if model not in fallback_models:
            fallback_models.insert(0, model)

        if "404" in error_text or "NOT_FOUND" in error_text or "not found" in error_text.lower():
            last_error = error
            for fallback_model in fallback_models:
                if fallback_model == model:
                    continue
                fallback_url = f"https://generativelanguage.googleapis.com/v1beta/models/{fallback_model}:generateContent?key={api_key}"
                try:
                    response = _post_json(fallback_url, {"Content-Type": "application/json"}, body)
                    model = fallback_model
                    break
                except Exception as fallback_error:
                    last_error = fallback_error
                    continue
            else:
                raise RuntimeError(f"LLM provider call failed: {last_error}") from last_error
        else:
            raise RuntimeError(f"LLM provider call failed: {error}") from error

    candidates = response.get("candidates") or []
    if not candidates:
        raise RuntimeError("Gemini returned no candidates")
    parts = (candidates[0].get("content") or {}).get("parts") or []
    text = "".join(str(part.get("text", "")) for part in parts if isinstance(part, dict)).strip()
    return _parse_json_or_text(text)


def _call_claude_api(payload: dict[str, Any]) -> dict[str, Any] | str:
    api_key = os.getenv("ANTHROPIC_API_KEY", "").strip()
    model = os.getenv("ANTHROPIC_MODEL", "claude-3.5-haiku-20241022").strip()
    if not api_key:
        raise RuntimeError("ANTHROPIC_API_KEY is required")

    body = {
        "model": model,
        "max_tokens": 1024,
        "temperature": 0.2,
        "system": str(payload.get("instruction") or "").strip(),
        "messages": [{"role": "user", "content": json.dumps(payload, ensure_ascii=False)}],
    }
    response = _post_json(
        "https://api.anthropic.com/v1/messages",
        {
            "Content-Type": "application/json",
            "x-api-key": api_key,
            "anthropic-version": "2023-06-01",
        },
        body,
    )

    content = response.get("content") or []
    text = "".join(str(part.get("text", "")) for part in content if isinstance(part, dict)).strip()
    return _parse_json_or_text(text)


def _post_json(url: str, headers: dict[str, str], body: dict[str, Any]) -> dict[str, Any]:
    request_body = json.dumps(body, ensure_ascii=False).encode("utf-8")
    request_obj = urllib_request.Request(url, data=request_body, headers=headers, method="POST")
    try:
        with urllib_request.urlopen(request_obj, timeout=DEFAULT_TIMEOUT_SEC) as response:
            response_text = response.read().decode("utf-8", errors="replace")
    except urllib_error.HTTPError as error:  # pragma: no cover - network path
        detail = error.read().decode("utf-8", errors="replace") if error.fp else str(error)
        raise RuntimeError(f"HTTP {error.code}: {detail}") from error
    except Exception as error:  # pragma: no cover - network path
        raise RuntimeError(f"HTTP request failed: {error}") from error

    try:
        return json.loads(response_text)
    except Exception as error:
        raise RuntimeError("LLM response JSON parsing failed") from error


def _parse_json_or_text(text: str) -> dict[str, Any] | str:
    cleaned = (text or "").strip()
    if cleaned.startswith("```"):
        cleaned = cleaned.replace("```json", "").replace("```", "").strip()
    if not cleaned:
        return ""
    try:
        return json.loads(cleaned)
    except Exception:
        return cleaned


def _extract_summary_text(result: dict[str, Any] | str) -> str:
    if isinstance(result, str):
        return result.strip()
    if isinstance(result, dict):
        for key in ("summary", "text", "result"):
            value = result.get(key)
            if isinstance(value, str) and value.strip():
                return value.strip()
    return ""


def _fallback_summary_text(source_text: str, output_limit: int) -> str:
    """LLM 실패 시 원문 전체 대신 짧은 추출 요약을 반환한다."""
    compact = " ".join((source_text or "").split())
    if not compact:
        return ""

    sentences = compact.replace("\n", " ").split(". ")
    picked: list[str] = []
    current_len = 0
    for sentence in sentences:
        candidate = sentence.strip()
        if not candidate:
            continue
        if not candidate.endswith("."):
            candidate += "."
        next_len = current_len + len(candidate) + (1 if picked else 0)
        if next_len > output_limit:
            break
        picked.append(candidate)
        current_len = next_len
        if len(picked) >= 4:
            break

    if picked:
        return " ".join(picked).strip()

    return compact[:output_limit].rstrip()
