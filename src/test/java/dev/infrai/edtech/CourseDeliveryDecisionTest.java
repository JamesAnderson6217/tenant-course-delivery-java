package dev.infrai.edtech;

import java.time.Instant;

public final class CourseDeliveryDecisionTest {
    private CourseDeliveryDecisionTest() {}

    public static void main(String[] args) {
        String bucket = CourseDeliveryService.tenantBucket("North Campus 2026");
        if (!"edtech-north-campus-2026".equals(bucket)) {
            throw new AssertionError("Tenant bucket decision changed: " + bucket);
        }

        Instant now = Instant.parse("2026-08-20T08:00:00Z");
        CourseDeliveryService.DeliveryRequest request = new CourseDeliveryService.DeliveryRequest(
                "north-campus", "risk-101", "learner-42", now.minusSeconds(1), 1_000_000);
        boolean rejected = false;
        try {
            CourseDeliveryService.validateDeadline(request.deadline(), now);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        if (!rejected) throw new AssertionError("Past deadline was accepted");
        System.out.println("PASS tenant isolation and learner deadline decisions");
    }
}
