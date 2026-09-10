import { afterEach, expect, it, vi } from "vitest";
import {
  loadConsistentRoomSnapshot, resolveClipboardAfterRefresh,
  shouldConfirmClipboardReplacement, shouldNotifyRoomUpdate, startRoomUpdateMonitor,
} from "../../app/lib/room-updates";
import { deferred } from "./fixtures";

afterEach(() => { vi.useRealTimers(); });

it("protects unsaved drafts and only notifies on newer or unavailable state", () => {
  expect(shouldNotifyRoomUpdate(4, { version: 3, available: true })).toBe(false);
  expect(shouldNotifyRoomUpdate(4, { version: 4, available: true })).toBe(false);
  expect(shouldNotifyRoomUpdate(4, { version: 5, available: true })).toBe(true);
  expect(shouldNotifyRoomUpdate(4, { version: 4, available: false })).toBe(true);
  expect(shouldConfirmClipboardReplacement("draft", "saved")).toBe(true);
  expect(shouldConfirmClipboardReplacement("saved", "saved")).toBe(false);
  expect(resolveClipboardAfterRefresh("new draft", "old draft", "remote")).toBe("new draft");
  expect(resolveClipboardAfterRefresh("draft", "draft", "remote")).toBe("remote");
});

it("retries a racing snapshot and rejects closed or continuously changing rooms", async () => {
  const loadRoom = vi.fn().mockResolvedValueOnce({ version: 1 }).mockResolvedValue({ version: 2 });
  const loadFiles = vi.fn().mockResolvedValue(["file"]);
  const loadRevision = vi.fn().mockResolvedValue({ version: 2, available: true });
  await expect(loadConsistentRoomSnapshot({ loadRoom, loadFiles, loadRevision })).resolves.toEqual({ room: { version: 2 }, files: ["file"] });
  expect(loadRoom).toHaveBeenCalledTimes(2);
  loadRevision.mockResolvedValue({ version: 3, available: true });
  loadRoom.mockClear();
  await expect(loadConsistentRoomSnapshot({ loadRoom, loadFiles, loadRevision })).rejects.toThrow("changing quickly");
  expect(loadRoom).toHaveBeenCalledTimes(3);
  loadRevision.mockResolvedValue({ version: 3, available: false });
  await expect(loadConsistentRoomSnapshot({ loadRoom, loadFiles, loadRevision })).rejects.toThrow("no longer active");
});

it("pauses in hidden tabs, avoids overlapping checks, recovers after failure and stops cleanly", async () => {
  vi.useFakeTimers();
  let visible = false;
  const pending = deferred<void>();
  const check = vi.fn().mockReturnValueOnce(pending.promise).mockRejectedValueOnce(new Error("Offline")).mockResolvedValue(undefined);
  const windowTarget = new EventTarget();
  const documentTarget = new EventTarget();
  const stop = startRoomUpdateMonitor({ check, isVisible: () => visible, scheduler: window, windowTarget, documentTarget });
  await vi.advanceTimersByTimeAsync(30000);
  expect(check).not.toHaveBeenCalled();
  visible = true;
  documentTarget.dispatchEvent(new Event("visibilitychange"));
  windowTarget.dispatchEvent(new Event("focus"));
  await vi.advanceTimersByTimeAsync(30000);
  expect(check).toHaveBeenCalledTimes(1);
  pending.resolve();
  await pending.promise;
  await vi.advanceTimersByTimeAsync(30000);
  expect(check).toHaveBeenCalledTimes(2);
  await vi.advanceTimersByTimeAsync(30000);
  expect(check).toHaveBeenCalledTimes(3);
  visible = false;
  documentTarget.dispatchEvent(new Event("visibilitychange"));
  windowTarget.dispatchEvent(new Event("focus"));
  await vi.advanceTimersByTimeAsync(30000);
  expect(check).toHaveBeenCalledTimes(3);
  stop();
  visible = true;
  windowTarget.dispatchEvent(new Event("focus"));
  documentTarget.dispatchEvent(new Event("visibilitychange"));
  await vi.advanceTimersByTimeAsync(30000);
  expect(check).toHaveBeenCalledTimes(3);
  expect(vi.getTimerCount()).toBe(0);
});
