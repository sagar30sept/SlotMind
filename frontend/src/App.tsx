import { FormEvent, useEffect, useMemo, useState } from "react";
import { addToWaitlist, book, decideSuggestion, fetchAppointments, fetchProviders, fetchSlots, fetchWaitlist, removeFromWaitlist, setProviderStatus, setSlotStatus, suggestAgain } from "./api";
import type { Appointment, AppointmentType, Provider, ProviderStatus, Slot, TriggerReason, WaitlistEntry } from "./types";

const TYPES: AppointmentType[] = ["ROUTINE", "URGENT", "FOLLOW_UP"];

function clock(value: string): string {
  return new Intl.DateTimeFormat(undefined, { hour: "numeric", minute: "2-digit" }).format(new Date(value));
}

function titleCase(value: string): string {
  if (value === "FOLLOW_UP") return "Follow-up";
  return value
    .toLowerCase()
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

function waited(value: string, now: Date): string {
  const minutes = Math.max(0, Math.round((now.getTime() - new Date(value).getTime()) / 60000));
  if (minutes < 1) return "just now";
  if (minutes < 60) return `${minutes} min`;
  const hours = Math.round(minutes / 60);
  return hours === 1 ? "1 hour" : `${hours} hours`;
}

function opening(entry: WaitlistEntry): string {
  const who = entry.preferredProviderName ?? "Any provider";
  if (entry.matchingOpenSlots === 0) return `${who} · no open slot`;
  if (entry.matchingOpenSlots === 1) return `${who} · 1 open slot`;
  return `${who} · ${entry.matchingOpenSlots} open slots`;
}

function badge(trigger: TriggerReason): string {
  if (trigger === "PROVIDER_OFFLINE") return "Provider offline";
  if (trigger === "SLOT_CANCELLED") return "Slot cancelled";
  return "Manual";
}

export function App() {
  const [providers, setProviders] = useState<Provider[]>([]);
  const [slots, setSlots] = useState<Slot[]>([]);
  const [appointments, setAppointments] = useState<Appointment[]>([]);
  const [waitlist, setWaitlist] = useState<WaitlistEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [cardErrors, setCardErrors] = useState<Record<string, string>>({});
  const [patientName, setPatientName] = useState("");
  const [appointmentType, setAppointmentType] = useState<AppointmentType>("ROUTINE");
  const [providerId, setProviderId] = useState("");
  const [slotId, setSlotId] = useState("");
  const [waitingName, setWaitingName] = useState("");
  const [waitingType, setWaitingType] = useState<AppointmentType>("ROUTINE");
  const [waitingProviderId, setWaitingProviderId] = useState("");
  const [waitingNote, setWaitingNote] = useState("");
  const [removingId, setRemovingId] = useState<string | null>(null);
  const [now, setNow] = useState(() => new Date());

  async function refresh() {
    const [nextAppointments, nextProviders, nextSlots, nextWaitlist] = await Promise.all([
      fetchAppointments(),
      fetchProviders(),
      fetchSlots(),
      fetchWaitlist(),
    ]);
    setAppointments(nextAppointments);
    setProviders(nextProviders);
    setSlots(nextSlots);
    setWaitlist(nextWaitlist);
  }

  useEffect(() => {
    let cancelled = false;
    async function tick() {
      try {
        const [nextAppointments, nextProviders, nextSlots, nextWaitlist] = await Promise.all([
          fetchAppointments(),
          fetchProviders(),
          fetchSlots(),
          fetchWaitlist(),
        ]);
        if (cancelled) return;
        setAppointments(nextAppointments);
        setProviders(nextProviders);
        setSlots(nextSlots);
        setWaitlist(nextWaitlist);
        setError(null);
        setNow(new Date());
      } catch (cause) {
        if (!cancelled) {
          setError(cause instanceof Error ? cause.message : "The desk could not refresh");
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    void tick();
    const timer = window.setInterval(() => void tick(), 2000);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, []);

  const pending = appointments.filter((appointment) => appointment.status === "RESCHEDULE_PENDING");
  const recent = appointments.filter(
    (appointment) => appointment.suggestion && appointment.status !== "RESCHEDULE_PENDING",
  );
  const openSlots = slots.filter((slot) => slot.status === "OPEN" && slot.providerId === providerId);
  const offlineCount = providers.filter((provider) => provider.status === "OFFLINE").length;
  const openCount = slots.filter((slot) => slot.status === "OPEN").length;

  const schedule = useMemo(
    () => [...slots].sort((a, b) => a.startTime.localeCompare(b.startTime)),
    [slots],
  );

  function loadFor(provider: Provider) {
    const booked = slots.filter((slot) => slot.providerId === provider.id && slot.status === "BOOKED").length;
    const active = appointments.filter(
      (appointment) =>
        appointment.providerId === provider.id &&
        appointment.status !== "COMPLETED",
    ).length;
    return { booked, active };
  }

  async function onBook(event: FormEvent) {
    event.preventDefault();
    setError(null);
    try {
      await book({ patientName, appointmentType, providerId, slotId });
      setPatientName("");
      setSlotId("");
      await refresh();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Booking failed");
    }
  }

  async function onDecision(suggestionId: string, action: "ACCEPT" | "REJECT") {
    setBusyId(suggestionId);
    setCardErrors((current) => ({ ...current, [suggestionId]: "" }));
    try {
      await decideSuggestion(suggestionId, action);
      await refresh();
    } catch (cause) {
      setCardErrors((current) => ({
        ...current,
        [suggestionId]: cause instanceof Error ? cause.message : "That update failed",
      }));
    } finally {
      setBusyId(null);
    }
  }

  async function onSuggestAgain(appointmentId: string) {
    setBusyId(appointmentId);
    setCardErrors((current) => ({ ...current, [appointmentId]: "" }));
    try {
      await suggestAgain(appointmentId);
      await refresh();
    } catch (cause) {
      setCardErrors((current) => ({
        ...current,
        [appointmentId]: cause instanceof Error ? cause.message : "Could not find another slot",
      }));
    } finally {
      setBusyId(null);
    }
  }

  async function onProviderStatus(id: string, status: "OFFLINE" | "AVAILABLE") {
    setError(null);
    try {
      await setProviderStatus(id, status);
      await refresh();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not update the provider");
    }
  }

  async function onAddToWaitlist(event: FormEvent) {
    event.preventDefault();
    setError(null);
    try {
      await addToWaitlist({
        patientName: waitingName,
        appointmentType: waitingType,
        preferredProviderId: waitingProviderId || undefined,
        note: waitingNote || undefined,
      });
      setWaitingName("");
      setWaitingNote("");
      await refresh();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not add to the waitlist");
    }
  }

  async function onRemoveFromWaitlist(id: string) {
    setRemovingId(id);
    setError(null);
    try {
      await removeFromWaitlist(id);
      await refresh();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not remove that patient");
    } finally {
      setRemovingId(null);
    }
  }

  async function cancelSlot(id: string) {
    setError(null);
    try {
      await setSlotStatus(id, "CANCELLED");
      await refresh();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not cancel the slot");
    }
  }

  const today = new Intl.DateTimeFormat(undefined, {
    weekday: "long",
    month: "long",
    day: "numeric",
  }).format(now);

  return (
    <div className="shell">
      <header className="topbar">
        <div className="brand">
          <span className="mark" aria-hidden="true">S</span>
          <div>
            <p className="brand-name">SlotMind</p>
            <p className="brand-sub">CareBridge front desk</p>
          </div>
        </div>
        <p className="today">{today}</p>
        <p className={error ? "live bad" : "live"} role="status">
          <span className="dot" />
          {loading ? "Loading schedule" : error ?? "Live"}
        </p>
      </header>

      <section className="metrics" aria-label="Schedule summary">
        <article>
          <p>Waiting on desk</p>
          <strong className={pending.length > 0 ? "accent" : ""}>{pending.length}</strong>
        </article>
        <article>
          <p>Open slots</p>
          <strong className={openCount > 0 ? "ok" : ""}>{openCount}</strong>
        </article>
        <article>
          <p>Providers offline</p>
          <strong className={offlineCount > 0 ? "bad" : ""}>{offlineCount}</strong>
        </article>
        <article>
          <p>On waitlist</p>
          <strong className={waitlist.length > 0 ? "accent" : ""}>{waitlist.length}</strong>
        </article>
      </section>

      <div className="workspace">
        <section className="panel" aria-label="Pending reschedules">
          <div className="panel-head">
            <h2>Reschedule queue</h2>
            <span>{pending.length === 0 ? "Clear" : `${pending.length} waiting`}</span>
          </div>
          {pending.length === 0 && (
            <p className="empty">No patients are waiting. A suggestion appears when a provider goes offline or a slot is cancelled.</p>
          )}
          <ul className="cards">
            {pending.map((appointment) => {
              const suggestion = appointment.suggestion?.status === "PENDING" ? appointment.suggestion : null;
              const canSuggestAgain = appointment.suggestion != null && appointment.suggestion.status !== "PENDING";
              return (
                <li key={appointment.id} className={appointment.appointmentType === "URGENT" ? "card urgent" : "card"}>
                  <div className="card-body">
                    <p className="eyebrow">
                      <span className={`type ${appointment.appointmentType}`}>{titleCase(appointment.appointmentType)}</span>
                      {suggestion && <span>{badge(suggestion.triggerReason)}</span>}
                    </p>
                    <h3>{appointment.patientName}</h3>
                    <p className="subtle">Currently with {appointment.providerName}</p>
                    {suggestion ? (
                      <>
                        <p className="reason">{suggestion.reasoning}</p>
                        <p className="proposal">
                          <strong>{clock(suggestion.recommendedSlotStart)}</strong>
                          <span>{suggestion.providerName}</span>
                          <span className="confidence">{Math.round(suggestion.confidence * 100)}%</span>
                        </p>
                      </>
                    ) : (
                      <p className="waiting">
                        {appointment.suggestion
                          ? `The last suggestion was ${appointment.suggestion.status.toLowerCase()}. This visit is still waiting for a new slot.`
                          : appointment.lastError ?? "Suggestion not ready yet"}
                      </p>
                    )}
                    {suggestion && cardErrors[suggestion.suggestionId] && (
                      <p className="card-error" role="alert">{cardErrors[suggestion.suggestionId]}</p>
                    )}
                    {canSuggestAgain && cardErrors[appointment.id] && (
                      <p className="card-error" role="alert">{cardErrors[appointment.id]}</p>
                    )}
                  </div>
                  {canSuggestAgain && (
                    <div className="card-actions">
                      <button
                        type="button"
                        className="primary"
                        disabled={busyId === appointment.id}
                        onClick={() => void onSuggestAgain(appointment.id)}
                      >
                        {busyId === appointment.id ? "Finding a slot…" : "Suggest another"}
                      </button>
                    </div>
                  )}
                  {suggestion && (
                    <div className="card-actions">
                      <button
                        type="button"
                        className="primary"
                        disabled={busyId === suggestion.suggestionId}
                        onClick={() => void onDecision(suggestion.suggestionId, "ACCEPT")}
                      >
                        {busyId === suggestion.suggestionId ? "Saving…" : "Accept"}
                      </button>
                      <button
                        type="button"
                        className="ghost"
                        disabled={busyId === suggestion.suggestionId}
                        onClick={() => void onDecision(suggestion.suggestionId, "REJECT")}
                      >
                        Reject
                      </button>
                    </div>
                  )}
                </li>
              );
            })}
          </ul>
          {recent.length > 0 && (
            <div className="recent">
              <h3>Recent</h3>
              <ul>
                {recent.map((appointment) => (
                  <li key={appointment.id}>
                    <span>{appointment.patientName}</span>
                    <span className={`pill ${appointment.suggestion?.status ?? ""}`}>
                      {titleCase(appointment.suggestion?.status ?? appointment.status)}
                    </span>
                    <span className="subtle">
                      {appointment.suggestion?.providerName ?? appointment.providerName}
                      {appointment.suggestion ? ` · ${clock(appointment.suggestion.recommendedSlotStart)}` : ""}
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </section>

        <aside className="side">
          <section className="panel" aria-label="Providers">
            <div className="panel-head">
              <h2>Providers</h2>
            </div>
            <ul className="provider-list">
              {providers.map((provider) => {
                const load = loadFor(provider);
                return (
                  <li key={provider.id}>
                    <span className={`dot ${provider.status}`} aria-hidden="true" />
                    <div className="provider-copy">
                      <p className="person">{provider.name}</p>
                      <p className="subtle">
                        {titleCase(provider.specialty)} · {load.booked} booked · {load.active} active
                      </p>
                    </div>
                    <StatusSelect status={provider.status} onChange={(status) => void onProviderStatus(provider.id, status)} />
                  </li>
                );
              })}
            </ul>
          </section>

          <section className="panel" aria-label="Book an appointment">
            <div className="panel-head">
              <h2>New appointment</h2>
            </div>
            <form className="book" onSubmit={(event) => void onBook(event)}>
              <label className="wide">
                Patient
                <input value={patientName} onChange={(event) => setPatientName(event.target.value)} required placeholder="Full name" />
              </label>
              <label>
                Type
                <select value={appointmentType} onChange={(event) => setAppointmentType(event.target.value as AppointmentType)}>
                  {TYPES.map((type) => (
                    <option key={type} value={type}>{titleCase(type)}</option>
                  ))}
                </select>
              </label>
              <label>
                Provider
                <select value={providerId} onChange={(event) => { setProviderId(event.target.value); setSlotId(""); }} required>
                  <option value="">Select</option>
                  {providers.filter((provider) => provider.status !== "OFFLINE").map((provider) => (
                    <option key={provider.id} value={provider.id}>{provider.name}</option>
                  ))}
                </select>
              </label>
              <label className="wide">
                Open slot
                <select value={slotId} onChange={(event) => setSlotId(event.target.value)} required>
                  <option value="">{providerId ? "Select a slot" : "Choose a provider first"}</option>
                  {openSlots.map((slot) => (
                    <option key={slot.id} value={slot.id}>
                      {clock(slot.startTime)} · {slot.durationMinutes} min
                    </option>
                  ))}
                </select>
              </label>
              <button type="submit" className="primary wide">Book appointment</button>
            </form>
          </section>
        </aside>
      </div>

      <section className="panel waitlist" aria-label="Waitlist">
        <div className="panel-head">
          <h2>Waitlist</h2>
          <span>{waitlist.length === 0 ? "Empty" : `${waitlist.length} waiting for an opening`}</span>
        </div>
        <div className="waitlist-layout">
          <div>
            {waitlist.length === 0 && (
              <p className="empty">No one is waiting for an opening.</p>
            )}
            <ul className="waitlist-list">
              {waitlist.map((entry) => (
                <li key={entry.id}>
                  <div>
                    <p className="eyebrow">
                      <span className={`type ${entry.appointmentType}`}>{titleCase(entry.appointmentType)}</span>
                      <span>Waiting {waited(entry.createdAt, now)}</span>
                    </p>
                    <h3>{entry.patientName}</h3>
                    <p className="subtle">{opening(entry)}</p>
                    {entry.note && <p className="reason">{entry.note}</p>}
                  </div>
                  <button
                    type="button"
                    className="text"
                    disabled={removingId === entry.id}
                    onClick={() => void onRemoveFromWaitlist(entry.id)}
                  >
                    {removingId === entry.id ? "Removing…" : "Remove"}
                  </button>
                </li>
              ))}
            </ul>
          </div>
          <form className="book" onSubmit={(event) => void onAddToWaitlist(event)}>
            <label className="wide">
              Patient
              <input value={waitingName} onChange={(event) => setWaitingName(event.target.value)} required placeholder="Full name" />
            </label>
            <label>
              Type
              <select value={waitingType} onChange={(event) => setWaitingType(event.target.value as AppointmentType)}>
                {TYPES.map((type) => (
                  <option key={type} value={type}>{titleCase(type)}</option>
                ))}
              </select>
            </label>
            <label>
              Provider
              <select value={waitingProviderId} onChange={(event) => setWaitingProviderId(event.target.value)}>
                <option value="">Any</option>
                {providers.map((provider) => (
                  <option key={provider.id} value={provider.id}>{provider.name}</option>
                ))}
              </select>
            </label>
            <label className="wide">
              Note
              <input value={waitingNote} onChange={(event) => setWaitingNote(event.target.value)} placeholder="What they are waiting for" maxLength={280} />
            </label>
            <button type="submit" className="primary wide">Add to waitlist</button>
          </form>
        </div>
      </section>

      <section className="panel board" aria-label="Today">
        <div className="panel-head">
          <h2>Today</h2>
          <span>{schedule.length} slots</span>
        </div>
        <div className="schedule-scroll">
        <table className="schedule">
          <thead>
            <tr>
              <th>Time</th>
              <th>Patient</th>
              <th>Provider</th>
              <th>Visit</th>
              <th>Status</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {schedule.map((slot) => {
              const visit = visitOn(slot, appointments);
              return (
                <tr key={slot.id}>
                  <td>{clock(slot.startTime)}</td>
                  <td className={visit?.appointmentType === "URGENT" ? "urgent-name" : ""}>{visit?.patientName ?? "Open"}</td>
                  <td>{slot.providerName}</td>
                  <td>{visit ? titleCase(visit.appointmentType) : `${slot.durationMinutes} min`}</td>
                  <td><span className={`pill ${slot.status}`}>{titleCase(slot.status)}</span></td>
                  <td>
                    {slot.status === "BOOKED" && (
                      <button type="button" className="text" onClick={() => void cancelSlot(slot.id)}>Cancel</button>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
        </div>
      </section>
    </div>
  );
}

function visitOn(slot: Slot, appointments: Appointment[]): Appointment | undefined {
  return appointments.find((appointment) => appointment.slotId === slot.id && appointment.status !== "COMPLETED");
}

function StatusSelect({
  status,
  onChange,
}: {
  status: ProviderStatus;
  onChange: (status: "OFFLINE" | "AVAILABLE") => void;
}) {
  return (
    <select
      className={`status ${status}`}
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
