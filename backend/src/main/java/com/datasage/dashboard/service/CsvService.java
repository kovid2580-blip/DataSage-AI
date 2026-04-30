package com.datasage.dashboard.service;

import com.datasage.dashboard.dto.DataPreviewResponse;
import com.datasage.dashboard.exception.AppException;
import com.datasage.dashboard.model.DatasetRow;
import com.datasage.dashboard.model.UploadedFile;
import com.datasage.dashboard.repository.DatasetRowRepository;
import com.datasage.dashboard.repository.UploadedFileRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CsvService {
    private final UploadedFileRepository uploadedFileRepository;
    private final DatasetRowRepository datasetRowRepository;
    private final ObjectMapper objectMapper;

    public CsvService(UploadedFileRepository uploadedFileRepository,
                      DatasetRowRepository datasetRowRepository,
                      ObjectMapper objectMapper) {
        this.uploadedFileRepository = uploadedFileRepository;
        this.datasetRowRepository = datasetRowRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DataPreviewResponse uploadCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException("Please upload a CSV file.");
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            List<String> headers = new ArrayList<>(parser.getHeaderMap().keySet());
            if (headers.isEmpty()) {
                throw new AppException("CSV file must include a header row.");
            }

            UploadedFile uploadedFile = new UploadedFile();
            uploadedFile.setFileName(file.getOriginalFilename());
            uploadedFile.setHeadersJson(objectMapper.writeValueAsString(headers));
            uploadedFile.setUploadedAt(LocalDateTime.now());

            List<Map<String, String>> previewRows = new ArrayList<>();
            for (CSVRecord record : parser) {
                Map<String, String> rowMap = new LinkedHashMap<>();
                for (String header : headers) {
                    rowMap.put(header, record.isMapped(header) ? record.get(header) : "");
                }

                DatasetRow row = new DatasetRow();
                row.setUploadedFile(uploadedFile);
                row.setRowDataJson(objectMapper.writeValueAsString(rowMap));
                uploadedFile.getRows().add(row);

                if (previewRows.size() < 20) {
                    previewRows.add(rowMap);
                }
            }

            if (uploadedFile.getRows().isEmpty()) {
                throw new AppException("CSV file does not contain data rows.");
            }

            UploadedFile savedFile = uploadedFileRepository.save(uploadedFile);
            return new DataPreviewResponse(
                    savedFile.getId(),
                    savedFile.getFileName(),
                    headers,
                    previewRows,
                    savedFile.getRows().size(),
                    savedFile.getUploadedAt()
            );
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AppException("Could not parse CSV file.", exception);
        }
    }

    public DataPreviewResponse getLatestDataPreview() {
        UploadedFile latestFile = getLatestUploadedFile();
        List<String> headers = readHeaders(latestFile);
        List<Map<String, String>> rows = readRows(latestFile);
        List<Map<String, String>> previewRows = rows.stream().limit(20).toList();

        return new DataPreviewResponse(
                latestFile.getId(),
                latestFile.getFileName(),
                headers,
                previewRows,
                rows.size(),
                latestFile.getUploadedAt()
        );
    }

    public UploadedFile getLatestUploadedFile() {
        return uploadedFileRepository.findTopByOrderByUploadedAtDesc()
                .orElseThrow(() -> new AppException("Upload a CSV file before asking questions."));
    }

    public List<String> readHeaders(UploadedFile uploadedFile) {
        try {
            return objectMapper.readValue(uploadedFile.getHeadersJson(), new TypeReference<>() {
            });
        } catch (Exception exception) {
            throw new AppException("Could not read dataset headers.", exception);
        }
    }

    public List<Map<String, String>> readRows(UploadedFile uploadedFile) {
        try {
            List<DatasetRow> rows = datasetRowRepository.findByUploadedFileOrderByIdAsc(uploadedFile);
            List<Map<String, String>> parsedRows = new ArrayList<>();
            for (DatasetRow row : rows) {
                parsedRows.add(objectMapper.readValue(row.getRowDataJson(), new TypeReference<>() {
                }));
            }
            return parsedRows;
        } catch (Exception exception) {
            throw new AppException("Could not read dataset rows.", exception);
        }
    }
}
