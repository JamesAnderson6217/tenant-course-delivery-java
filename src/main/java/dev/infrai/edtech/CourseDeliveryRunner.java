package dev.infrai.edtech;

import java.time.Clock;
import java.time.Instant;

public final class CourseDeliveryRunner {
    private CourseDeliveryRunner() {}

    public static void main(String[] args) {
        StorageConfig config = StorageConfig.load();
        CourseDeliveryService service = new CourseDeliveryService(new InfraiStorageClient(config), Clock.systemUTC());
        CourseDeliveryService.DeliveryRequest request = new CourseDeliveryService.DeliveryRequest(
                "north-campus", "risk-101", "learner-42", Instant.now().plusSeconds(86_400), 10_000_000);
        try {
            InfraiStorageClient.PresignedUpload upload = service.openDelivery(request);
            System.out.println(Json.write(java.util.Map.of(
                    "bucket", upload.bucket(), "key", upload.key(),
                    "method", upload.method(), "uploadUrl", upload.url())));
        } catch (InfraiStorageClient.InfraiException exception) {
            System.err.println(Json.write(java.util.Map.of(
                    "status", exception.status(), "code", exception.code(), "message", exception.getMessage())));
            System.exit(2);
        }
    }
}
