package com.project.server.service.llm;

import java.util.Map;

public interface LlmApiService {
    String getProviderName();

    String getModelName();

    Map<String, Object> generateJson(String systemPrompt, String userPrompt);
}
