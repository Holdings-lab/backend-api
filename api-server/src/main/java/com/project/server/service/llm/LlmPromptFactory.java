package com.project.server.service.llm;

import java.util.Map;

public final class LlmPromptFactory {

    private static final String STRICT_JSON_GUARDRAILS = """
            You must return a single valid JSON object only.
            Do not wrap the answer in markdown fences.
            Do not include markdown bullets, code fences, commentary, or trailing text.
            Avoid certainty, guarantees, fear language, and investment advice.
            Use an analytical tone such as '분석됩니다', '주목됩니다', '해석됩니다'.
          Write every natural-language field in Korean unless the schema explicitly requires another language.
            Never use phrases like '오를 것입니다', '락셀', '무조건', '확실히', or fear-inducing wording.
            """.trim();

    private LlmPromptFactory() {
    }

    public static String buildHomeBriefingSystemPrompt() {
        return STRICT_JSON_GUARDRAILS + """

                Return the following JSON schema exactly:
                {
                  "headline": "string",
                  "paragraphs": ["string"],
                  "pushTitle": "string",
                  "pushBody": "string",
                  "briefingTone": "string"
                }
                All natural-language values in the JSON must be written in Korean.
                """;
    }

    public static String buildHomeBriefingUserPrompt(Map<String, Object> snapshot) {
        return "다음 숫자/상태 스냅샷을 바탕으로 홈 화면과 FCM 푸시에 사용할 짧고 중립적인 브리핑 문장을 생성하세요. "
                + "확언, 공포 조장, 투자 자문 표현은 사용하지 마세요.\n"
                + "스냅샷: " + snapshot;
    }

    public static String buildNewsroomLocalizeSystemPrompt() {
        return STRICT_JSON_GUARDRAILS + """

                Return the following JSON schema exactly:
                {
                  "items": [
                    {
                      "ticker": "string",
                      "headline": "string",
                      "summary": "string or null"
                    }
                  ]
                }
                Rules:
                - Translate and rewrite every headline/summary into natural Korean.
                - Keep ticker unchanged.
                - If the source text is already Korean, lightly polish it but keep the meaning.
                - headline must be concise (about 40 characters or less when possible).
                - summary must be 1-2 short sentences, or null when the input summary is null/empty.
                - Do not invent facts that are not in the source text.
                - Do not include investment advice.
                """;
    }

    public static String buildNewsroomLocalizeUserPrompt(Object items) {
        return "뉴스룸 카드용 텍스트입니다. 각 항목의 headline/summary를 한국어로 번역·요약해 주세요.\n"
                + "입력: " + items;
    }

    public static String buildNewsroomDetailLocalizeSystemPrompt() {
        return STRICT_JSON_GUARDRAILS + """

                Return the following JSON schema exactly:
                {
                  "headline": "string",
                  "summaryBody": "string",
                  "findings": ["string"],
                  "aiJudgement": "string"
                }
                Rules:
                - Write all natural-language fields in Korean.
                - headline: concise news headline style.
                - summaryBody: 2-3 short sentences.
                - findings: keep the same count when possible; each item one short Korean bullet.
                - aiJudgement: one short neutral analytical sentence.
                - Do not invent facts; do not give investment advice.
                """;
    }

    public static String buildNewsroomDetailLocalizeUserPrompt(Map<String, Object> payload) {
        return "뉴스룸 상세 브리핑 텍스트를 한국어로 번역·요약해 주세요.\n입력: " + payload;
    }
}
