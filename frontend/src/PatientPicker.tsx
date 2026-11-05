import { useEffect, useState } from "react";
import { createPatient, searchPatients } from "./api";
import { message } from "./format";
import type { Patient } from "./types";

/**
 * Finds an existing patient by name or phone, or registers a new one inline.
 * Lives inside other forms, so the new-patient fields are not a nested <form>.
 */
export function PatientPicker({
  value,
  onChange,
  label = "Patient",
}: {
  value: Patient | null;
  onChange: (patient: Patient | null) => void;
  label?: string;
}) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<Patient[]>([]);
  const [searching, setSearching] = useState(false);
  const [creating, setCreating] = useState(false);
  const [draft, setDraft] = useState({ fullName: "", phone: "", dateOfBirth: "", email: "" });
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (value || creating || query.trim().length < 2) {
      setResults([]);
      return;
    }
    let cancelled = false;
    setSearching(true);
    const timer = window.setTimeout(() => {
      searchPatients(query.trim())
        .then((found) => {
          if (!cancelled) setResults(found);
        })
        .catch(() => {
          if (!cancelled) setResults([]);
        })
        .finally(() => {
          if (!cancelled) setSearching(false);
        });
    }, 250);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [query, value, creating]);

  function startCreate() {
    setCreating(true);
    setError(null);
    setDraft({ fullName: query.trim(), phone: "", dateOfBirth: "", email: "" });
  }

  async function saveNew() {
    if (!draft.fullName.trim() || !draft.phone.trim()) {
      setError("Name and phone are required");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const patient = await createPatient({
        fullName: draft.fullName,
        phone: draft.phone,
        email: draft.email || undefined,
        dateOfBirth: draft.dateOfBirth || undefined,
      });
      setCreating(false);
      setQuery("");
      onChange(patient);
    } catch (cause) {
      setError(message(cause, "Could not register the patient"));
    } finally {
      setSaving(false);
    }
  }

  if (value) {
    return (
      <div className="wide picker">
        <span className="picker-label">{label}</span>
        <div className="chosen">
          <div>
            <p className="strong">{value.fullName}</p>
            <p className="muted small">{value.phone ?? "No phone on file"}</p>
          </div>
          <button type="button" className="btn link" onClick={() => onChange(null)}>
            Change
          </button>
        </div>
      </div>
    );
  }

  if (creating) {
    return (
      <div className="wide picker new-patient">
        <span className="picker-label">New patient</span>
        <input
          aria-label="Full name"
          placeholder="Full name"
          value={draft.fullName}
          onChange={(event) => setDraft({ ...draft, fullName: event.target.value })}
        />
        <input
          aria-label="Phone"
          placeholder="Phone, e.g. +1-555-0100"
          value={draft.phone}
          onChange={(event) => setDraft({ ...draft, phone: event.target.value })}
        />
        <div className="pair">
          <input
            aria-label="Date of birth"
            type="date"
            value={draft.dateOfBirth}
            onChange={(event) => setDraft({ ...draft, dateOfBirth: event.target.value })}
          />
          <input
            aria-label="Email"
            type="email"
            placeholder="Email (optional)"
            value={draft.email}
            onChange={(event) => setDraft({ ...draft, email: event.target.value })}
          />
        </div>
        {error && <p className="form-error" role="alert">{error}</p>}
        <div className="pair">
          <button type="button" className="btn ghost" onClick={() => setCreating(false)} disabled={saving}>
            Back
          </button>
          <button type="button" className="btn primary" onClick={() => void saveNew()} disabled={saving}>
            {saving ? "Saving…" : "Register patient"}
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="wide picker">
      <label className="picker-label" htmlFor={`${label}-search`}>{label}</label>
      <input
        id={`${label}-search`}
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        placeholder="Search name or phone"
        autoComplete="off"
      />
      {query.trim().length >= 2 && (
        <ul className="results" role="listbox">
          {results.map((patient) => (
            <li key={patient.id}>
              <button type="button" onClick={() => onChange(patient)}>
                <span className="strong">{patient.fullName}</span>
                <span className="muted small">{patient.phone ?? "No phone"}{patient.dateOfBirth ? ` · born ${patient.dateOfBirth}` : ""}</span>
              </button>
            </li>
          ))}
          {!searching && results.length === 0 && <li className="muted none">No match</li>}
        </ul>
      )}
      <button type="button" className="btn link add" onClick={startCreate}>
        + New patient
      </button>
    </div>
  );
}
