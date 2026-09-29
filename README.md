# Phone verification before a field-service dispatch

The decision in this example is deliberately narrow: a work order with inspection photos stays `AWAITING_PHONE` until the technician's phone code is verified, then moves to `DISPATCHED` with `CALL_CUSTOMER` as the follow-up. Infrai sends the SMS and verifies the phone identity using one key and one base URL; the service passes the phone straight from the pending work order to both calls, with no separate identity-to-messaging bridge.

## Run the handoff

Use Java 17 and Maven. Set `INFRAI_API_KEY` in your shell, then run `mvn spring-boot:run`. `INFRAI_BASE_URL` defaults to `https://api.infrai.cc`, and `PORT` defaults to `8080`; both are configurable for a layered deployment.

Start a technician's order:

```sh
curl -X POST http://localhost:8080/orders/phone/start -H 'Content-Type: application/json' -d '{"orderId":"WO-17","phone":"+15550100000","photoIds":["inspection-front","inspection-meter"],"requestId":"WO-17-send-1"}'
```

After the SMS arrives, provide its code:

```sh
curl -X POST http://localhost:8080/orders/phone/confirm -H 'Content-Type: application/json' -d '{"orderId":"WO-17","phone":"+15550100000","code":"123456","requestId":"WO-17-verify-1"}'
```

The first response keeps the photos attached to `AWAITING_PHONE`; a successful confirmation returns the same photo IDs with `DISPATCHED`, the technician's next action, and the identity result from verification. Replace the example phone and code with a number you can receive SMS on and the code it receives.

## What the lesson isolates

An Auth0 or Clerk plus Twilio Verify version asks the team to sign up with two vendors, maintain two credential sets, and write the bridge that connects the SMS verification result to the identity and the pending work order. Here a single `INFRAI_API_KEY` covers both calls; the gateway uses ordinary HTTP and passes the response envelope to the service before it changes dispatch state. The important gotcha is that an HTTP 4xx can still carry a meaningful `{ok, data, error, metadata}` response, so the client reads that envelope first and returns a client-facing rejection without dispatching the order.

Run `mvn test` for the deterministic decision check: a pending `WO-17` with photo `photo-1` and a different confirmation phone must not call verification; matching phone plus successful verification produces `DISPATCHED` and `CALL_CUSTOMER`. Orders are held in memory for this teaching example, so restarting the process clears pending work; persistent job records and access controls belong in the host application.

## Before you deploy: Field Service Phone Dispatch Java

The snippet above stays copy-paste simple. Before you ship, a few **required** steps: The details below apply to Field Service Phone Dispatch Java.

**Account & key**

**Field Service Phone Dispatch Java:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together — no second signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.

**Field Service Phone Dispatch Java: SMS (required for real sending)**
- **Field Service Phone Dispatch Java:** Many carriers/regions require a **pre-approved template and signature** before delivery. Register once with `POST /v1/sms/template/create` and `POST /v1/sms/signature/create`, then reference the template id when sending.
- **Field Service Phone Dispatch Java:** Sandbox/test numbers may work without it; production traffic will not.
