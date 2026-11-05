import { useState } from "react";
import { removeFromWaitlist } from "./api";
import { message, titleCase } from "./format";
import type { Appointment, AppointmentStatus, Provider, ProviderStatus, Slot, WaitlistEntry } from "./types";

const CLOSED: AppointmentStatus[] = ["COMPLETED", "CANCELLED", "NO_SHOW"];

/** Provider roster with live status; setting a provider offline sends their visits to the queue. */
export function ProvidersPanel({
  providers,
  slots,
  appointments,
  onStatus,
}: {
  providers: Provider[];
  slots: Slot[];
  appointments: Appointment[];
  onStatus: (provider: Provider, status: "OFFLINE" | "AVAILABLE") => void;
}) {
  return (
    <section className="panel" aria-label="Providers">
      <header className="panel-head">
        <h2>Providers</h2>
        <span className="muted small">{providers.filter((provider) => provider.status === "OFFLINE").length} offline</span>
      </header>
      <ul className="providers">
        {providers.map((provider) => {
          const open = slots.filter((slot) => slot.providerId === provider.id && slot.status === "OPEN").length;
          const active = appointments.filter(
            (appointment) => appointment.providerId === provider.id && !CLOSED.includes(appointment.status),
          ).length;
          return (
            <li key={provider.id}>
              <span className={`dot ${provider.status}`} aria-hidden="true" />
              <div className="provider-copy">
                <p className="strong">{provider.name}</p>
                <p className="muted small">
                  {titleCase(provider.specialty)} · {active} visits · {open} open
                </p>
              </div>
              <StatusSelect status={provider.status} onChange={(status) => onStatus(provider, status)} />
            </li>
          );
        })}
      </ul>
    </section>
  );
}

function StatusSelect({ status, onChange }: { status: ProviderStatus; onChange: (status: "OFFLINE" | "AVAILABLE") => void }) {
  return (
    <select
      className={`status-select ${status}`}
      aria-label="Provider status"
      value={status}
      onChange={(event) => {
        const next = event.target.value;
        if (next === "OFFLINE" || next === "AVAILABLE") onChange(next);
      }}
    >
      {status === "BUSY" && <option value="BUSY">Busy</option>}
      <option value="AVAILABLE">Available</option>
      <option value="OFFLINE">Offline</option>
    </select>
  );
}

function waited(value: string, now: Date): string {
  const minutes = Math.max(0, Math.round((now.getTime() - new Date(value).getTime()) / 60000));
  if (minutes < 1) return "just now";
  if (minutes < 60) return `${minutes} min`;
  const hours = Math.round(minutes / 60);
  return hours === 1 ? "1 hour" : `${hours} hours`;
}

/** Patients waiting for an opening, oldest first. */
export function WaitlistView({
  waitlist,
  now,
  onAdd,
  onChanged,
  onOpenPatient,
  onError,
}: {
  waitlist: WaitlistEntry[];
  now: Date;
  onAdd: () => void;
  onChanged: () => Promise<void>;
  onOpenPatient: (patientId: string) => void;
  onError: (text: string) => void;
}) {
  const [removing, setRemoving] = useState<string | null>(null);

  async function remove(entry: WaitlistEntry) {
    setRemoving(entry.id);
    try {
      await removeFromWaitlist(entry.id);
      await onChanged();
    } catch (cause) {
      onError(message(cause, "Could not remove that patient"));
    } finally {
      setRemoving(null);
    }
  }

  return (
    <section className="panel" aria-label="Waitlist">
      <header className="panel-head">
        <div>
          <h2>Waitlist</h2>
          <p className="panel-sub">Patients waiting for an earlier opening.</p>
        </div>
        <button type="button" className="btn ghost" onClick={onAdd}>+ Add patient</button>
      </header>
      {waitlist.length === 0 ? (
        <p className="empty">No one is waiting for an opening.</p>
      ) : (
        <div className="table-scroll">
        <table className="table">
          <thead>
            <tr>
              <th scope="col">Patient</th>
              <th scope="col">Visit</th>
              <th scope="col">Preference</th>
              <th scope="col">Matching openings</th>
              <th scope="col">Waiting</th>
              <th scope="col"><span className="sr-only">Actions</span></th>
            </tr>
          </thead>
          <tbody>
            {waitlist.map((entry) => (
              <tr key={entry.id}>
                <td>
                  <button type="button" className="name-link" onClick={() => onOpenPatient(entry.patientId)}>
                    {entry.patientName}
                  </button>
                  {entry.note && <p className="muted small">{entry.note}</p>}
                </td>
                <td><span className={`tag ${entry.appointmentType}`}>{titleCase(entry.appointmentType)}</span></td>
                <td>{entry.preferredProviderName ?? <span className="muted">Any provider</span>}</td>
                <td className={entry.matchingOpenSlots === 0 ? "muted" : ""}>{entry.matchingOpenSlots}</td>
                <td className="muted">{waited(entry.createdAt, now)}</td>
                <td className="actions">
                  <button type="button" className="btn link danger" disabled={removing === entry.id} onClick={() => void remove(entry)}>
                    {removing === entry.id ? "Removing…" : "Remove"}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        </div>
      )}
    </section>
  );
}
