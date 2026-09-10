import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import type { Room, RoomFile } from "../../app/lib/api";
import { RoomExperience } from "../../app/ui/RoomExperience";

const request = vi.hoisted(() => vi.fn());
vi.mock("../../app/lib/api", async (original) => ({ ...await original<object>(), api: request }));
vi.mock("../../app/ui/SiteHeader", () => ({ SiteHeader: () => null }));
let room: Room;
let files: RoomFile[];
beforeEach(() => {
  room = {
    id: "room-1", accessCode: "4821K", plan: "FREE", visibility: "PUBLIC", passwordProtected: false,
    accessLimit: 20, accessCount: 1, remainingEntries: 19, clipboardText: "saved", fileBytes: 4,
    fileLimitBytes: 1073741824, clipboardLimit: 10000, createdAt: new Date().toISOString(),
    expiresAt: new Date(Date.now() + 1800000).toISOString(), owner: false, version: 1,
  };
  files = [{ id: "file-1", name: "note.txt", contentType: "text/plain", sizeBytes: 4, uploadedAt: new Date().toISOString() }];
  request.mockReset().mockImplementation(async (path: string, init?: RequestInit) => {
    if (init?.method) return room;
    if (path.endsWith("/revision")) return { version: room.version, available: true };
    if (path.endsWith("/files")) return files;
    return { ...room };
  });
});

it("shows participant content and downloads without owner controls", async () => {
  render(<RoomExperience code="4821K" />);
  expect(await screen.findByRole("textbox", { name: "Shared clipboard" })).toHaveValue("saved");
  expect(screen.getByRole("link", { name: "Download" })).toHaveAttribute("href", expect.stringContaining("/api/rooms/4821K/files/file-1"));
  expect(screen.queryByRole("button", { name: /Delete|Room controls/ })).not.toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Saved" })).toBeDisabled();
});

it("sends the clipboard version, preserves a failed draft and reloads after saving", async () => {
  render(<RoomExperience code="4821K" />);
  const user = userEvent.setup();
  const clipboard = await screen.findByRole("textbox", { name: "Shared clipboard" });
  fireEvent.change(clipboard, { target: { value: "edited" } });
  request.mockRejectedValueOnce(new Error("Version conflict"));
  await user.click(screen.getByRole("button", { name: "Save changes" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Version conflict");
  expect(clipboard).toHaveValue("edited");
  request.mockImplementationOnce(async () => { room = { ...room, version: 2, clipboardText: "edited" }; return room; });
  await user.click(screen.getByRole("button", { name: "Save changes" }));
  expect(await screen.findByRole("button", { name: "Saved" })).toBeDisabled();
  expect(request).toHaveBeenCalledWith("/api/rooms/4821K/clipboard", {
    method: "PATCH", body: JSON.stringify({ text: "edited", version: 1 }),
  });
});

it("requests confirmation before replacing an unsaved draft with a remote update", async () => {
  render(<RoomExperience code="4821K" />);
  const user = userEvent.setup();
  const clipboard = await screen.findByRole("textbox", { name: "Shared clipboard" });
  fireEvent.change(clipboard, { target: { value: "local draft" } });
  room = { ...room, version: 2, clipboardText: "remote text" };
  fireEvent.focus(window);
  const load = await screen.findByRole("button", { name: "Load update" });
  const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
  await user.click(load);
  expect(clipboard).toHaveValue("local draft");
  confirm.mockReturnValue(true);
  await user.click(load);
  await waitFor(() => expect(clipboard).toHaveValue("remote text"));
  expect(screen.queryByRole("button", { name: "Load update" })).not.toBeInTheDocument();
});

it("enters a password-protected room after a rejected attempt", async () => {
  request.mockRejectedValueOnce(new Error("Entry required"));
  render(<RoomExperience code="4821K" />);
  const user = userEvent.setup();
  await user.type(await screen.findByLabelText(/^Password/), "secret");
  request.mockRejectedValueOnce(new Error("Wrong password"));
  await user.click(screen.getByRole("button", { name: /Enter ShareRoom/ }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Wrong password");
  await user.click(screen.getByRole("button", { name: /Enter ShareRoom/ }));
  expect(await screen.findByLabelText("Shared clipboard")).toHaveValue("saved");
  expect(request).toHaveBeenCalledWith("/api/rooms/enter", {
    method: "POST", body: JSON.stringify({ accessCode: "4821K", password: "secret" }),
  });
});

it("uploads a file and retains unsaved clipboard edits across the refresh", async () => {
  render(<RoomExperience code="4821K" />);
  const user = userEvent.setup();
  const clipboard = await screen.findByLabelText("Shared clipboard");
  fireEvent.change(clipboard, { target: { value: "local draft" } });
  files = [...files, { ...files[0], id: "file-2", name: "second.txt" }];
  await user.upload(screen.getByLabelText(/Drop a file or browse/), new File(["new"], "second.txt", { type: "text/plain" }));
  expect(await screen.findByText("second.txt")).toBeInTheDocument();
  expect(clipboard).toHaveValue("local draft");
  const upload = request.mock.calls.find(([, init]) => init?.method === "POST");
  expect(upload?.[0]).toBe("/api/rooms/4821K/files");
  expect(upload?.[1].body.get("file").name).toBe("second.txt");
});

it("requires owner confirmation for deletion and refreshes the file board", async () => {
  room.owner = true;
  render(<RoomExperience code="4821K" />);
  const user = userEvent.setup();
  const remove = await screen.findByRole("button", { name: "Delete note.txt" });
  const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
  await user.click(remove);
  expect(request.mock.calls.some(([, init]) => init?.method === "DELETE")).toBe(false);
  confirm.mockReturnValue(true);
  files = [];
  await user.click(remove);
  expect(await screen.findByText("Files added to this room appear here.")).toBeInTheDocument();
  expect(request).toHaveBeenCalledWith("/api/rooms/4821K/files/file-1", { method: "DELETE" });
});

it("saves owner policy with a version and recovers from a failed save", async () => {
  room.owner = true;
  render(<RoomExperience code="4821K" />);
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: /Room controls/ }));
  await user.selectOptions(screen.getByLabelText("Privacy"), "PRIVATE");
  await user.type(screen.getByLabelText(/^Password/), "new-secret");
  fireEvent.change(screen.getByLabelText("Guest entry limit"), { target: { value: "40" } });
  await user.selectOptions(screen.getByLabelText("Total lifetime"), "60");
  request.mockRejectedValueOnce(new Error("Conflict"));
  await user.click(screen.getByRole("button", { name: "Save controls" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Conflict");
  await user.click(screen.getByRole("button", { name: "Save controls" }));
  await waitFor(() => expect(screen.queryByRole("button", { name: "Save controls" })).not.toBeInTheDocument());
  expect(request).toHaveBeenCalledWith("/api/rooms/4821K/settings", {
    method: "PATCH", body: JSON.stringify({ visibility: "PRIVATE", password: "new-secret", lifetimeMinutes: 60, accessLimit: 40, version: 1 }),
  });
});
