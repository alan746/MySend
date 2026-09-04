export type RoomRevision = {
  version: number;
  available: boolean;
};

type MonitorEventTarget = {
  addEventListener(name: string, listener: EventListener): void;
  removeEventListener(name: string, listener: EventListener): void;
};

type MonitorScheduler = {
  setInterval(callback: () => void, delay: number): number;
  clearInterval(id: number): void;
};

type RoomUpdateMonitorOptions = {
  check: () => Promise<void>;
  isVisible: () => boolean;
  scheduler: MonitorScheduler;
  windowTarget: MonitorEventTarget;
  documentTarget: MonitorEventTarget;
};

type ConsistentRoomSnapshotOptions<TRoom extends { version: number }, TFile> = {
  loadRoom: () => Promise<TRoom>;
  loadFiles: () => Promise<TFile[]>;
  loadRevision: () => Promise<RoomRevision>;
  maximumAttempts?: number;
};

const ROOM_UPDATE_INTERVAL_MS = 30_000;

export function shouldNotifyRoomUpdate(
  currentVersion: number,
  revision: RoomRevision,
) {
  return !revision.available || revision.version > currentVersion;
}

export function shouldConfirmClipboardReplacement(
  clipboardDraft: string,
  savedClipboard: string,
) {
  return clipboardDraft !== savedClipboard;
}

export function resolveClipboardAfterRefresh(
  currentClipboard: string,
  clipboardAtRefreshStart: string,
  refreshedClipboard: string,
) {
  return currentClipboard === clipboardAtRefreshStart
    ? refreshedClipboard
    : currentClipboard;
}

export async function loadConsistentRoomSnapshot<
  TRoom extends { version: number },
  TFile,
>({
  loadRoom,
  loadFiles,
  loadRevision,
  maximumAttempts = 3,
}: ConsistentRoomSnapshotOptions<TRoom, TFile>) {
  for (let attempt = 0; attempt < maximumAttempts; attempt += 1) {
    const room = await loadRoom();
    const files = await loadFiles();
    const revision = await loadRevision();

    if (!revision.available) {
      throw new Error("This ShareRoom is no longer active.");
    }
    if (revision.version === room.version) {
      return { room, files };
    }
  }

  throw new Error("The room is changing quickly. Try loading the update again.");
}

export function startRoomUpdateMonitor({
  check,
  isVisible,
  scheduler,
  windowTarget,
  documentTarget,
}: RoomUpdateMonitorOptions) {
  let intervalId: number | undefined;
  let checking = false;
  let stopped = false;

  async function runCheck() {
    if (stopped || checking || !isVisible()) return;
    checking = true;
    try {
      await check();
    } catch {
      return;
    } finally {
      checking = false;
    }
  }

  function startInterval() {
    if (intervalId !== undefined || !isVisible()) return;
    intervalId = scheduler.setInterval(() => void runCheck(), ROOM_UPDATE_INTERVAL_MS);
  }

  function stopInterval() {
    if (intervalId === undefined) return;
    scheduler.clearInterval(intervalId);
    intervalId = undefined;
  }

  function handleFocus() {
    void runCheck();
  }

  function handleVisibilityChange() {
    if (isVisible()) {
      startInterval();
      void runCheck();
      return;
    }
    stopInterval();
  }

  windowTarget.addEventListener("focus", handleFocus);
  documentTarget.addEventListener("visibilitychange", handleVisibilityChange);
  startInterval();

  return () => {
    stopped = true;
    stopInterval();
    windowTarget.removeEventListener("focus", handleFocus);
    documentTarget.removeEventListener("visibilitychange", handleVisibilityChange);
  };
}
