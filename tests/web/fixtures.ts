import type { Account } from "../../app/lib/api";
import { vi } from "vitest";

export const account: Account = {
  id: "account-1", email: "member@example.test", plan: "FREE",
  activeRoomLimit: 2, roomMinutes: 60, clipboardCharacters: 10000,
  roomFileBytes: 1073741824, billingProfileAvailable: false,
};

export function captureNavigation() {
  const location = { assign: vi.fn(), replace: vi.fn() };
  const originalWindow = window;
  vi.stubGlobal("window", new Proxy(originalWindow, {
    get(target, property) {
      if (property === "location") return location;
      return Reflect.get(target, property, target);
    },
  }));
  return location;
}

export function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}
