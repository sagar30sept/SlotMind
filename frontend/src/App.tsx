import { useCallback, useEffect, useState } from "react";
import { fetchAppointments, fetchProviders, fetchSlots, fetchWaitlist, setProviderStatus, setSlotStatus } from "./api";
import { BookingDialog, WaitlistDialog } from "./Dialogs";
import { clock, dayLabel, message, sameDay } from "./format";
import { Modal } from "./Modal";
import { PatientHistory } from "./PatientHistory";
import { RescheduleQueue } from "./RescheduleQueue";
import { ScheduleView } from "./ScheduleView";
import { ProvidersPanel, WaitlistView } from "./SidePanels";
import type { Appointment, Provider, Slot, WaitlistEntry } from "./types";
import { VisitsPanel } from "./VisitsPanel";

type Tab = "desk" | "schedule" | "waitlist";
type Dialog = "book" | "waitlist" | null;
type Confirm = { title: string; body: string; action: string; run: () => Promise<unknown> } | null;
type Toast = { id: number; text: string; tone: "ok" | "bad" } | null;

function tabFromHash(): Tab {
  const hash = window.location.hash.slice(1);
  return hash === "schedule" || hash === "waitlist" ? hash : "desk";
}

export function App() {
  const [providers, setProviders] = useState<Provider[]>([]);
  const [slots, setSlots] = useState<Slot[]>([]);
  const [appointments, setAppointments] = useState<Appointment[]>([]);
  const [waitlist, setWaitlist] = useState<WaitlistEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [offline, setOffline] = useState(false);
  const [now, setNow] = useState(() => new Date());
  const [tab, setTab] = useState<Tab>(tabFromHash);
  const [dialog, setDialog] = useState<Dialog>(null);
  const [confirm, setConfirm] = useState<Confirm>(null);
  const [confirming, setConfirming] = useState(false);
  const [toast, setToast] = useState<Toast>(null);
  const [historyId, setHistoryId] = useState<string | null>(null);

  const refresh = useCallback(async () => {
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
    setNow(new Date());
  }, []);

  useEffect(() => {
    let cancelled = false;
    async function tick() {
      try {
        await refresh();
        if (!cancelled) setOffline(false);
      } catch {
        if (!cancelled) setOffline(true);
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
  }, [refresh]);

  useEffect(() => {
    const onHash = () => setTab(tabFromHash());
    window.addEventListener("hashchange", onHash);
    return () => window.removeEventListener("hashchange", onHash);
  }, []);

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(null), 5000);
    return () => window.clearTimeout(timer);
  }, [toast]);

  const notify = useCallback((text: string, tone: "ok" | "bad" = "ok") => setToast({ id: Date.now(), text, tone }), []);
  const closeDialog = useCallback(() => setDialog(null), []);
  const closeHistory = useCallback(() => setHistoryId(null), []);
  const closeConfirm = useCallback(() => setConfirm(null), []);

  function go(next: Tab) {
    window.location.hash = next === "desk" ? "" : next;
    setTab(next);
  }

  async function done(summary: string) {
    setDialog(null);
    notify(summary);
    await refresh().catch(() => undefined);
  }

  function changeProvider(provider: Provider, status: "OFFLINE" | "AVAILABLE") {
    const run = async () => {
      await setProviderStatus(provider.id, status);
      notify(status === "OFFLINE" ? `${provider.name} is offline. Their visits are being moved.` : `${provider.name} is available`);
      await refresh();
    };
    if (status === "OFFLINE") {
      const affected = appointments.filter(
        (appointment) => appointment.providerId === provider.id && (appointment.status === "CONFIRMED" || appointment.status === "RESCHEDULED"),
      ).length;
      setConfirm({
        title: `Set ${provider.name} offline?`,
        body:
          affected === 0
            ? "They have no booked visits to move."
            : `${affected} booked visit${affected === 1 ? "" : "s"} will move to the reschedule queue with a proposed new time.`,
        action: "Set offline",
        run,
      });
      return;
    }
    void run().catch((cause) => notify(message(cause, "Could not update the provider"), "bad"));
  }

  function cancelSlot(slot: Slot, patientName: string | undefined) {
    setConfirm({
      title: `Cancel the ${clock(slot.startTime)} slot?`,
      body: `${dayLabel(slot.startTime)} with ${slot.providerName}.${
        patientName ? ` ${patientName}'s visit will move to the reschedule queue.` : ""
      }`,
      action: "Cancel slot",
      run: async () => {
        await setSlotStatus(slot.id, "CANCELLED");
        notify("Slot cancelled");
        await refresh();
      },
    });
  }

  async function runConfirm() {
    if (!confirm) return;
    setConfirming(true);
    try {
      await confirm.run();
      setConfirm(null);
    } catch (cause) {
      notify(message(cause, "That update failed"), "bad");
    } finally {
      setConfirming(false);
    }
  }

  const pending = appointments.filter((appointment) => appointment.status === "RESCHEDULE_PENDING").length;
  const todays = appointments.filter((appointment) => sameDay(new Date(appointment.slotStart), now));
  const todayActive = todays.filter((appointment) => appointment.status !== "CANCELLED").length;
  const checkedIn = appointments.filter((appointment) => appointment.status === "CHECKED_IN").length;
  const noShows = todays.filter((appointment) => appointment.status === "NO_SHOW").length;
  const openSlots = slots.filter((slot) => slot.status === "OPEN" && new Date(slot.startTime) > now).length;
  const offlineProviders = providers.filter((provider) => provider.status === "OFFLINE").length;

  const today = new Intl.DateTimeFormat(undefined, { weekday: "long", day: "numeric", month: "long" }).format(now);

  return (
    <div className="app">
      <header className="topbar">
        <div className="topbar-inner">
          <div className="brand">
            <span className="logo" aria-hidden="true">S</span>
            <span className="brand-name">SlotMind</span>
            <span className="brand-sep" aria-hidden="true" />
            <span className="muted">{today}</span>
          </div>
          <div className="topbar-actions">
            <span className={offline ? "live off" : "live"} role="status">
              <span className="pulse" aria-hidden="true" />
              {loading ? "Connecting" : offline ? "Offline" : "Live"}
            </span>
            <button type="button" className="btn ghost" onClick={() => setDialog("waitlist")}>Add to waitlist</button>
            <button type="button" className="btn primary" onClick={() => setDialog("book")}>New appointment</button>
          </div>
        </div>
        <nav className="tabs-nav" aria-label="Sections">
          <div className="topbar-inner">
            <button type="button" className={tab === "desk" ? "nav-tab on" : "nav-tab"} aria-current={tab === "desk" ? "page" : undefined} onClick={() => go("desk")}>
              Desk {pending > 0 && <span className="badge attention">{pending}</span>}
            </button>
            <button type="button" className={tab === "schedule" ? "nav-tab on" : "nav-tab"} aria-current={tab === "schedule" ? "page" : undefined} onClick={() => go("schedule")}>
              Schedule
            </button>
            <button type="button" className={tab === "waitlist" ? "nav-tab on" : "nav-tab"} aria-current={tab === "waitlist" ? "page" : undefined} onClick={() => go("waitlist")}>
              Waitlist {waitlist.length > 0 && <span className="badge">{waitlist.length}</span>}
            </button>
          </div>
        </nav>
      </header>

      <main className="page">
        {offline && !loading && (
          <p className="banner bad" role="alert">The desk can't reach the server. Showing the last data received; retrying every 2 seconds.</p>
        )}

        {tab === "desk" && (
          <>
            <section className="stats" aria-label="Today at a glance">
              <Stat label="Needs decision" value={pending} tone={pending > 0 ? "attention" : undefined} />
              <Stat label="Visits today" value={todayActive} />
              <Stat label="Checked in" value={checkedIn} tone={checkedIn > 0 ? "ok" : undefined} />
              <Stat label="No-shows today" value={noShows} tone={noShows > 0 ? "bad" : undefined} />
              <Stat label="Open slots" value={openSlots} />
              <Stat label="Providers offline" value={offlineProviders} tone={offlineProviders > 0 ? "bad" : undefined} />
            </section>
            <div className="desk">
              <div className="desk-main">
                <RescheduleQueue appointments={appointments} onChanged={refresh} onOpenPatient={setHistoryId} />
                <VisitsPanel appointments={appointments} now={now} onChanged={refresh} onOpenPatient={setHistoryId} />
              </div>
              <aside className="desk-side">
                <ProvidersPanel providers={providers} slots={slots} appointments={appointments} onStatus={changeProvider} />
              </aside>
            </div>
          </>
        )}

        {tab === "schedule" && (
          <ScheduleView
            slots={slots}
            providers={providers}
            appointments={appointments}
            now={now}
            onCancelSlot={cancelSlot}
            onOpenPatient={setHistoryId}
          />
        )}

        {tab === "waitlist" && (
          <WaitlistView
            waitlist={waitlist}
            now={now}
            onAdd={() => setDialog("waitlist")}
            onChanged={refresh}
            onOpenPatient={setHistoryId}
            onError={(text) => notify(text, "bad")}
          />
        )}
      </main>

      {dialog === "book" && <BookingDialog providers={providers} slots={slots} now={now} onClose={closeDialog} onDone={(text) => void done(text)} />}
      {dialog === "waitlist" && <WaitlistDialog providers={providers} onClose={closeDialog} onDone={(text) => void done(text)} />}

      {confirm && (
        <Modal title={confirm.title} onClose={closeConfirm}>
          <p className="confirm-body">{confirm.body}</p>
          <footer className="modal-foot">
            <button type="button" className="btn ghost" onClick={closeConfirm}>Keep</button>
            <button type="button" className="btn danger" disabled={confirming} onClick={() => void runConfirm()}>
              {confirming ? "Working…" : confirm.action}
            </button>
          </footer>
        </Modal>
      )}

      {historyId && <PatientHistory patientId={historyId} onClose={closeHistory} />}

      {toast && (
        <div key={toast.id} className={`toast ${toast.tone}`} role={toast.tone === "bad" ? "alert" : "status"}>
          {toast.text}
          <button type="button" className="icon-btn" aria-label="Dismiss" onClick={() => setToast(null)}>×</button>
        </div>
      )}
    </div>
  );
}

function Stat({ label, value, tone }: { label: string; value: number; tone?: "attention" | "ok" | "bad" }) {
  return (
    <article className="stat">
      <p>{label}</p>
      <strong className={tone ?? ""}>{value}</strong>
    </article>
  );
}
