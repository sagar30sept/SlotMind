import { useState } from "react";
import { cancelAppointment, visitAction, type VisitAction } from "./api";
import { clock, dayLabel, message, sameDay, titleCase } from "./format";
import type { Appointment, AppointmentStatus } from "./types";

const CHECK_IN_OPENS_MS = 60 * 60 * 1000;
const MOVABLE: AppointmentStatus[] = ["CONFIRMED", "RESCHEDULED"];
const CANCELLABLE: AppointmentStatus[] = ["CONFIRMED", "RESCHEDULED", "RESCHEDULE_PENDING"];
const UPCOMING: AppointmentStatus[] = ["CONFIRMED", "RESCHEDULED", "RESCHEDULE_PENDING", "CHECKED_IN"];

type View = "upcoming" | "closed";

/** Day-of-visit actions the desk can take, mirroring the server's rules so buttons only show when they will work. */
function actionsFor(appointment: Appointment, now: Date): VisitAction[] {
  const start = new Date(appointment.slotStart).getTime();
  const end = start + appointment.durationMinutes * 60_000;
  const at = now.getTime();
  const actions: VisitAction[] = [];
  if (MOVABLE.includes(appointment.status)) {
    if (at >= start - CHECK_IN_OPENS_MS && at <= end) actions.push("check-in");
    if (at >= start) actions.push("no-show");
    if (at < start) actions.push("reschedule");
  }
  if (appointment.status === "CHECKED_IN") actions.push("complete");
  return actions;
}

const LABEL: Record<VisitAction, string> = {
  "check-in": "Check in",
  complete: "Complete",
  "no-show": "No-show",
  reschedule: "Reschedule",
};

export function VisitsPanel({
  appointments,
  now,
  onChanged,
  onOpenPatient,
}: {
  appointments: Appointment[];
  now: Date;
  onChanged: () => Promise<void>;
  onOpenPatient: (patientId: string) => void;
}) {
  const [view, setView] = useState<View>("upcoming");
  const [busy, setBusy] = useState<string | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [cancelling, setCancelling] = useState<string | null>(null);
  const [reason, setReason] = useState("");

  const upcoming = appointments
    .filter((appointment) => UPCOMING.includes(appointment.status))
    .sort((a, b) => a.slotStart.localeCompare(b.slotStart));
  const closed = appointments
    .filter((appointment) => !UPCOMING.includes(appointment.status))
    .sort((a, b) => b.slotStart.localeCompare(a.slotStart));
  const rows = view === "upcoming" ? upcoming : closed;

  async function run(id: string, work: () => Promise<unknown>) {
    setBusy(id);
    setErrors((current) => ({ ...current, [id]: "" }));
    try {
      await work();
      await onChanged();
    } catch (cause) {
      setErrors((current) => ({ ...current, [id]: message(cause, "That update failed") }));
    } finally {
      setBusy(null);
    }
  }

  function confirmCancel(id: string) {
    void run(id, async () => {
      await cancelAppointment(id, reason.trim() || undefined);
      setCancelling(null);
      setReason("");
    });
  }

  return (
    <section className="panel visits" aria-label="Visits">
      <div className="panel-head">
        <h2>Visits</h2>
        <div className="segmented" role="tablist">
          <button type="button" role="tab" aria-selected={view === "upcoming"} className={view === "upcoming" ? "on" : ""} onClick={() => setView("upcoming")}>
            Upcoming · {upcoming.length}
          </button>
          <button type="button" role="tab" aria-selected={view === "closed"} className={view === "closed" ? "on" : ""} onClick={() => setView("closed")}>
            Closed · {closed.length}
          </button>
        </div>
      </div>
      {rows.length === 0 && (
        <p className="empty">{view === "upcoming" ? "No upcoming visits." : "No completed, cancelled or missed visits yet."}</p>
      )}
      <ul className="visit-list">
        {rows.map((appointment) => {
          const start = new Date(appointment.slotStart);
          const actions = view === "upcoming" ? actionsFor(appointment, now) : [];
          const canCancel = view === "upcoming" && CANCELLABLE.includes(appointment.status);
          const isBusy = busy === appointment.id;
          return (
            <li key={appointment.id} className={appointment.appointmentType === "URGENT" ? "visit urgent" : "visit"}>
              <div className="visit-time">
                <strong>{sameDay(start, now) ? "Today" : dayLabel(appointment.slotStart)}</strong>
                <span className="muted">{clock(appointment.slotStart)}</span>
              </div>
              <div className="visit-copy">
                <button type="button" className="name-link" onClick={() => onOpenPatient(appointment.patientId)}>
                  {appointment.patientName}
                </button>
                <p className="muted visit-meta">
                  <span className={`tag ${appointment.appointmentType}`}>{titleCase(appointment.appointmentType)}</span>
                  {" · "}
                  {appointment.providerName} · {appointment.durationMinutes} min
                </p>
                {appointment.cancelReason && <p className="muted small">Reason: {appointment.cancelReason}</p>}
                {errors[appointment.id] && <p className="form-error" role="alert">{errors[appointment.id]}</p>}
                {cancelling === appointment.id && (
                  <div className="cancel-row">
                    <input
                      aria-label="Cancellation reason"
                      placeholder="Reason (optional)"
                      maxLength={280}
                      value={reason}
                      autoFocus
                      onChange={(event) => setReason(event.target.value)}
                      onKeyDown={(event) => {
                        if (event.key === "Enter") confirmCancel(appointment.id);
                        if (event.key === "Escape") setCancelling(null);
                      }}
                    />
                    <button type="button" className="btn danger sm" disabled={isBusy} onClick={() => confirmCancel(appointment.id)}>
                      {isBusy ? "Cancelling…" : "Cancel visit"}
                    </button>
                    <button type="button" className="btn ghost sm" onClick={() => setCancelling(null)}>Keep</button>
                  </div>
                )}
              </div>
              <div className="visit-actions">
                <span className={`status ${appointment.status}`}>{titleCase(appointment.status)}</span>
                {actions.map((action) => (
                  <button
                    key={action}
                    type="button"
                    className={action === "check-in" || action === "complete" ? "btn primary sm" : "btn ghost sm"}
                    disabled={isBusy}
                    onClick={() => void run(appointment.id, () => visitAction(appointment.id, action))}
                  >
                    {LABEL[action]}
                  </button>
                ))}
                {canCancel && cancelling !== appointment.id && (
                  <button
                    type="button"
                    className="btn link danger"
                    disabled={isBusy}
                    onClick={() => {
                      setCancelling(appointment.id);
                      setReason("");
                    }}
                  >
                    Cancel
                  </button>
                )}
              </div>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
