# SlotMind

CareBridge front desk for rebooking appointments when a provider goes offline or a slot is cancelled. The API proposes a slot. The desk confirms it with the patient before the booking changes.

## Run

From `backend`:

```bash
mvn spring-boot:run
```

The API listens on port 8080. H2 console: http://localhost:8080/h2-console  
JDBC URL `jdbc:h2:file:./data/slotmind`, user `sa`, empty password.

From `frontend`:

```bash
npm install
npm run dev
```

The desk is at http://localhost:5173 and proxies `/appointments`, `/providers`, `/slots`, `/suggestions`, and `/waitlist` to the API.

## Gemini

The default strategy is `ai`, model `gemini-3.5-flash`. Put the key in `backend/config/application-local.yml` (gitignored) or export `SLOTMIND_LLM_API_KEY` before starting the API. That file is loaded with `spring.config.import` and is not packaged as a committed secret. If the model call fails, the rule strategy still stores a suggestion. Set `slotmind.scheduling.strategy=rule` to skip the network.

`StrategyRegistry` resolves that property on every call, for both `POST /appointments/{id}/suggest` and the outbox suggest job.

## Five-minute walkthrough

1. Open the desk. Seed data shows five providers and four confirmed appointments.
2. Book a patient onto an open slot (`SLT-004` or `SLT-005`). The appointment is `CONFIRMED` and that slot is `BOOKED`.
3. Show `slotmind.scheduling.strategy` in `backend/src/main/resources/application.yml`. Both the suggest path and the worker use `StrategyRegistry`.
4. Set Dr. Sarah Chen (`PRV-001`) to offline. The PATCH returns before suggestion rows exist.
5. Wait for two cards badged "Provider offline". Read one reasoning sentence aloud. The recommended slots are still `OPEN`.
6. Accept one card. That appointment is `RESCHEDULED`, its provider is the new slot's provider, the old slot is `OPEN`, and the new slot is `BOOKED`.
7. Close on ADR sections 3 and 5 in `ADR.md`: the accept transaction, and `RoomConstraintStrategy` as a new bean with no caller change.

Cancelling `SLT-003` creates one "Slot cancelled" suggestion for Aisha Patel.
