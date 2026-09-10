import { beforeEach, describe, expect, it, vi } from "vitest";

describe("API transport", () => {
  const fetchMock = vi.fn<typeof fetch>();
  beforeEach(() => {
    vi.resetModules();
    fetchMock.mockReset();
    vi.stubGlobal("fetch", fetchMock);
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "https://api.example.test/");
  });

  it("sends JSON with credentials and the browser mutation marker", async () => {
    const { api } = await import("../../app/lib/api");
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ id: "room-1" })));
    const body = JSON.stringify({ visibility: "PUBLIC" });
    await expect(api("/api/rooms", {
      method: "POST", body, headers: { "X-Trace": "test" }, credentials: "omit",
    })).resolves.toEqual({ id: "room-1" });
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("https://api.example.test/api/rooms");
    expect(init).toMatchObject({ method: "POST", body, credentials: "include" });
    const headers = new Headers(init?.headers);
    expect(headers.get("Content-Type")).toBe("application/json");
    expect(headers.get("X-Requested-With")).toBe("MySendWeb");
    expect(headers.get("X-Trace")).toBe("test");
  });

  it("leaves multipart boundaries to fetch and accepts empty responses", async () => {
    const { api } = await import("../../app/lib/api");
    const body = new FormData();
    body.append("file", new File(["hello"], "note.txt"));
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    await expect(api("/api/files", { method: "POST", body })).resolves.toBeUndefined();
    expect(new Headers(fetchMock.mock.calls[0][1]?.headers).has("Content-Type")).toBe(false);
    expect(fetchMock.mock.calls[0][1]?.body).toBe(body);
  });

  it.each([
    [JSON.stringify({ message: "Room has closed" }), 410, "Room has closed"],
    [JSON.stringify({}), 403, "Request failed (403)"],
    ["upstream unavailable", 502, "Request failed (502)"],
  ])("handles an unsuccessful response: %s", async (body, status, message) => {
    const { api } = await import("../../app/lib/api");
    fetchMock.mockResolvedValue(new Response(body, { status }));
    await expect(api("/api/rooms")).rejects.toThrow(message);
  });

  it("maps timeouts to a retry message and sets the request deadline", async () => {
    const { api } = await import("../../app/lib/api");
    const timeout = vi.spyOn(AbortSignal, "timeout");
    fetchMock.mockRejectedValue(new DOMException("expired", "TimeoutError"));
    await expect(api("/api/rooms")).rejects.toThrow("The request timed out. Please try again.");
    expect(timeout).toHaveBeenCalledWith(15_000);
  });

  it("combines caller cancellation with the deadline", async () => {
    const { api } = await import("../../app/lib/api");
    const controller = new AbortController();
    fetchMock.mockResolvedValue(new Response("[]"));
    await api("/api/rooms", { signal: controller.signal });
    const signal = fetchMock.mock.calls[0][1]?.signal;
    expect(signal?.aborted).toBe(false);
    controller.abort();
    expect(signal?.aborted).toBe(true);
  });

  it("reports a network failure without leaking transport details", async () => {
    const { api } = await import("../../app/lib/api");
    fetchMock.mockRejectedValue(new TypeError("private transport detail"));
    await expect(api("/api/rooms")).rejects.toThrow("The MySend service is not connected yet.");
  });

  it("supports same-origin requests and encodes download path segments", async () => {
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", undefined);
    const { api, roomDownloadUrl } = await import("../../app/lib/api");
    fetchMock.mockResolvedValue(new Response("[]"));
    await api("/api/rooms");
    expect(fetchMock.mock.calls[0][0]).toBe("/api/rooms");
    expect(roomDownloadUrl("4821/K", "file ?#")).toBe("/api/rooms/4821%2FK/files/file%20%3F%23");
  });
});
