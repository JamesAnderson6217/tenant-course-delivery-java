package dev.infrai.edtech;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CourseDeliveryService {
    private final InfraiStorageClient storage;
    private final Clock clock;

    public CourseDeliveryService(InfraiStorageClient storage, Clock clock) {
        this.storage = storage;
        this.clock = clock;
    }

    public InfraiStorageClient.PresignedUpload openDelivery(DeliveryRequest request) {
        validateDeadline(request.deadline(), clock.instant());
        String bucket = tenantBucket(request.tenantId());
        String key = "courses/" + safe(request.courseId()) + "/learners/" + safe(request.learnerId()) + "/assignment.pdf";
        storage.createBucket(bucket);
        String idempotencyKey = safe(request.tenantId()) + "-" + safe(request.courseId()) + "-" + safe(request.learnerId());
        return storage.presignCoursePut(bucket, key, "application/pdf", request.maxBytes(), idempotencyKey);
    }

    public DeliveryReport educatorReport(String tenantId, List<ExpectedDelivery> expected) {
        String bucket = tenantBucket(tenantId);
        storage.createBucket(bucket);
        int submitted = 0;
        int overdue = 0;
        for (ExpectedDelivery delivery : expected) {
            String key = "courses/" + safe(delivery.courseId()) + "/learners/" + safe(delivery.learnerId()) + "/assignment.pdf";
            boolean found = storage.objectExists(bucket, key);
            if (found) submitted++;
            if (!found && !delivery.deadline().isAfter(clock.instant())) overdue++;
        }
        List<Map<String, Object>> storedItems = storage.listObjects(bucket);
        return new DeliveryReport(tenantId, expected.size(), submitted, overdue, storedItems.size());
    }

    static String tenantBucket(String tenantId) {
        String normalized = safe(tenantId);
        if (normalized.length() < 3) throw new IllegalArgumentException("tenantId must contain at least three characters");
        return "edtech-" + normalized;
    }

    static void validateDeadline(Instant deadline, Instant now) {
        if (!deadline.isAfter(now)) {
            throw new IllegalArgumentException("Learner deadline must be in the future");
        }
    }

    private static String safe(String value) {
        String ascii = Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (ascii.isBlank()) throw new IllegalArgumentException("Identifier must contain letters or digits");
        return ascii;
    }

    public record DeliveryRequest(String tenantId, String courseId, String learnerId,
                                  Instant deadline, long maxBytes) {}
    public record ExpectedDelivery(String courseId, String learnerId, Instant deadline) {}
    public record DeliveryReport(String tenantId, int assigned, int submitted, int overdue,
                                 int storedObjects) {}
}
