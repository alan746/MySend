import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { DashboardExperience } from "../../app/ui/DashboardExperience";
import { account, deferred } from "./fixtures";

const { request, push } = vi.hoisted(() => ({ request: vi.fn(), push: vi.fn() }));
vi.mock("../../app/lib/api", () => ({ api: request }));
vi.mock("../../app/ui/SiteHeader", () => ({ SiteHeader: () => null }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));
beforeEach(() => { request.mockReset(); push.mockReset(); });

it("shows loading and the sign-in gate when the session is unavailable", async () => {
  const pending = deferred<never>();
  request.mockReturnValue(pending.promise);
  render(<DashboardExperience />);
  expect(screen.getByRole("status")).toHaveTextContent("Opening your dashboard");
  pending.reject(new Error("Unauthenticated"));
  expect(await screen.findByRole("heading", { name: "Log in to see your rooms." })).toBeInTheDocument();
  expect(request).toHaveBeenCalledTimes(1);
});

it.each(["FREE", "PREMIUM"] as const)("shows active rooms and constrains %s plan options", async (plan) => {
  request.mockResolvedValueOnce({ ...account, plan, roomMinutes: plan === "FREE" ? 60 : 180 })
    .mockResolvedValueOnce([
      { id: "room-1", accessCode: "4821K", visibility: "PRIVATE", remainingEntries: 8, expiresAt: new Date(Date.now() + 600000).toISOString() },
      { id: "room-2", accessCode: "1234A", visibility: "PUBLIC", remainingEntries: 2, expiresAt: "2020-01-01T00:00:00Z" },
    ]);
  render(<DashboardExperience />);
  expect(await screen.findByRole("link", { name: /4821K/ })).toHaveAttribute("href", "/room/4821K");
  expect(screen.getByRole("link", { name: /1234A/ })).toHaveTextContent("0 min left");
  expect(screen.getByLabelText("Guest entries")).toHaveAttribute("max", plan === "FREE" ? "100" : "1000");
  if (plan === "FREE") expect(screen.queryByRole("option", { name: "180 minutes" })).not.toBeInTheDocument();
  else expect(screen.getByRole("option", { name: "180 minutes" })).toBeInTheDocument();
});

it("creates a private room with selected limits and retries a failed request", async () => {
  request.mockResolvedValueOnce(account).mockResolvedValueOnce([])
    .mockRejectedValueOnce(new Error("Room limit reached"))
    .mockResolvedValueOnce({ accessCode: "4821K" });
  render(<DashboardExperience />);
  const user = userEvent.setup();
  expect(await screen.findByText("No active rooms yet.")).toBeInTheDocument();
  await user.click(screen.getByRole("radio", { name: /Private/ }));
  await user.type(screen.getByLabelText(/Room password optional/), "room-secret");
  await user.selectOptions(screen.getByLabelText("Open for"), "30");
  fireEvent.change(screen.getByLabelText("Guest entries"), { target: { value: "50" } });
  await user.click(screen.getByRole("button", { name: /Create ShareRoom/ }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Room limit reached");
  expect(push).not.toHaveBeenCalled();
  await user.click(screen.getByRole("button", { name: /Create ShareRoom/ }));
  await waitFor(() => expect(push).toHaveBeenCalledWith("/room/4821K"));
  expect(request).toHaveBeenLastCalledWith("/api/rooms", {
    method: "POST", body: JSON.stringify({ visibility: "PRIVATE", password: "room-secret", lifetimeMinutes: 30, accessLimit: 50 }),
  });
});

it("omits a previously entered private password when creating a public room", async () => {
  request.mockResolvedValueOnce(account).mockResolvedValueOnce([]).mockResolvedValueOnce({ accessCode: "4821K" });
  render(<DashboardExperience />);
  const user = userEvent.setup();
  await user.click(await screen.findByRole("radio", { name: /Private/ }));
  await user.type(screen.getByLabelText(/Room password optional/), "room-secret");
  await user.click(screen.getByRole("radio", { name: /Public/ }));
  await user.click(screen.getByRole("button", { name: /Create ShareRoom/ }));
  await waitFor(() => expect(push).toHaveBeenCalledWith("/room/4821K"));
  expect(JSON.parse(request.mock.calls[2][1].body)).toMatchObject({ visibility: "PUBLIC", password: null });
});

it("validates and normalizes room codes, then retries entry with a password", async () => {
  request.mockResolvedValueOnce(account).mockResolvedValueOnce([])
    .mockRejectedValueOnce(new Error("Password required"))
    .mockResolvedValueOnce({ accessCode: "4821K" });
  render(<DashboardExperience />);
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: /Enter ShareRoom/ }));
  expect(screen.getByRole("alert")).toHaveTextContent("Enter four digits followed by one letter.");
  expect(request).toHaveBeenCalledTimes(2);
  fireEvent.change(screen.getByLabelText(/^Access code/), { target: { value: "48-21k!" } });
  expect(screen.getByLabelText(/^Access code/)).toHaveValue("4821K");
  await user.click(screen.getByRole("button", { name: /Enter ShareRoom/ }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Password required");
  expect(JSON.parse(request.mock.calls[2][1].body)).toEqual({ accessCode: "4821K", password: null });
  await user.type(screen.getByLabelText(/Room password if required/), "room-secret");
  await user.click(screen.getByRole("button", { name: /Enter ShareRoom/ }));
  await waitFor(() => expect(push).toHaveBeenCalledWith("/room/4821K"));
  expect(request).toHaveBeenLastCalledWith("/api/rooms/enter", {
    method: "POST", body: JSON.stringify({ accessCode: "4821K", password: "room-secret" }),
  });
});

it("does not load rooms after the dashboard has unmounted", async () => {
  const pending = deferred<typeof account>();
  request.mockReturnValue(pending.promise);
  const view = render(<DashboardExperience />);
  view.unmount();
  pending.resolve(account);
  await pending.promise;
  expect(request).toHaveBeenCalledTimes(1);
});
