# SlotMind architecture decisions

Accepted. Ranking stays swappable, accept stays atomic, and suggestion work leaves the request path.

## 1. Scheduling logic placement

### Context

A booking must be conflict-safe, and the ranking that proposes a slot must be replaceable. HTTP suggest and the background worker both need a ranking. Neither owns the rules.

### Options

- Rank inside the controller.
- Branch in the application service.
- Entities own legal transitions. A strategy port owns ranking. Application services own the transactions.

### Decision

Entities own legal transitions. `SchedulingStrategy` returns an ordered list of recommendations and does not load the database. `BookingService`, `SuggestionWriter`, and `SuggestionDecisionService` own the transactions.

### Consequences

The cost is extra types. The controller stays thin, ranking tests without HTTP, and a new ranking does not edit the booking transaction.

## 2. Strategy selection

### Context

Rule ranking and Gemini ranking must switch without a recompile. The desk request and the outbox worker must resolve the same strategy.

### Options

- Branch in each caller.
- Use `@Conditional` so only one strategy is on the classpath.
- Register every `SchedulingStrategy` by `name()` and resolve `slotmind.scheduling.strategy` on each call.

### Decision

`StrategyRegistry` resolves the configured name on every call. HTTP suggest and the suggest job both call the registry. A new strategy is a bean plus a config value. Callers stay unchanged. `reasoning` is one sentence for the coordinator. Slot and provider ids stay in their own fields.

### Consequences

A config change takes a restart. Callers cannot drift, and adding a strategy does not edit the registry.

## 3. Transactional accept

### Context

Accept commits a booking the patient has agreed to. The appointment, the released slot, and the new slot must commit together. Two coordinators can accept suggestions that name the same open slot.

### Options

- Pessimistic lock on the slot.
- `@Version` plus status guards in one transaction.
- A database constraint alone.

### Decision

One transaction proceeds only when the suggestion is `PENDING`, the appointment is `RESCHEDULE_PENDING`, and the target slot is `OPEN`. It books the new slot, points the appointment at that slot and its provider, sets the appointment to `RESCHEDULED`, and marks the suggestion `ACCEPTED`. A second accept of the same suggestion fails the `PENDING` guard. Two suggestions for one slot lose on `@Version` or the `OPEN` check. The loser rolls back and the API returns 409. The losing appointment stays `RESCHEDULE_PENDING`.

### Consequences

The conflict is explicit and safe to retry. The transaction does not span the model call. A lost race asks the desk to refresh.

## 4. Agentic loop decoupling

### Context

Provider `OFFLINE` fans out to every confirmed appointment for that provider. Slot cancel affects the one appointment on that slot. Both endpoints must return before any suggestion exists. A crash must not drop the work. A replay must not create a second pending suggestion.

### Options

- Publish an in-process event and handle it on another thread.
- Run fan-out and the model call inside one job.
- Write a fan-out row in the status transaction, then child `SUGGEST` rows that a poller claims.

### Decision

The PATCH transaction updates status and inserts `FANOUT_PROVIDER_OFFLINE` or `FANOUT_SLOT_CANCELLED`, then returns. `ProviderOfflineFanout` and `SlotCancelledFanout` select the appointments. `RescheduleOrchestrator` enqueues one `SUGGEST` job per confirmed appointment. The suggest handler runs `SchedulingStrategy`. A Gemini failure falls back to the rule list inside the strategy, and that recommendation is still stored. The recommended slot stays `OPEN` until the desk accepts. A poller claims jobs with a conditional update and a lease. `findClaimableIds()` is the lookup a later CDC listener can replace. Lease reclaim stays.

### Consequences

On H2 the worker polls about once a second. The request thread never calls the model. The job row survives restart. CDC can replace the wake-up without changing the handlers.

## 5. Extensibility

### Context

Sprint 2 adds room and equipment constraints. Book, suggest, and accept must not change to support that.

### Options

- Fold rooms into the rule strategy.
- Add `RoomConstraintStrategy` as another `SchedulingStrategy`.

### Decision

A room strategy implements `SchedulingStrategy`, is a Spring bean with its own `name()`, and is selected by `slotmind.scheduling.strategy`. The registry, the suggest endpoint, and the worker stay as they are.

Out of scope this sprint: patient portal, billing, waitlist auto-fill, notifications, CDC, and a database load study. The desk lists patients waiting for an opening. Nothing books them when a slot opens.

### Consequences

A strategy sees only today's `SchedulingContext`: the appointment, urgency, patient name, trigger, open slots, and the provider roster. A room strategy can implement that contract now. A room inventory can be added to the context without changing callers.
