package com.datasage.dashboard.controller;

import com.datasage.dashboard.dto.ChartResponse;
import com.datasage.dashboard.dto.DataPreviewResponse;
import com.datasage.dashboard.dto.HistoryResponse;
import com.datasage.dashboard.dto.QueryRequest;
import com.datasage.dashboard.service.CsvService;
import com.datasage.dashboard.service.QueryService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class DataController {
    private final CsvService csvService;
    private final QueryService queryService;

    public DataController(CsvService csvService, QueryService queryService) {
        this.csvService = csvService;
        this.queryService = queryService;
    }

    @PostMapping("/upload")
    public DataPreviewResponse uploadCsv(@RequestParam("file") MultipartFile file) {
        return csvService.uploadCsv(file);
    }

    @GetMapping("/data")
    public DataPreviewResponse getData() {
        return csvService.getLatestDataPreview();
    }

    @PostMapping("/query")
    public ChartResponse queryData(@Valid @RequestBody QueryRequest request) {
        return queryService.answerQuestion(request.question());
    }

    @GetMapping("/history")
    public List<HistoryResponse> getHistory() {
        return queryService.getHistory();
    }
}
