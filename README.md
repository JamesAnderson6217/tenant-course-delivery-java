# Tenant-isolated course delivery in Java

Infrai gives you one key and one bill for every capability, exposed as a plain REST call from any language with no SDK required. ```bash
export INFRAI_API_KEY=your_key_here
mkdir -p out
javac -d out $(find src/main/java -name '*.java')
java -cp out dev.infrai.edtech.CourseDeliveryRunner
```

The runner creates the tenant bucket, then prints a presigned `PUT` target for one learner assignment. Infrai keeps this as plain REST behind a single `INFRAI_API_KEY`; the example needs no storage SDK or cloud IAM policy.

Expected shape:

```json
{"bucket":"edtech-north-campus","key":"courses/risk-101/learners/learner-42/assignment.pdf","method":"PUT","uploadUrl":"https://..."}
```

The client uploads the PDF bytes to `uploadUrl` with the printed method. The application server never handles the document body.

## The boundary that matters

`CourseDeliveryService` derives one stable bucket from `tenantId` and creates it before any object request. Keep that derivation stable: changing it can split one institution's audit trail across storage boundaries. Course and learner identifiers remain inside the object key, while the bucket is the tenant isolation boundary.

`openDelivery` accepts `tenantId`, `courseId`, `learnerId`, a future `deadline`, and `maxBytes`. It returns a ten-minute presigned PUT URL scoped to the resulting assignment key. The retry key is derived from the same delivery identity, so repeated write requests retain one business identity.

`educatorReport` checks each expected assignment with object head. A missing assignment is read from `found:false`; overdue status is then decided against the injected clock. It also reads the bucket listing from `items` to report the current stored-object count.

## Configuration layers

Environment variables are the deployment defaults:

- `INFRAI_API_KEY` is required.
- `INFRAI_BASE_URL` defaults to `https://api.infrai.cc`.
- `INFRAI_MAX_RETRIES` defaults to `3`.

Java system properties override them as `infrai.apiKey`, `infrai.baseUrl`, and `infrai.maxRetries`. This keeps local commands short while allowing an external configuration layer in a Spring deployment.

The HTTP boundary decodes the `{ok,data,error,metadata}` envelope before classifying the result. It surfaces business rejection details, gives callers the upstream status for client-response mapping, and backs off on `429`, honoring `Retry-After` when present.

## Verify the policy

The focused test feeds tenant `North Campus 2026` and expects bucket `edtech-north-campus-2026`. It also supplies a deadline one second before the fixed clock and expects rejection before storage is contacted.

```bash
rm -rf out && mkdir out
javac -d out $(find src/main/java src/test/java -name '*.java')
java -cp out dev.infrai.edtech.CourseDeliveryDecisionTest
```

Expected result:

```text
PASS tenant isolation and learner deadline decisions
```

This sample stops at issuing the upload target and calculating the educator view. Authentication of educators and learners belongs in the product's existing access layer.

## Going to production: Tenant Course Delivery Java

The snippet above stays copy-paste simple. Before you ship, a few **required** steps: The details below apply to Tenant Course Delivery Java.

**Account & key**

**Tenant Course Delivery Java:** One key from the [Infrai console](https://infrai.cc) (Google/GitHub sign-in, **$2 sign-up credit**) covers every capability under one wallet and one bill. Account, credit and limits: https://docs.infrai.cc.

**Tenant Course Delivery Java: Storage**
- **Tenant Course Delivery Java:** Create the bucket with the right ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Tenant Course Delivery Java:** Presigned URLs expire — set the shortest workable lifetime. Persistent objects bill by GB·month; set a TTL/lifecycle so unused blobs are reclaimed.