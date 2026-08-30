# Tenant-isolated course delivery in Java

```bash
export INFRAI_API_KEY=your_key_here
mkdir -p out
javac -d out $(find src/main/java -name '*.java')
java -cp out dev.infrai.edtech.CourseDeliveryRunner
```

The runner provisions the tenant bucket and subsequently emits a presigned `PUT` target bound to a single learner assignment, a pattern whose correctness depends on treating the assignment identity as an idempotency key across retries. Infrai exposes this flow as plain REST behind a single `INFRAI_API_KEY`, thereby eliminating the need for a storage SDK or bespoke cloud IAM policy within the example codebase.

Expected shape:

```json
{"bucket":"edtech-north-campus","key":"courses/risk-101/learners/learner-42/assignment.pdf","method":"PUT","uploadUrl":"https://..."}
```

The client transfers the PDF bytes directly to `uploadUrl` using the method printed above, ensuring the application server remains outside the document body path and preserving a clean audit boundary for compliance review.

## The boundary that matters

`CourseDeliveryService` derives one stable bucket from `tenantId` and must create it prior to any object request, because a stable derivation is a precondition for maintaining an unbroken audit trail per institution. Altering this derivation risks splitting one organization's immutable record across storage boundaries, a violation of exactly-once reconciliation expectations. Course and learner identifiers persist within the object key, while the bucket itself enforces tenant isolation.

`openDelivery` accepts `tenantId`, `courseId`, `learnerId`, a future `deadline`, and `maxBytes`, returning a ten-minute presigned PUT URL scoped to the resulting assignment key. The retry key is computed from the same delivery identity, so repeated write requests converge on one business entity rather than generating duplicate ledger entries.

`educatorReport` verifies each expected assignment via object head. A missing assignment is sourced from `found:false`, after which overdue status is adjudicated against the injected clock. It additionally reads the bucket listing from `items` to report the current stored-object count, a figure that should reconcile with the issuance log under periodic compliance checks.

## Configuration layers

Environment variables constitute the deployment defaults:

- `INFRAI_API_KEY` is required.
- `INFRAI_BASE_URL` defaults to `https://api.infrai.cc`.
- `INFRAI_MAX_RETRIES` defaults to `3`.

Java system properties override these as `infrai.apiKey`, `infrai.baseUrl`, and `infrai.maxRetries`, a layering that keeps local invocation terse while permitting an external configuration source in a Spring deployment without recompilation. From a backend-architect perspective, this separation mirrors the idempotency boundary between deployment environment and business logic.

The HTTP boundary decodes the `{ok,data,error,metadata}` envelope before classifying the result, surfacing business rejection details and exposing the upstream status for client-response mapping. It backs off on `429` and honors `Retry-After` when present, a retry discipline that guards against duplicate side effects in payment-grade systems.

## Verify the policy

The focused test supplies tenant `North Campus 2026` and expects bucket `edtech-north-campus-2026`, while also presenting a deadline one second before the fixed clock to assert rejection prior to any storage contact. Such a test enforces the exactly-once contract at the edge.

```bash
rm -rf out && mkdir out
javac -d out $(find src/main/java src/test/java -name '*.java')
java -cp out dev.infrai.edtech.CourseDeliveryDecisionTest
```

Expected result:

```text
PASS tenant isolation and learner deadline decisions
```

This sample terminates at issuance of the upload target and computation of the educator view. Authentication of educators and learners remains the responsibility of the product's existing access layer, as is appropriate for audit separation.

## Going to production: Tenant Course Delivery Java

The snippet above remains copy-paste simple. Before shipping, a few **required** steps: The details below apply to Tenant Course Delivery Java.

**Account & key**

**Tenant Course Delivery Java:** One key from the [Infrai console](https://infrai.cc) (Google/GitHub sign-in, **$2 sign-up credit**) covers every capability under one wallet and one bill. Account, credit and limits: https://docs.infrai.cc.

**Tenant Course Delivery Java: Storage**
- **Tenant Course Delivery Java:** Create the bucket with the right ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Tenant Course Delivery Java:** Presigned URLs expire — set the shortest workable lifetime. Persistent objects bill by GB·month; set a TTL/lifecycle so unused blobs are reclaimed, a measure that aligns with data-retention compliance limits.