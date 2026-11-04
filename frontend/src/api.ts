import type { ApiError, Appointment, AppointmentType, Provider, ProviderStatus, Slot, SlotStatus, WaitlistEntry } from "./types";

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(init?.headers ?? {}),
    },
  });
  if (!response.ok) {
    let message = response.statusText;
    try {
      const body = (await response.json()) as ApiError;
      if (body.message) {
        message = body.message;
      }
    } catch {
      message = response.statusText;
    }
    throw new Error(message);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

export function fetchAppointments(): Promise<Appointment[]> {
  return request("/appointments");
}

export function fetchProviders(): Promise<Provider[]> {
  return request("/providers");
}

export function fetchSlots(): Promise<Slot[]> {
  return request("/slots");
}

export function book(body: {
  patientName: string;
  appointmentType: AppointmentType;
  providerId: string;
  slotId: string;
}): Promise<Appointment> {
  return request("/appointments", { method: "POST", body: JSON.stringify(body) });
}

export function setProviderStatus(id: string, status: ProviderStatus): Promise<Provider> {
  return request(`/providers/${id}/status`, { method: "PATCH", body: JSON.stringify({ status }) });
}

export function setSlotStatus(id: string, status: SlotStatus): Promise<Slot> {
  return request(`/slots/${id}/status`, { method: "PATCH", body: JSON.stringify({ status }) });
}

export function decideSuggestion(id: string, action: "ACCEPT" | "REJECT"): Promise<Appointment> {
  return request(`/suggestions/${id}`, { method: "PATCH", body: JSON.stringify({ action }) });
}

export function suggestAgain(appointmentId: string): Promise<Appointment> {
  return request(`/appointments/${appointmentId}/suggest`, { method: "POST" });
}

export function fetchWaitlist(): Promise<WaitlistEntry[]> {
  return request("/waitlist");
}

export function addToWaitlist(body: {
  patientName: string;
  appointmentType: AppointmentType;
  preferredProviderId?: string;
  note?: string;
}): Promise<WaitlistEntry> {
  return request("/waitlist", { method: "POST", body: JSON.stringify(body) });
}

export function removeFromWaitlist(id: string): Promise<void> {
  return request(`/waitlist/${id}`, { method: "DELETE" });
}
