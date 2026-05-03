package com.datasage.dashboard.service;

import com.datasage.dashboard.dto.ChartResponse;
import com.datasage.dashboard.dto.HistoryResponse;
import com.datasage.dashboard.dto.StructuredQuery;
import com.datasage.dashboard.exception.AppException;
import com.datasage.dashboard.model.QueryHistory;
import com.datasage.dashboard.model.UploadedFile;
import com.datasage.dashboard.repository.QueryHistoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class QueryService {
    private final CsvService csvService;
    private final OpenAiService openAiService;
    private final QueryHistoryRepository queryHistoryRepository;
    private final ObjectMapper objectMapper;

    public QueryService(CsvService csvService,
                        OpenAiService openAiService,
                        QueryHistoryRepository queryHistoryRepository,
                        ObjectMapper objectMapper) {
        this.csvService = csvService;
        this.openAiService = openAiService;
        this.queryHistoryRepository = queryHistoryRepository;
        this.objectMapper = objectMapper;
    }

    public ChartResponse answerQuestion(String question) {
        UploadedFile uploadedFile = csvService.getLatestUploadedFile();
        List<String> headers = csvService.readHeaders(uploadedFile);
        List<Map<String, String>> rows = csvService.readRows(uploadedFile);
        StructuredQuery structuredQuery = openAiService.createStructuredQuery(question, headers);
        repairStructuredQuery(question, structuredQuery, headers, rows);
        validateStructuredQuery(structuredQuery, headers);

        ChartResponse response = processRows(rows, structuredQuery);
        saveHistory(question, structuredQuery, response);
        return response;
    }

    public List<HistoryResponse> getHistory() {
        return queryHistoryRepository.findTop20ByOrderByCreatedAtDesc()
                .stream()
                .map(history -> new HistoryResponse(
                        history.getId(),
                        history.getQuestion(),
                        history.getStructuredQueryJson(),
                        history.getResponseJson(),
                        history.getCreatedAt()
                ))
                .toList();
    }

    private ChartResponse processRows(List<Map<String, String>> rows, StructuredQuery query) {
        Map<String, List<Double>> groupedValues = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            String label = valueOrUnknown(row.get(query.getGroupBy()));
            double value = "count".equalsIgnoreCase(query.getAggregate()) ? 1 : parseNumber(row.get(query.getColumn()));
            groupedValues.computeIfAbsent(label, ignored -> new ArrayList<>()).add(value);
        }

        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (Map.Entry<String, List<Double>> entry : groupedValues.entrySet()) {
            labels.add(entry.getKey());
            values.add(applyAggregate(entry.getValue(), query.getAggregate()));
        }

        String summary = buildSummary(labels, values, query);
        return new ChartResponse(query.getChartType(), labels, values, summary);
    }

    private void validateStructuredQuery(StructuredQuery query, List<String> headers) {
        query.setGroupBy(matchHeader(query.getGroupBy(), headers));
        query.setColumn(matchHeader(query.getColumn(), headers));

        if (query.getGroupBy() == null || !headers.contains(query.getGroupBy())) {
            throw new AppException("AI selected an unknown groupBy column. Try mentioning one of: " + headers);
        }
        if (!"count".equalsIgnoreCase(query.getAggregate()) && (query.getColumn() == null || !headers.contains(query.getColumn()))) {
            throw new AppException("AI selected an unknown value column. Try mentioning one of: " + headers);
        }
        if (query.getChartType() == null || query.getChartType().isBlank()) {
            query.setChartType("bar");
        }
    }

    private void repairStructuredQuery(String question, StructuredQuery query, List<String> headers, List<Map<String, String>> rows) {
        String lowerQuestion = question.toLowerCase();

        String inferredGroupBy = inferGroupByFromQuestion(lowerQuestion, headers);
        if (inferredGroupBy != null) {
            query.setGroupBy(inferredGroupBy);
        } else {
            query.setGroupBy(matchHeader(query.getGroupBy(), headers));
        }

        if (query.getGroupBy() == null || !headers.contains(query.getGroupBy())) {
            query.setGroupBy(headers.isEmpty() ? null : headers.get(0));
        }

        if (query.getAggregate() == null || query.getAggregate().isBlank()) {
            query.setAggregate("sum");
        }

        if ("count".equalsIgnoreCase(query.getAggregate())) {
            return;
        }

        query.setColumn(matchHeader(query.getColumn(), headers));

        boolean invalidMetricChoice = query.getColumn() == null
                || !headers.contains(query.getColumn())
                || query.getColumn().equals(query.getGroupBy())
                || !isMostlyNumericColumn(rows, query.getColumn())
                || allNumericValuesAreZero(rows, query.getColumn());

        if (invalidMetricChoice) {
            String repairedColumn = chooseBestMetricColumn(lowerQuestion, headers, rows, query.getGroupBy());
            if (repairedColumn != null) {
                query.setColumn(repairedColumn);
            }
        }
    }

    private String inferGroupByFromQuestion(String lowerQuestion, List<String> headers) {
        return headers.stream()
                .filter(header -> lowerQuestion.contains(normalizeHeader(header)))
                .findFirst()
                .orElseGet(() -> {
                    int byIndex = lowerQuestion.indexOf(" by ");
                    if (byIndex < 0) {
                        return null;
                    }
                    String trailingPhrase = lowerQuestion.substring(byIndex + 4);
                    return headers.stream()
                            .filter(header -> trailingPhrase.contains(normalizeHeader(header)))
                            .findFirst()
                            .orElse(null);
                });
    }

    private String chooseBestMetricColumn(String lowerQuestion, List<String> headers, List<Map<String, String>> rows, String groupBy) {
        return headers.stream()
                .filter(header -> !header.equals(groupBy))
                .filter(header -> isMostlyNumericColumn(rows, header))
                .max(Comparator.comparingInt(header -> scoreMetricColumn(lowerQuestion, header)))
                .orElseGet(() -> headers.stream()
                        .filter(header -> !header.equals(groupBy))
                        .filter(header -> isMostlyNumericColumn(rows, header))
                        .findFirst()
                        .orElse(null));
    }

    private int scoreMetricColumn(String lowerQuestion, String header) {
        String normalizedHeader = normalizeHeader(header);
        int score = 0;

        if (lowerQuestion.contains("sale") || lowerQuestion.contains("sales")) {
            if (normalizedHeader.contains("revenue")) score += 10;
            if (normalizedHeader.contains("sales")) score += 9;
            if (normalizedHeader.contains("amount")) score += 8;
            if (normalizedHeader.contains("units")) score += 7;
            if (normalizedHeader.contains("quantity")) score += 7;
        }
        if (lowerQuestion.contains("revenue")) {
            if (normalizedHeader.contains("revenue")) score += 12;
        }
        if (lowerQuestion.contains("profit")) {
            if (normalizedHeader.contains("profit")) score += 12;
        }
        if (lowerQuestion.contains("rating")) {
            if (normalizedHeader.contains("rating")) score += 12;
        }
        if (lowerQuestion.contains("unit") || lowerQuestion.contains("quantity")) {
            if (normalizedHeader.contains("units")) score += 10;
            if (normalizedHeader.contains("quantity")) score += 10;
            if (normalizedHeader.contains("qty")) score += 10;
        }
        if (lowerQuestion.contains("price") || lowerQuestion.contains("cost")) {
            if (normalizedHeader.contains("price")) score += 10;
            if (normalizedHeader.contains("cost")) score += 10;
        }
        if (normalizedHeader.matches(".*(revenue|sales|amount|profit|units|quantity|qty|price|cost|value|total|rating|score).*")) {
            score += 2;
        }

        return score;
    }

    private boolean isMostlyNumericColumn(List<Map<String, String>> rows, String column) {
        int nonBlankValues = 0;
        int numericValues = 0;

        for (Map<String, String> row : rows) {
            String rawValue = row.get(column);
            if (rawValue == null || rawValue.isBlank()) {
                continue;
            }
            nonBlankValues++;
            if (isNumeric(rawValue)) {
                numericValues++;
            }
        }

        return nonBlankValues > 0 && numericValues >= Math.max(1, nonBlankValues / 2);
    }

    private boolean allNumericValuesAreZero(List<Map<String, String>> rows, String column) {
        boolean foundNumeric = false;
        for (Map<String, String> row : rows) {
            String rawValue = row.get(column);
            if (!isNumeric(rawValue)) {
                continue;
            }
            foundNumeric = true;
            if (parseNumber(rawValue) != 0) {
                return false;
            }
        }
        return foundNumeric;
    }

    private boolean isNumeric(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return false;
        }
        try {
            Double.parseDouble(rawValue.replaceAll("[,$%]", "").trim());
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private String normalizeHeader(String header) {
        return header.toLowerCase().replace("_", " ").trim();
    }

    private String matchHeader(String selectedHeader, List<String> headers) {
        if (selectedHeader == null) {
            return null;
        }
        return headers.stream()
                .filter(header -> header.equalsIgnoreCase(selectedHeader.trim()))
                .findFirst()
                .orElse(selectedHeader);
    }

    private double applyAggregate(List<Double> values, String aggregate) {
        DoubleSummaryStatistics statistics = values.stream().collect(Collectors.summarizingDouble(Double::doubleValue));
        return switch (aggregate == null ? "sum" : aggregate.toLowerCase()) {
            case "average", "avg" -> statistics.getAverage();
            case "count" -> statistics.getCount();
            case "min" -> statistics.getMin();
            case "max" -> statistics.getMax();
            default -> statistics.getSum();
        };
    }

    private double parseNumber(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return 0;
        }
        try {
            return Double.parseDouble(rawValue.replaceAll("[,$%]", "").trim());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "Unknown" : value;
    }

    private String buildSummary(List<String> labels, List<Double> values, StructuredQuery query) {
        if (labels.isEmpty()) {
            return "No matching data was found for this question.";
        }

        int maxIndex = 0;
        for (int i = 1; i < values.size(); i++) {
            if (values.get(i) > values.get(maxIndex)) {
                maxIndex = i;
            }
        }

        return "The " + query.getAggregate() + " of " + query.getColumn()
                + " grouped by " + query.getGroupBy()
                + " is highest for " + labels.get(maxIndex)
                + " at " + String.format("%.2f", values.get(maxIndex)) + ".";
    }

    private void saveHistory(String question, StructuredQuery structuredQuery, ChartResponse response) {
        try {
            QueryHistory history = new QueryHistory();
            history.setQuestion(question);
            history.setStructuredQueryJson(objectMapper.writeValueAsString(structuredQuery));
            history.setResponseJson(objectMapper.writeValueAsString(response));
            history.setCreatedAt(LocalDateTime.now());
            queryHistoryRepository.save(history);
        } catch (Exception exception) {
            throw new AppException("Could not save query history.", exception);
        }
    }
}
