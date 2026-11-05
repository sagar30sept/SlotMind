# SlotMind

CareBridge front desk for rebooking appointments when a provider goes offline or a slot is cancelled. The API proposes a slot. The desk confirms it with the patient before the booking changes.

## Run

From `backend`:

```bash
mvn spring-boot:run
```

The API listens on port 8080. H2 console: http://localhost:8080/h2-console  
JDBC URL `jdbc:h2:file:./data/slotmind`, user `sa`, empty password.

Flyway owns the schema (`backend/src/main/resources/db/migration`). A database created before Flyway is baselined at V1 and upgraded in place. Patients are created from the names already on file. Delete `backend/data/` for a fresh seed.

From `frontend`:

```bash
npm install
npm run dev
```

The desk is at http://localhost:5173 and proxies `/appointments`, `/patients`, `/providers`, `/slots`, `/suggestions`, and `/waitlist` to the API.

## Gemini

The default strategy is `ai`, model `gemini-3.5-flash`. Put the key in `backend/config/application-local.yml` (gitignored) or export `SLOTMIND_LLM_API_KEY` before starting the API. That file is loaded with `spring.config.import` and is not packaged as a committed secret. If the model call fails, the rule strategy still stores a suggestion. Set `slotmind.scheduling.strategy=rule` to skip the network.

`StrategyRegistry` resolves that property on every call, for both `POST /appointments/{id}/suggest` and the outbox suggest job.

## Five-minute walkthrough

1. Open the desk. Seed data shows five providers and four confirmed appointments.
2. In New appointment, search a patient by name or phone, or use "+ New patient" to register one (name and phone required). Book them onto an open slot. The appointment is `CONFIRMED` and that slot is `BOOKED`. Booking the same patient at an overlapping time returns `PATIENT_DOUBLE_BOOKED`.
3. Show `slotmind.scheduling.strategy` in `backend/src/main/resources/application.yml`. Both the suggest path and the worker use `StrategyRegistry`.
4. Set Dr. Sarah Chen (`PRV-001`) to offline. The PATCH returns before suggestion rows exist.
5. Wait for two cards badged "Provider offline". Read one reasoning sentence aloud. The recommended slots are still `OPEN`.
6. Accept one card. That appointment is `RESCHEDULED`, its provider is the new slot's provider, the old slot is `OPEN`, and the new slot is `BOOKED`.
7. Close on ADR sections 3 and 5 in `ADR.md`: the accept transaction, and `RoomConstraintStrategy` as a new bean with no caller change.

Cancelling `SLT-003` creates one "Slot cancelled" suggestion for Aisha Patel.

### Visit lifecycle

The Visits panel lists upcoming visits, and its Closed tab lists finished ones. Buttons appear only when the server would allow the action:

- **Check in**: from 60 minutes before the visit until it ends. Then **Complete**.
- **No-show**: once the visit has started.
- **Reschedule**: queues the visit for a new proposal, including a visit that was already moved.
- **Cancel**: asks for an optional reason and reopens the slot.

Click a patient's name anywhere to open their history.

| Endpoint | Effect |
|---|---|
| `GET /patients?q=` / `POST /patients` / `GET /patients/{id}` | Search, register, history |
| `GET /appointments/{id}` | One visit with its latest proposal |
| `POST /appointments/{id}/cancel` `{reason}` | `CANCELLED`, slot reopened |
| `POST /appointments/{id}/check-in` | `CHECKED_IN` |
| `POST /appointments/{id}/complete` | `COMPLETED` |
| `POST /appointments/{id}/no-show` | `NO_SHOW` |
| `POST /appointments/{id}/reschedule` | 202, `RESCHEDULE_PENDING` |

