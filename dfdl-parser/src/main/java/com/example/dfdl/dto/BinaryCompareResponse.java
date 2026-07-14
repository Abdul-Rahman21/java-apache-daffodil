package com.example.dfdl.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of comparing a client-shared SMPRES binary with an unparse-generated binary.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BinaryCompareResponse {

    private boolean success;
    private String verdict;
    private String summary;
    private int matchPercent;
    private String encoding;
    private FileSummary clientFile;
    private FileSummary unparseFile;
    private Map<String, CheckResult> checks = new LinkedHashMap<>();
    private Map<String, String> exactSegments = new LinkedHashMap<>();
    private List<String> matches = new ArrayList<>();
    private List<String> differences = new ArrayList<>();
    private List<SegmentDiff> segmentDiffs = new ArrayList<>();
    private String error;

    public static BinaryCompareResponse failure(String error) {
        BinaryCompareResponse response = new BinaryCompareResponse();
        response.success = false;
        response.error = error;
        return response;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getVerdict() {
        return verdict;
    }

    public void setVerdict(String verdict) {
        this.verdict = verdict;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public int getMatchPercent() {
        return matchPercent;
    }

    public void setMatchPercent(int matchPercent) {
        this.matchPercent = matchPercent;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public FileSummary getClientFile() {
        return clientFile;
    }

    public void setClientFile(FileSummary clientFile) {
        this.clientFile = clientFile;
    }

    public FileSummary getUnparseFile() {
        return unparseFile;
    }

    public void setUnparseFile(FileSummary unparseFile) {
        this.unparseFile = unparseFile;
    }

    public Map<String, CheckResult> getChecks() {
        return checks;
    }

    public void setChecks(Map<String, CheckResult> checks) {
        this.checks = checks;
    }

    public Map<String, String> getExactSegments() {
        return exactSegments;
    }

    public void setExactSegments(Map<String, String> exactSegments) {
        this.exactSegments = exactSegments;
    }

    public List<String> getMatches() {
        return matches;
    }

    public void setMatches(List<String> matches) {
        this.matches = matches;
    }

    public List<String> getDifferences() {
        return differences;
    }

    public void setDifferences(List<String> differences) {
        this.differences = differences;
    }

    public List<SegmentDiff> getSegmentDiffs() {
        return segmentDiffs;
    }

    public void setSegmentDiffs(List<SegmentDiff> segmentDiffs) {
        this.segmentDiffs = segmentDiffs;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public static class FileSummary {
        private String fileName;
        private int sizeBytes;
        private int segmentCount;
        private String messageType;
        private String firstBytesHex;
        private boolean ebcdicIbm037;
        private Map<String, Integer> tagCounts = new LinkedHashMap<>();

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public int getSizeBytes() {
            return sizeBytes;
        }

        public void setSizeBytes(int sizeBytes) {
            this.sizeBytes = sizeBytes;
        }

        public int getSegmentCount() {
            return segmentCount;
        }

        public void setSegmentCount(int segmentCount) {
            this.segmentCount = segmentCount;
        }

        public String getMessageType() {
            return messageType;
        }

        public void setMessageType(String messageType) {
            this.messageType = messageType;
        }

        public String getFirstBytesHex() {
            return firstBytesHex;
        }

        public void setFirstBytesHex(String firstBytesHex) {
            this.firstBytesHex = firstBytesHex;
        }

        public boolean isEbcdicIbm037() {
            return ebcdicIbm037;
        }

        public void setEbcdicIbm037(boolean ebcdicIbm037) {
            this.ebcdicIbm037 = ebcdicIbm037;
        }

        public Map<String, Integer> getTagCounts() {
            return tagCounts;
        }

        public void setTagCounts(Map<String, Integer> tagCounts) {
            this.tagCounts = tagCounts;
        }
    }

    public static class CheckResult {
        private boolean passed;
        private String detail;

        public CheckResult() {
        }

        public CheckResult(boolean passed, String detail) {
            this.passed = passed;
            this.detail = detail;
        }

        public boolean isPassed() {
            return passed;
        }

        public void setPassed(boolean passed) {
            this.passed = passed;
        }

        public String getDetail() {
            return detail;
        }

        public void setDetail(String detail) {
            this.detail = detail;
        }
    }

    public static class SegmentDiff {
        private int index;
        private String tag;
        private String status;
        private String client;
        private String unparse;
        private String note;

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public String getTag() {
            return tag;
        }

        public void setTag(String tag) {
            this.tag = tag;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getClient() {
            return client;
        }

        public void setClient(String client) {
            this.client = client;
        }

        public String getUnparse() {
            return unparse;
        }

        public void setUnparse(String unparse) {
            this.unparse = unparse;
        }

        public String getNote() {
            return note;
        }

        public void setNote(String note) {
            this.note = note;
        }
    }
}
