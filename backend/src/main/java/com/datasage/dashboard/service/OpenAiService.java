package com.datasage.dashboard.service;

import com.datasage.dashboard.dto.StructuredQuery;
import com.datasage.dashboard.exception.AppException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OpenAiService {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.api.url}")
    private String apiUrl;

    @Value("${openai.model}")
    private String model;

    public OpenAiService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public StructuredQuery createStructuredQuery(String question, List<String> headers) {
        if (apiKey == null || apiKey.isBlank() || "YOUR_OPENAI_API_KEY".equals(apiKey)) {
            return fallbackStructuredQuery(question, headers);
        }

        try {
            String prompt = """
                    Convert the user question into JSON for a simple CSV analyst app.
                    Available columns: %s
                    Return only valid JSON with these keys:
                    operation: groupBy
                    groupBy: one available column
                    aggregate: sum, average, count, min, or max
                    column: one numeric column, or any column for count
                    chartType: bar, line, pie, or area
                    User question: %s
                    """.formatted(headers, question);

            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "temperature", 0,
                    "messages", List.of(
                            Map.of("role", "system", "content", "You return only compact JSON. No markdown."),
                            Map.of("role", "user", "content", prompt)
                    )
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AppException("OpenAI request failed with status " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").get(0).path("message").path("content").asText();
            return objectMapper.readValue(stripCodeFence(content), StructuredQuery.class);
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AppException("Could not create a structured query from the question.", exception);
        }
    }

    private StructuredQuery fallbackStructuredQuery(String question, List<String> headers) {
        String lowerQuestion = question.toLowerCase();
        StructuredQuery query = new StructuredQuery();
        query.setChartType(lowerQuestion.contains("pie") ? "pie" : lowerQuestion.contains("line") ? "line" : "bar");
        query.setAggregate(lowerQuestion.contains("average") || lowerQuestion.contains("avg") ? "average" :
                lowerQuestion.contains("count") ? "count" :
                        lowerQuestion.contains("min") ? "min" :
                                lowerQuestion.contains("max") ? "max" : "sum");
        query.setGroupBy(findMentionedHeader(lowerQuestion, headers, headers.isEmpty() ? null : headers.get(0)));
        query.setColumn(findMentionedHeader(lowerQuestion, headers, findLikelyNumericHeader(headers, query.getGroupBy())));
        return query;
    }

    private String findMentionedHeader(String lowerQuestion, List<String> headers, String defaultHeader) {
        for (String header : headers) {
            if (lowerQuestion.contains(header.toLowerCase())) {
                return header;
            }
        }
        return defaultHeader;
    }

    private String findLikelyNumericHeader(List<String> headers, String groupBy) {
        return headers.stream()
                .filter(header -> !header.equals(groupBy))
                .filter(header -> header.toLowerCase().matches(".*(amount|sales|price|total|revenue|profit|cost|value|quantity|qty|count).*"))
                .findFirst()
                .orElse(headers.stream().filter(header -> !header.equals(groupBy)).findFirst().orElse(groupBy));
    }

    private String stripCodeFence(String content) {
        return content.replace("```json", "").replace("```", "").trim();
    }
}
