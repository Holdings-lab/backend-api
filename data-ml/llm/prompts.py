from __future__ import annotations

from typing import Any


STRICT_JSON_GUARDRAILS = """
You must return a single valid JSON object only.
Do not wrap the answer in markdown fences.
Do not include markdown bullets, code fences, commentary, or trailing text.
Avoid certainty, guarantees, fear language, and investment advice.
Use an analytical tone such as '분석됩니다', '주목됩니다', '해석됩니다'.
Write every natural-language field in Korean unless the schema explicitly requires another language.
Never use phrases like '오를 것입니다', '락셀', '무조건', '확실히', or fear-inducing wording.
""".strip()


def build_article_insight_system_prompt() -> str:
    return (
        STRICT_JSON_GUARDRAILS
        + """

Return the following JSON schema exactly:
{
  "summary": "string",
  "keywords": ["string"],
  "assetImpacts": [
    {"asset": "string", "direction": "positive|neutral|negative", "confidence": 0.0, "reason": "string"}
  ],
  "tone": "string"
}
All natural-language values in the JSON must be written in Korean.
If the provided `title` or `body`/`bodySummary` are not in Korean, first translate them into Korean before generating the JSON. Ensure the translated text is used when forming `summary`, `keywords`, and `assetImpacts`.
"""
    ).strip()


def build_home_briefing_system_prompt() -> str:
    return (
        STRICT_JSON_GUARDRAILS
        + """

Return the following JSON schema exactly:
{
  "headline": "string",
  "paragraphs": ["string"],
  "pushTitle": "string",
  "pushBody": "string",
  "briefingTone": "string"
}
All natural-language values in the JSON must be written in Korean.
If any article `title` or `body`/`bodySummary` in the snapshot are not in Korean, translate them into Korean first and then write the briefing text in Korean.
"""
    ).strip()


def build_article_insight_user_prompt(article: dict[str, Any]) -> str:
    return (
        "기사 메타데이터와 본문을 바탕으로 정책 이벤트의 핵심 요약과 자산별 영향도를 생성하세요. "
        "기사 본문이 짧아도 추측을 과장하지 말고, 근거가 없으면 neutral로 둡니다.\n"
        f"기사 데이터: {article}"
    )


def build_home_briefing_user_prompt(snapshot: dict[str, Any]) -> str:
    return (
        "다음 숫자/상태 스냅샷을 바탕으로 홈 화면과 FCM 푸시에 사용할 짧고 중립적인 브리핑 문장을 생성하세요. "
        "확언, 공포 조장, 투자 자문 표현은 사용하지 마세요.\n"
        f"스냅샷: {snapshot}"
    )
