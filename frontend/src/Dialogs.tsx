import { FormEvent, useMemo, useState } from "react";
import { addToWaitlist, book } from "./api";
import { clock, dayLabel, message, titleCase } from "./format";
import { Modal } from "./Modal";
import { PatientPicker } from "./PatientPicker";
import type { AppointmentType, Patient, Provider, Slot } from "./types";

const TYPES: AppointmentType[] = ["ROUTINE", "URGENT", "FOLLOW_UP"];

/** Books a patient onto one of a provider's open future slots, grouped by day. */
export function BookingDialog({
  providers,
  slots,
  now,
  onClose,
  onDone,
}: {
  providers: Provider[];
  slots: Slot[];
  now: Date;
  onClose: () => void;
  onDone: (summary: string) => void;
}) {
  const [patient, setPatient] = useState<Patient | null>(null);
  const [type, setType] = useState<AppointmentType>("ROUTINE");
  const [providerId, setProviderId] = useState("");
  const [slotId, setSlotId] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const days = useMemo(() => {
    const open = slots
      .filter((slot) => slot.status === "OPEN" && slot.providerId === providerId && new Date(slot.startTime) > now)
      .sort((a, b) => a.startTime.localeCompare(b.startTime));
    const grouped = new Map<string, Slot[]>();
    for (const slot of open) {
      const key = dayLabel(slot.startTime);
      grouped.set(key, [...(grouped.get(key) ?? []), slot]);
    }
    return [...grouped.entries()];
  }, [slots, providerId, now]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!patient) {
      setError("Choose or register a patient first.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const booked = await book({ patientId: patient.id, appointmentType: type, providerId, slotId });
      onDone(`Booked ${booked.patientName} with ${booked.providerName}, ${dayLabel(booked.slotStart)} at ${clock(booked.slotStart)}`);
    } catch (cause) {
      setError(message(cause, "Booking failed"));
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal title="New appointment" onClose={onClose}>
      <form className="form" onSubmit={(event) => void submit(event)}>
        <PatientPicker value={patient} onChange={setPatient} label="Patient" />
        <div className="field-row">
          <label className="field">
            <span>Visit type</span>
            <select value={type} onChange={(event) => setType(event.target.value as AppointmentType)}>
              {TYPES.map((value) => (
                <option key={value} value={value}>{titleCase(value)}</option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>Provider</span>
            <select
              value={providerId}
              required
              onChange={(event) => {
                setProviderId(event.target.value);
                setSlotId("");
              }}
            >
              <option value="">Select provider</option>
              {providers.filter((provider) => provider.status !== "OFFLINE").map((provider) => (
                <option key={provider.id} value={provider.id}>{provider.name}</option>
              ))}
            </select>
          </label>
        </div>
        <label className="field">
          <span>Time</span>
          <select value={slotId} required disabled={!providerId} onChange={(event) => setSlotId(event.target.value)}>
            <option value="">{providerId ? (days.length ? "Select a time" : "No open times") : "Choose a provider first"}</option>
            {days.map(([day, daySlots]) => (
              <optgroup key={day} label={day}>
                {daySlots.map((slot) => (
                  <option key={slot.id} value={slot.id}>
                    {clock(slot.startTime)} · {slot.durationMinutes} min
                  </option>
                ))}
              </optgroup>
            ))}
          </select>
        </label>
        {error && <p className="form-error" role="alert">{error}</p>}
        <footer className="modal-foot">
          <button type="button" className="btn ghost" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn primary" disabled={saving}>{saving ? "Booking…" : "Book appointment"}</button>
        </footer>
      </form>
    </Modal>
  );
}

/** Adds a patient to the waitlist with an optional provider preference and note. */
export function WaitlistDialog({
  providers,
  onClose,
  onDone,
}: {
  providers: Provider[];
  onClose: () => void;
  onDone: (summary: string) => void;
}) {
  const [patient, setPatient] = useState<Patient | null>(null);
  const [type, setType] = useState<AppointmentType>("ROUTINE");
  const [providerId, setProviderId] = useState("");
  const [note, setNote] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!patient) {
      setError("Choose or register a patient first.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const entry = await addToWaitlist({
        patientId: patient.id,
        appointmentType: type,
        preferredProviderId: providerId || undefined,
        note: note.trim() || undefined,
      });
      onDone(`Added ${entry.patientName} to the waitlist`);
    } catch (cause) {
      setError(message(cause, "Could not add to the waitlist"));
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal title="Add to waitlist" onClose={onClose}>
      <form className="form" onSubmit={(event) => void submit(event)}>
        <PatientPicker value={patient} onChange={setPatient} label="Patient" />
        <div className="field-row">
          <label className="field">
            <span>Visit type</span>
            <select value={type} onChange={(event) => setType(event.target.value as AppointmentType)}>
              {TYPES.map((value) => (
                <option key={value} value={value}>{titleCase(value)}</option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>Preferred provider</span>
            <select value={providerId} onChange={(event) => setProviderId(event.target.value)}>
              <option value="">Any provider</option>
              {providers.map((provider) => (
                <option key={provider.id} value={provider.id}>{provider.name}</option>
              ))}
            </select>
          </label>
        </div>
        <label className="field">
          <span>Note <em>optional</em></span>
          <input value={note} maxLength={280} placeholder="e.g. Mornings only" onChange={(event) => setNote(event.target.value)} />
        </label>
        {error && <p className="form-error" role="alert">{error}</p>}
        <footer className="modal-foot">
          <button type="button" className="btn ghost" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn primary" disabled={saving}>{saving ? "Adding…" : "Add to waitlist"}</button>
        </footer>
      </form>
    </Modal>
  );
}
