import { useEffect, useState } from "react";
import { fetchPatient } from "./api";
import { dayTime, message, titleCase } from "./format";
import type { PatientDetail } from "./types";

/** Side drawer with a patient's contact details and every visit, latest first. */
export function PatientHistory({ patientId, onClose }: { patientId: string; onClose: () => void }) {
  const [detail, setDetail] = useState<PatientDetail | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setDetail(null);
    setError(null);
    fetchPatient(patientId)
      .then((next) => {
        if (!cancelled) setDetail(next);
      })
      .catch((cause) => {
        if (!cancelled) setError(message(cause, "Could not load this patient"));
      });
    return () => {
      cancelled = true;
    };
  }, [patientId]);

  useEffect(() => {
    function onKey(event: KeyboardEvent) {
      if (event.key === "Escape") onClose();
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  const patient = detail?.patient;
  return (
    <div className="scrim" onClick={onClose}>
      <aside
        className="drawer"
        role="dialog"
        aria-modal="true"
        aria-label="Patient history"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="panel-head">
          <h2>{patient?.fullName ?? "Patient"}</h2>
          <button type="button" className="btn ghost sm" onClick={onClose}>Close</button>
        </div>
        {error && <p className="form-error" role="alert">{error}</p>}
        {!detail && !error && <p className="empty">Loading…</p>}
        {patient && (
          <dl className="facts">
            <dt>Phone</dt>
            <dd>{patient.phone ?? "Not on file"}</dd>
            <dt>Email</dt>
            <dd>{patient.email ?? "Not on file"}</dd>
            <dt>Born</dt>
            <dd>{patient.dateOfBirth ?? "Not on file"}</dd>
            <dt>Record</dt>
            <dd>{patient.id}</dd>
          </dl>
        )}
        {detail && (
          <>
            <h3 className="drawer-sub">Visits · {detail.appointments.length}</h3>
            {detail.appointments.length === 0 && <p className="empty">No visits yet.</p>}
            <ul className="history">
              {detail.appointments.map((appointment) => (
                <li key={appointment.id}>
                  <div>
                    <p className="strong">{dayTime(appointment.slotStart)}</p>
                    <p className="muted small">
                      {titleCase(appointment.appointmentType)} · {appointment.providerName}
                      {appointment.cancelReason ? ` · ${appointment.cancelReason}` : ""}
                    </p>
                  </div>
                  <span className={`status ${appointment.status}`}>{titleCase(appointment.status)}</span>
                </li>
              ))}
            </ul>
          </>
        )}
      </aside>
    </div>
  );
}
