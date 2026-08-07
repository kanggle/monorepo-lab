#!/usr/bin/env python3
"""
supplier-mock — the counterpart procurement-service has always called but that
existed in no compose file (TASK-SCM-BE-060).

`application.yml` has shipped `${SUPPLIER_MOCK_BASE_URL:http://supplier-mock:9090}`
since the service was written; nothing was ever wired to that name, so
`POST /api/procurement/po/{id}/submit` failed at the adapter and every PO stayed
in DRAFT. This is that missing counterpart — a wiring gap, not a design change.

Stdlib only (no pip install, no build step): it is mounted read-only into a
`python:3-alpine` container.

────────────────────────────────────────────────────────────────────────────
What it serves
────────────────────────────────────────────────────────────────────────────

  POST /v1/purchase-orders   the outbound call `SupplierApiClient` makes.
                             Responds `{"receiptRef": …, "status": "ACCEPTED"}` —
                             the exact shape the client's javadoc requires.
  GET  /health               liveness for the compose healthcheck.

Then, asynchronously, it plays the part a real supplier plays: it calls back
`POST /api/procurement/webhooks/supplier-ack` with the HMAC-SHA256 signature the
service's `WebhookSignatureFilter` verifies.

🔴 The ack is NOT optional decoration. `PoStatusMachine` makes
`SUBMITTED → ACKNOWLEDGED` a **SUPPLIER** transition and `CONFIRMED` reachable
only from `ACKNOWLEDGED`. A mock that answered the submit call and stopped would
park every PO in SUBMITTED — visibly better than DRAFT, and still not the arc.

────────────────────────────────────────────────────────────────────────────
🔴 Two things measured while building this — read before changing it
────────────────────────────────────────────────────────────────────────────

1. **The ack races the submit transaction.** `PurchaseOrderApplicationService.
   submit()` calls the supplier FIRST (deliberately — Edge Case #7, so a failed
   supplier call cannot leave a PO SUBMITTED) and only then transitions and
   commits. An ack that arrives before that commit sees a DRAFT PO and is
   rejected by the state machine.

   The fix here is **bounded retry, not a tuned delay**. A fixed sleep is a guess
   about someone else's commit latency; it passes on a fast host and rots on a
   slow one. We retry on the rejection and stop as soon as it takes.

2. **The ack webhook needs a `tenantId` the submit payload never sends.**
   `SupplierAckWebhookRequest` requires `tenantId`, but
   `RestSupplierAdapter.toSupplierPayload` sends only poId / poNumber /
   supplierId / currency / totalAmount / lines. A real supplier could not
   produce that field either — it is the buyer's own partition key.

   So the mock is TOLD the tenant out of band (`ACK_TENANT_ID`). That is a
   limitation of this mock AND an asymmetry in the product's outbound contract;
   it is recorded in the task rather than papered over. If the payload ever
   carries the tenant, delete this env var and read it from the request.
"""

import hashlib
import hmac
import json
import logging
import os
import threading
import time
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = int(os.environ.get("PORT", "9090"))
ACK_ENABLED = os.environ.get("ACK_ENABLED", "true").lower() == "true"
ACK_TARGET_URL = os.environ.get(
    "ACK_TARGET_URL",
    "http://procurement-service:8080/api/procurement/webhooks/supplier-ack",
)
ACK_TENANT_ID = os.environ.get("ACK_TENANT_ID", "scm")
WEBHOOK_SECRET = os.environ.get("WEBHOOK_SECRET", "scm-supplier-webhook-secret")
# First attempt waits this long so the common case does not burn a rejected
# request; the retries below are what actually make it correct.
ACK_INITIAL_DELAY_MS = int(os.environ.get("ACK_INITIAL_DELAY_MS", "800"))
ACK_MAX_ATTEMPTS = int(os.environ.get("ACK_MAX_ATTEMPTS", "8"))
ACK_RETRY_DELAY_MS = int(os.environ.get("ACK_RETRY_DELAY_MS", "1000"))

logging.basicConfig(
    level=logging.INFO, format="%(asctime)s supplier-mock %(levelname)s %(message)s"
)
log = logging.getLogger("supplier-mock")


def _sign(timestamp: str, raw_body: bytes) -> str:
    """HMAC-SHA256 over `timestamp + "." + rawBody`, lowercase hex.

    Mirrors `WebhookSignatureVerifier.computeHmac`. The verifier lowercases the
    provided header before comparing, but we emit lowercase anyway so the wire
    bytes match what the contract documents.
    """
    mac = hmac.new(WEBHOOK_SECRET.encode("utf-8"), digestmod=hashlib.sha256)
    mac.update(timestamp.encode("utf-8"))
    mac.update(b".")
    mac.update(raw_body)
    return mac.hexdigest()


