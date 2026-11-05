export type AppointmentType = "ROUTINE" | "URGENT" | "FOLLOW_UP";
export type AppointmentStatus =
  | "CONFIRMED"
  | "RESCHEDULE_PENDING"
  | "RESCHEDULED"
  | "CHECKED_IN"
  | "COMPLETED"
  | "CANCELLED"
  | "NO_SHOW";
export type ProviderStatus = "AVAILABLE" | "BUSY" | "OFFLINE";
export type SlotStatus = "OPEN" | "BOOKED" | "CANCELLED";
export type TriggerReason = "INITIAL" | "PROVIDER_OFFLINE" | "SLOT_CANCELLED";

export interface Provider {
  id: string;
  name: string;
  specialty: string;
  status: ProviderStatus;
}

export interface Slot {
  id: string;
  providerId: string;
  providerName: string;
  startTime: string;
  durationMinutes: number;
  status: SlotStatus;
}

export interface Suggestion {
  suggestionId: string;
  recommendedSlotId: string;
  recommendedSlotStart: string;
  providerName: string;
  alternateProviderId: string | null;
  confidence: number;
  reasoning: string;
  triggerReason: TriggerReason;
  status: "PENDING" | "ACCEPTED" | "REJECTED";
}

export interface Patient {
  id: string;
  fullName: string;
  phone: string | null;
  email: string | null;
  dateOfBirth: string | null;
}

export interface PatientDetail {
  patient: Patient;
  appointments: Appointment[];
}

export interface Appointment {
  id: string;
  patientId: string;
  patientName: string;
  appointmentType: AppointmentType;
  providerId: string;
  providerName: string;
  slotId: string;
  slotStart: string;
  durationMinutes: number;
  status: AppointmentStatus;
  cancelReason: string | null;
  checkedInAt: string | null;
  suggestion: Suggestion | null;
  lastError: string | null;
}

export interface WaitlistEntry {
  id: string;
  patientId: string;
  patientName: string;
  appointmentType: AppointmentType;
  preferredProviderId: string | null;
  preferredProviderName: string | null;
  note: string | null;
  createdAt: string;
  matchingOpenSlots: number;
}

export interface ApiError {
  code?: string;
  message?: string;
}
