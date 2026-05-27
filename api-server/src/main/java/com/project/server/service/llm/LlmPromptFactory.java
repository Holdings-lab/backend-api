package com.project.server.service.llm;

import java.util.Map;

public final class LlmPromptFactory {

    private static final String STRICT_JSON_GUARDRAILS = """
            You must return a single valid JSON object only.
            Do not wrap the answer in markdown fences.
            Do not include markdown bullets, code fences, commentary, or trailing text.
            Avoid certainty, guarantees, fear language, and investment advice.
            Use an analytical tone such as '분석됩니다', '주목됩니다', '해석됩니다'.
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
                """;
    }

    public static String buildHomeBriefingUserPrompt(Map<String, Object> snapshot) {
        return "다음 숫자/상태 스냅샷을 바탕으로 홈 화면과 FCM 푸시에 사용할 짧고 중립적인 브리핑 문장을 생성하세요. "
                + "확언, 공포 조장, 투자 자문 표현은 사용하지 마세요.\n"
                + "스냅샷: " + snapshot;
    }
}