def _post_ack(po_id: str, receipt_ref: str) -> None:
    """Play the supplier's callback: POST the signed ack until it is accepted.

    Retries exist for the submit-transaction race documented in the module
    docstring — NOT to paper over a genuine rejection. A 4xx that is not the
    race (bad signature, unknown PO) exhausts the attempts and is logged loudly;
    it never fails silently.
    """
    time.sleep(ACK_INITIAL_DELAY_MS / 1000.0)
    body = json.dumps(
        {
            "tenantId": ACK_TENANT_ID,
            "poId": po_id,
            "supplierAckRef": receipt_ref,
        }
    ).encode("utf-8")

    last = "no attempt made"
    for attempt in range(1, ACK_MAX_ATTEMPTS + 1):
        # 🔴 Re-stamp the timestamp every attempt. The verifier rejects a
        # signature it has already seen (replay nonce), so a retry that reused
        # the previous timestamp would produce the same signature and be
        # rejected as a replay — the retry would be structurally incapable of
        # succeeding.
        timestamp = str(int(time.time()))
        req = urllib.request.Request(
            ACK_TARGET_URL,
            data=body,
            method="POST",
            headers={
                "Content-Type": "application/json",
                "X-Supplier-Signature": _sign(timestamp, body),
                "X-Supplier-Timestamp": timestamp,
            },
        )
        try:
            with urllib.request.urlopen(req, timeout=10) as resp:
                log.info(
                    "ack accepted for PO %s (attempt %d, HTTP %d)",
                    po_id,
                    attempt,
                    resp.status,
                )
                return
        except urllib.error.HTTPError as e:
            detail = e.read(400).decode("utf-8", "replace")
            last = f"HTTP {e.code} {detail}"
        except Exception as e:  # noqa: BLE001 — transport, DNS, container not up yet
            last = f"{type(e).__name__}: {e}"
        log.info("ack for PO %s not accepted yet (attempt %d) — %s", po_id, attempt, last)
        time.sleep(ACK_RETRY_DELAY_MS / 1000.0)

    log.error(
        "ack for PO %s FAILED after %d attempts — last: %s. The PO stays SUBMITTED "
        "and cannot be confirmed. This is a real failure, not a slow start.",
        po_id,
        ACK_MAX_ATTEMPTS,
        last,
    )


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def _json(self, status: int, payload: dict) -> None:
        raw = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def do_GET(self):  # noqa: N802 — BaseHTTPRequestHandler contract
        if self.path == "/health":
            self._json(200, {"status": "UP"})
        else:
            self._json(404, {"error": "not found"})

    def _read_body(self) -> bytes:
        """Read the request body under either framing.

        🔴 `Content-Length` alone is not enough. Spring's `RestClient` sends this
        submission **chunked** (no Content-Length), so a length-only reader gets
        zero bytes and the payload looks empty — which is exactly how the first
        version of this mock failed: it rejected every real submission as
        "poId missing" while the body was sitting unread on the socket.
        """
        if self.headers.get("Transfer-Encoding", "").lower() == "chunked":
            chunks = []
            while True:
                line = self.rfile.readline().strip()
                if not line:
                    continue
                size = int(line.split(b";")[0], 16)
                if size == 0:
                    self.rfile.readline()  # trailing CRLF after the last chunk
                    break
                chunks.append(self.rfile.read(size))
                self.rfile.readline()  # CRLF after each chunk
            return b"".join(chunks)
        length = int(self.headers.get("Content-Length") or 0)
        return self.rfile.read(length) if length else b""

    def do_POST(self):  # noqa: N802 — BaseHTTPRequestHandler contract
        if self.path != "/v1/purchase-orders":
            self._json(404, {"error": "not found"})
            return

        raw = self._read_body()
        try:
            payload = json.loads(raw or b"{}")
        except json.JSONDecodeError:
            log.warning("rejecting malformed json (%d bytes): %r", len(raw), raw[:200])
            self._json(400, {"error": "malformed json"})
            return

        po_id = str(payload.get("poId") or "")
        po_number = str(payload.get("poNumber") or "")
        if not po_id:
            # 🔴 Fail loudly, and SAY WHAT WE SAW. A mock that accepts a payload
            # without the field the ack needs would return 200 and then never
            # ack — the PO would sit in SUBMITTED with nothing explaining why.
            # The first version rejected here without logging, so the failure
            # read as "the mock was never called".
            log.warning(
                "rejecting submission — no poId. framing=%s bytes=%d keys=%s body=%r",
                self.headers.get("Transfer-Encoding") or
                f"content-length:{self.headers.get('Content-Length')}",
                len(raw),
                sorted(payload.keys()) if isinstance(payload, dict) else type(payload).__name__,
                raw[:200],
            )
            self._json(400, {"error": "poId missing from submission payload"})
            return

        receipt_ref = f"SUP-ACK-{po_number or po_id}"
        idem = self.headers.get("Idempotency-Key", "")
        log.info("submission received: po=%s number=%s idempotency-key=%s",
                 po_id, po_number, idem)

        self._json(200, {"receiptRef": receipt_ref, "status": "ACCEPTED"})

        if ACK_ENABLED:
            threading.Thread(
                target=_post_ack, args=(po_id, receipt_ref), daemon=True
            ).start()

    def log_message(self, fmt, *args):  # quieter default access log
        log.debug(fmt, *args)


if __name__ == "__main__":
    log.info(
        "listening on :%d — ack=%s target=%s tenant=%s",
        PORT,
        ACK_ENABLED,
        ACK_TARGET_URL,
        ACK_TENANT_ID,
    )
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
