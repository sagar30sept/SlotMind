# SlotMind architecture decisions

## 1. Scheduling logic placement

### Context

Booking has to stay conflict-safe, and the way a slot is chosen has to be replaceable. The HTTP suggest path and the background worker both need a ranking, and neither should own the ranking rules.

### Options

- Rank slots inside the controller.
- Keep an if/else in the application service.
- Give state transitions to the entities and ranking to a strategy port. Application services own the transactions.

### Decision

Entities own the legal transitions. `SchedulingStrategy` returns an ordered list of recommendations and does not load the database. `BookingService`, `SuggestionWriter`, and `SuggestionDecisionService` own the transactions.

### Tradeoffs

There are more types than a single service method. The controller stays thin, the rules can be tested without HTTP, and a new ranking does not edit the booking transaction.

## 2. Strategy switchability

### Context

Rule-based ranking and the Gemini ranking must be selectable without recompiling, and the desk request and the outbox worker must not pick different strategies.

### Options

- Branch in each caller.
- Use `@Conditional` beans so only one strategy is on the classpath.
- Register every `SchedulingStrategy` bean by `name()` and resolve `slotmind.scheduling.strategy` on each call.

### Decision

`StrategyRegistry` resolves the config value on every call. HTTP suggest and the suggest job both call the registry. A later `RoomConstraintStrategy` is a new bean plus a config value. Callers stay unchanged. `reasoning` is a sentence the desk can read aloud. Slot and provider ids stay in the other fields.

### Tradeoffs

Changing the config takes a restart. Callers cannot drift, and adding a strategy does not require a registry edit.

## 3. Transactional accept

### Context

Accept finalizes a booking the patient has agreed to. It must update the appointment, the old slot, and the new slot together. Two coordinators can accept two suggestions that name the same open slot.

### Options

- Hold a pessimistic lock on the slot.
- Use `@Version` plus status guards in one transaction.
- Rely on a database constraint alone.

### Decision

One transaction requires the suggestion to be `PENDING`, the appointment to be `RESCHEDULE_PENDING`, and the new slot to be `OPEN`. It books the new slot, points the appointment at that slot and that slot's provider, sets the appointment to `RESCHEDULED`, and marks the suggestion `ACCEPTED`. A second accept of the same suggestion fails the `PENDING` guard. Two suggestions for one slot lose on `@Version` or the `OPEN` check, the loser rolls back, and the API returns 409. The losing appointment stays `RESCHEDULE_PENDING`.

### Tradeoffs

The conflict is visible and retryable. The transaction does not stay open across the model call. A lost accept asks the desk to refresh.

## 4. Agentic loop decoupling

### Context

Setting a provider `OFFLINE` affects every confirmed appointment for that provider. Cancelling a slot affects the one appointment on that slot. Both endpoints must return before suggestions exist. A crash must not drop the work, and a duplicate run must not create a second pending suggestion.

### Options

- Publish an in-process event and handle it on another thread.
- Do the fan-out and the model call inside one job.
- Write a fan-out row in the status transaction, then child `SUGGEST` rows that a poller claims.

### Decision

The PATCH transaction updates the status and inserts `FANOUT_PROVIDER_OFFLINE` or `FANOUT_SLOT_CANCELLED`, then returns. `ProviderOfflineFanout` and `SlotCancelledFanout` only choose the appointments. `RescheduleOrchestrator` enqueues one `SUGGEST` job per confirmed appointment. One suggest handler runs `SchedulingStrategy`. Gemini failures become the rule list inside the strategy, and that recommendation is still stored. The recommended slot stays `OPEN` until the desk accepts. A poller claims jobs with a conditional update and a lease. `findClaimableIds()` is the lookup a later CDC listener can replace. The reclaim scan for an expired lease stays.

### Tradeoffs

On H2 the worker wakes about once a second. The HTTP thread does not call the model. The job row is durable across a restart. CDC can replace the wake-up later without changing the handlers.

## 5. Extensibility

### Context

Sprint 2 adds room and equipment constraints. The callers that book, suggest, and accept should not change to support that.

### Options

- Edit the rule strategy so it also knows about rooms.
- Add `RoomConstraintStrategy` as another implementation of `SchedulingStrategy`.

### Decision

A room strategy implements `SchedulingStrategy`, is a Spring bean with its own `name()`, and is selected with `slotmind.scheduling.strategy`. The registry, the suggest endpoint, and the worker stay as they are. This sprint does not build a patient portal, billing, waitlist auto-fill, notifications, CDC, or a database load study. The desk can list patients who are waiting for an opening. Nothing books them when a slot opens.

### Tradeoffs

The strategy sees only the context it is given today: the appointment, urgency, patient name, trigger, open slots, and the provider roster. A room strategy can use that contract immediately. Richer inputs, such as a room inventory, can be added to `SchedulingContext` without changing the method signature's callers.
