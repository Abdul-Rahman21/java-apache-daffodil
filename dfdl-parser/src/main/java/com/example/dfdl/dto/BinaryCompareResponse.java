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
    /** Remaining percent that did not match ({@code 100 - matchPercent}). */
    private int mismatchPercent;
    private String encoding;
    private FileSummary clientFile;
    private FileSummary unparseFile;
    private Map<String, CheckResult> checks = new LinkedHashMap<>();
    private Map<String, String> exactSegments = new LinkedHashMap<>();
    private List<String> matches = new ArrayList<>();
    private List<String> differences = new ArrayList<>();
    private List<SegmentDiff> segmentDiffs = new ArrayList<>();
    /**
     * Present when {@code matchPercent &lt; 100}. Explains which checks failed and why.
     */
    private MismatchDetails mismatchDetails;
    /** JSON extracted from the client binary (EBCDIC → structured fields). */
    private ExtractedBinaryJson clientJson;
    /** JSON extracted from the unparse binary (EBCDIC → structured fields). */
    private ExtractedBinaryJson unparseJson;
    /** Field-level differences between the two extracted JSONs. */
    private List<FieldMismatch> mismatchedValues = new ArrayList<>();
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

    public int getMismatchPercent() {
        return mismatchPercent;
    }

    public void setMismatchPercent(int mismatchPercent) {
        this.mismatchPercent = mismatchPercent;
    }

    public MismatchDetails getMismatchDetails() {
        return mismatchDetails;
    }

    public void setMismatchDetails(MismatchDetails mismatchDetails) {
        this.mismatchDetails = mismatchDetails;
    }

    public ExtractedBinaryJson getClientJson() {
        return clientJson;
    }

    public void setClientJson(ExtractedBinaryJson clientJson) {
        this.clientJson = clientJson;
    }

    public ExtractedBinaryJson getUnparseJson() {
        return unparseJson;
    }

    public void setUnparseJson(ExtractedBinaryJson unparseJson) {
        this.unparseJson = unparseJson;
    }

    public List<FieldMismatch> getMismatchedValues() {
        return mismatchedValues;
    }

    public void setMismatchedValues(List<FieldMismatch> mismatchedValues) {
        this.mismatchedValues = mismatchedValues;
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

    /**
     * Explains why the compare did not reach 100% match.
     */
    public static class MismatchDetails {
        private int failedCheckCount;
        private int totalCheckCount;
        private String reason;
        private List<FailedCheck> failedChecks = new ArrayList<>();
        private List<String> whyNotMatched = new ArrayList<>();
        /** Field-level JSON diffs (same list as top-level mismatchedValues when present). */
        private List<FieldMismatch> mismatchedValues = new ArrayList<>();

        public int getFailedCheckCount() {
            return failedCheckCount;
        }

        public void setFailedCheckCount(int failedCheckCount) {
            this.failedCheckCount = failedCheckCount;
        }

        public int getTotalCheckCount() {
            return totalCheckCount;
        }

        public void setTotalCheckCount(int totalCheckCount) {
            this.totalCheckCount = totalCheckCount;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }

        public List<FailedCheck> getFailedChecks() {
            return failedChecks;
        }

        public void setFailedChecks(List<FailedCheck> failedChecks) {
            this.failedChecks = failedChecks;
        }

        public List<String> getWhyNotMatched() {
            return whyNotMatched;
        }

        public void setWhyNotMatched(List<String> whyNotMatched) {
            this.whyNotMatched = whyNotMatched;
        }

        public List<FieldMismatch> getMismatchedValues() {
            return mismatchedValues;
        }

        public void setMismatchedValues(List<FieldMismatch> mismatchedValues) {
            this.mismatchedValues = mismatchedValues;
        }
    }

    public static class FailedCheck {
        private String check;
        private String detail;
        private String impact;

        public FailedCheck() {
        }

        public FailedCheck(String check, String detail, String impact) {
            this.check = check;
            this.detail = detail;
            this.impact = impact;
        }

        public String getCheck() {
            return check;
        }

        public void setCheck(String check) {
            this.check = check;
        }

        public String getDetail() {
            return detail;
        }

        public void setDetail(String detail) {
            this.detail = detail;
        }

        public String getImpact() {
            return impact;
        }

        public void setImpact(String impact) {
            this.impact = impact;
        }
    }

    /**
     * One mismatched field after both binaries were extracted to JSON.
     */
    public static class FieldMismatch {
        private String path;
        private Object clientValue;
        private Object unparseValue;
        private String category;
        private String explanation;

        public FieldMismatch() {
        }

        public FieldMismatch(
                String path,
                Object clientValue,
                Object unparseValue,
                String category,
                String explanation) {
            this.path = path;
            this.clientValue = clientValue;
            this.unparseValue = unparseValue;
            this.category = category;
            this.explanation = explanation;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public Object getClientValue() {
            return clientValue;
        }

        public void setClientValue(Object clientValue) {
            this.clientValue = clientValue;
        }

        public Object getUnparseValue() {
            return unparseValue;
        }

        public void setUnparseValue(Object unparseValue) {
            this.unparseValue = unparseValue;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getExplanation() {
            return explanation;
        }

        public void setExplanation(String explanation) {
            this.explanation = explanation;
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
