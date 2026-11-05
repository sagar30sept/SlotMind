import { useState } from "react";
import { decideSuggestion, suggestAgain } from "./api";
import { dayTime, message, titleCase } from "./format";
import type { Appointment, TriggerReason } from "./types";

function trigger(reason: TriggerReason): string {
  if (reason === "PROVIDER_OFFLINE") return "Provider offline";
  if (reason === "SLOT_CANCELLED") return "Slot cancelled";
  return "Desk request";
}

/** Visits waiting for the desk to confirm a new time with the patient. */
export function RescheduleQueue({
  appointments,
  onChanged,
  onOpenPatient,
}: {
  appointments: Appointment[];
  onChanged: () => Promise<void>;
  onOpenPatient: (patientId: string) => void;
}) {
  const [busy, setBusy] = useState<string | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [showRecent, setShowRecent] = useState(false);

  const pending = appointments
    .filter((appointment) => appointment.status === "RESCHEDULE_PENDING")
    .sort((a, b) => Number(b.appointmentType === "URGENT") - Number(a.appointmentType === "URGENT") || a.slotStart.localeCompare(b.slotStart));
  const recent = appointments.filter(
    (appointment) => appointment.suggestion && appointment.suggestion.status !== "PENDING" && appointment.status !== "RESCHEDULE_PENDING",
  );

  async function run(appointmentId: string, work: () => Promise<unknown>, fallback: string) {
    setBusy(appointmentId);
    setErrors((current) => ({ ...current, [appointmentId]: "" }));
    try {
      await work();
      await onChanged();
    } catch (cause) {
      setErrors((current) => ({ ...current, [appointmentId]: message(cause, fallback) }));
    } finally {
      setBusy(null);
    }
  }

  if (pending.length === 0) {
    return (
      <section className="clear-banner" aria-label="Reschedule queue">
        <span className="check" aria-hidden="true">✓</span>
        <p>
          <strong>No reschedules to review.</strong> New proposals appear here when a provider goes offline, a slot is cancelled, or you reschedule a visit.
        </p>
        {recent.length > 0 && (
          <button type="button" className="btn link" onClick={() => setShowRecent((value) => !value)}>
            {showRecent ? "Hide" : "Recent"} ({recent.length})
          </button>
        )}
        {showRecent && <RecentList appointments={recent} />}
      </section>
    );
  }

  return (
    <section className="panel" aria-label="Reschedule queue">
      <header className="panel-head">
        <div>
          <h2>Needs your decision</h2>
          <p className="panel-sub">Confirm the new time with the patient, then accept.</p>
        </div>
        <span className="count attention">{pending.length}</span>
      </header>
      <ul className="queue">
        {pending.map((appointment) => {
          const suggestion = appointment.suggestion?.status === "PENDING" ? appointment.suggestion : null;
          const isBusy = busy === appointment.id;
          return (
            <li key={appointment.id} className={appointment.appointmentType === "URGENT" ? "proposal urgent" : "proposal"}>
              <div className="proposal-main">
                <div className="proposal-title">
                  <button type="button" className="name-link" onClick={() => onOpenPatient(appointment.patientId)}>
                    {appointment.patientName}
                  </button>
                  <span className={`tag ${appointment.appointmentType}`}>{titleCase(appointment.appointmentType)}</span>
                  {(suggestion ?? appointment.suggestion) && (
                    <span className="tag neutral">{trigger((suggestion ?? appointment.suggestion)!.triggerReason)}</span>
                  )}
                </div>
                <p className="muted">
                  Was {dayTime(appointment.slotStart)} with {appointment.providerName}
                </p>
                {suggestion ? (
                  <>
                    <p className="move">
                      <span className="arrow" aria-hidden="true">→</span>
                      <strong>{dayTime(suggestion.recommendedSlotStart)}</strong>
                      <span>with {suggestion.providerName}</span>
                      <span className="confidence">{Math.round(suggestion.confidence * 100)}% fit</span>
                    </p>
                    <p className="reasoning">{suggestion.reasoning}</p>
                  </>
                ) : (
                  <p className="muted waiting">
                    {appointment.suggestion
                      ? `Last proposal was ${appointment.suggestion.status.toLowerCase()}.`
                      : appointment.lastError ?? "Finding a new time…"}
                  </p>
                )}
                {errors[appointment.id] && <p className="form-error" role="alert">{errors[appointment.id]}</p>}
              </div>
              <div className="proposal-actions">
                {suggestion ? (
                  <>
                    <button
                      type="button"
                      className="btn primary"
                      disabled={isBusy}
                      onClick={() => void run(appointment.id, () => decideSuggestion(suggestion.suggestionId, "ACCEPT"), "Could not accept")}
                    >
                      {isBusy ? "Saving…" : "Accept"}
                    </button>
                    <button
                      type="button"
                      className="btn ghost"
                      disabled={isBusy}
                      onClick={() => void run(appointment.id, () => decideSuggestion(suggestion.suggestionId, "REJECT"), "Could not reject")}
                    >
                      Reject
                    </button>
                  </>
                ) : (
                  appointment.suggestion && (
                    <button
                      type="button"
                      className="btn ghost"
                      disabled={isBusy}
                      onClick={() => void run(appointment.id, () => suggestAgain(appointment.id), "Could not find another time")}
                    >
                      {isBusy ? "Searching…" : "Suggest another"}
                    </button>
                  )
                )}
              </div>
            </li>
          );
        })}
      </ul>
      {recent.length > 0 && (
        <footer className="panel-foot">
          <button type="button" className="btn link" onClick={() => setShowRecent((value) => !value)}>
            {showRecent ? "Hide recent decisions" : `Recent decisions (${recent.length})`}
          </button>
          {showRecent && <RecentList appointments={recent} />}
        </footer>
      )}
    </section>
  );
}

function RecentList({ appointments }: { appointments: Appointment[] }) {
  return (
    <ul className="recent">
      {appointments.slice(0, 8).map((appointment) => (
        <li key={appointment.id}>
          <span>{appointment.patientName}</span>
          <span className={`status ${appointment.suggestion!.status}`}>{titleCase(appointment.suggestion!.status)}</span>
          <span className="muted">
            {appointment.suggestion!.providerName} · {dayTime(appointment.suggestion!.recommendedSlotStart)}
          </span>
        </li>
      ))}
    </ul>
  );
}
