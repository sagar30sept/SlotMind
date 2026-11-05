import { useMemo, useState } from "react";
import { clock, dayLabel, sameDay, titleCase } from "./format";
import type { Appointment, Provider, Slot } from "./types";

function dayKey(value: string | Date): string {
  const date = new Date(value);
  return `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}`;
}

function chipLabel(value: string, now: Date): string {
  const date = new Date(value);
  if (sameDay(date, now)) return "Today";
  const tomorrow = new Date(now);
  tomorrow.setDate(now.getDate() + 1);
  if (sameDay(date, tomorrow)) return "Tomorrow";
  return dayLabel(value);
}

/** One day at a time: providers across, start times down. */
export function ScheduleView({
  slots,
  providers,
  appointments,
  now,
  onCancelSlot,
  onOpenPatient,
}: {
  slots: Slot[];
  providers: Provider[];
  appointments: Appointment[];
  now: Date;
  onCancelSlot: (slot: Slot, patientName: string | undefined) => void;
  onOpenPatient: (patientId: string) => void;
}) {
  const days = useMemo(() => {
    const seen = new Map<string, string>();
    for (const slot of [...slots].sort((a, b) => a.startTime.localeCompare(b.startTime))) {
      const key = dayKey(slot.startTime);
      if (!seen.has(key)) seen.set(key, slot.startTime);
    }
    return [...seen.entries()].map(([key, first]) => ({ key, first }));
  }, [slots]);

  const [picked, setPicked] = useState<string | null>(null);
  const todayKey = dayKey(now);
  const selected = picked && days.some((day) => day.key === picked)
    ? picked
    : days.find((day) => day.key === todayKey)?.key ?? days[0]?.key;

  const visitBySlot = useMemo(() => {
    const map = new Map<string, Appointment>();
    for (const appointment of appointments) {
      // A cancelled visit releases its slot, so it never describes what is on the slot now.
      if (appointment.status !== "CANCELLED") map.set(appointment.slotId, appointment);
    }
    return map;
  }, [appointments]);

  const daySlots = slots.filter((slot) => dayKey(slot.startTime) === selected);
  const times = [...new Set(daySlots.map((slot) => clock(slot.startTime)))]
    .map((label) => ({ label, at: daySlots.find((slot) => clock(slot.startTime) === label)!.startTime }))
    .sort((a, b) => a.at.localeCompare(b.at));
  const cell = new Map(daySlots.map((slot) => [`${slot.providerId}|${clock(slot.startTime)}`, slot]));
  const booked = daySlots.filter((slot) => slot.status === "BOOKED").length;
  const open = daySlots.filter((slot) => slot.status === "OPEN").length;

  return (
    <section className="panel" aria-label="Schedule">
      <header className="panel-head wrap">
        <div className="day-chips" role="tablist" aria-label="Day">
          {days.map((day) => (
            <button
              key={day.key}
              type="button"
              role="tab"
              aria-selected={day.key === selected}
              className={day.key === selected ? "chip on" : "chip"}
              onClick={() => setPicked(day.key)}
            >
              {chipLabel(day.first, now)}
            </button>
          ))}
        </div>
        <p className="muted small">
          <span className="legend booked" /> {booked} booked <span className="legend open" /> {open} open
        </p>
      </header>

      {times.length === 0 ? (
        <p className="empty">No slots on this day.</p>
      ) : (
        <div className="calendar-scroll">
          <table className="calendar">
            <thead>
              <tr>
                <th className="time-col" scope="col">Time</th>
                {providers.map((provider) => (
                  <th key={provider.id} scope="col">
                    <span className={`dot ${provider.status}`} aria-hidden="true" />
                    {provider.name}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {times.map((time) => (
                <tr key={time.label}>
                  <th className="time-col" scope="row">{time.label}</th>
                  {providers.map((provider) => {
                    const slot = cell.get(`${provider.id}|${time.label}`);
                    if (!slot) return <td key={provider.id} className="cell none" />;
                    const visit = visitBySlot.get(slot.id);
                    if (slot.status === "CANCELLED") {
                      return (
                        <td key={provider.id} className="cell cancelled">
                          <span>Cancelled</span>
                        </td>
                      );
                    }
                    if (slot.status === "OPEN") {
                      return (
                        <td key={provider.id} className="cell open">
                          <span>Open</span>
                        </td>
                      );
                    }
                    const past = new Date(slot.startTime) < now;
                    return (
                      <td key={provider.id} className={`cell booked ${visit?.appointmentType ?? ""}`}>
                        <div className="cell-body">
                          {visit ? (
                            <button type="button" className="name-link" onClick={() => onOpenPatient(visit.patientId)}>
                              {visit.patientName}
                            </button>
                          ) : (
                            <span>Booked</span>
                          )}
                          <span className="cell-meta">
                            {visit ? `${titleCase(visit.appointmentType)} · ${titleCase(visit.status)}` : `${slot.durationMinutes} min`}
                          </span>
                        </div>
                        {!past && (
                          <button
                            type="button"
                            className="cell-action"
                            title="Cancel this slot (moves the visit to the reschedule queue)"
                            aria-label={`Cancel slot at ${time.label} with ${provider.name}`}
                            onClick={() => onCancelSlot(slot, visit?.patientName)}
                          >
                            ×
                          </button>
                        )}
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
