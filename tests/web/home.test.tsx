import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { HomeExperience } from "../../app/ui/HomeExperience";
import { account } from "./fixtures";

const { request, push } = vi.hoisted(() => ({ request: vi.fn(), push: vi.fn() }));
vi.mock("../../app/lib/api", () => ({ api: request }));
vi.mock("../../app/ui/SiteHeader", () => ({ SiteHeader: () => null }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));
beforeEach(() => { request.mockReset(); push.mockReset(); });

it("creates a guest room with the guest limits and retries after failure", async () => {
  request.mockRejectedValueOnce(new Error("No session"))
    .mockRejectedValueOnce(new Error("Room limit reached")).mockResolvedValueOnce({ accessCode: "4821K" });
  render(<HomeExperience />);
  const user = userEvent.setup();
  expect(screen.getByLabelText("Guest entries")).toHaveAttribute("max", "20");
  expect(screen.queryByRole("option", { name: "30 minutes" })).not.toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "Create ShareRoom" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Room limit reached");
  await user.click(screen.getByRole("button", { name: "Create ShareRoom" }));
  await waitFor(() => expect(push).toHaveBeenCalledWith("/room/4821K"));
  expect(request).toHaveBeenLastCalledWith("/api/rooms", {
    method: "POST", body: JSON.stringify({ visibility: "PUBLIC", password: null, lifetimeMinutes: 15, accessLimit: 20 }),
  });
});

it("applies member limits when creating a private room", async () => {
  request.mockResolvedValueOnce({ ...account, plan: "PREMIUM", roomMinutes: 180 }).mockResolvedValueOnce({ accessCode: "4821K" });
  render(<HomeExperience />);
  const user = userEvent.setup();
  expect(await screen.findByText("Premium account limits are active.")).toBeInTheDocument();
  await user.click(screen.getByRole("radio", { name: /Private/ }));
  await user.type(screen.getByLabelText(/Room password/), "secret");
  await user.selectOptions(screen.getByLabelText("Open for"), "180");
  fireEvent.change(screen.getByLabelText("Guest entries"), { target: { value: "500" } });
  await user.click(screen.getByRole("button", { name: "Create ShareRoom" }));
  await waitFor(() => expect(push).toHaveBeenCalledWith("/room/4821K"));
  expect(JSON.parse(request.mock.calls[1][1].body)).toEqual({ visibility: "PRIVATE", password: "secret", lifetimeMinutes: 180, accessLimit: 500 });
});

it("validates guest entry, normalizes codes and clears errors on tab changes", async () => {
  request.mockRejectedValueOnce(new Error("No session")).mockRejectedValueOnce(new Error("Wrong password"))
    .mockResolvedValueOnce({ accessCode: "4821K" });
  render(<HomeExperience />);
  const user = userEvent.setup();
  await user.click(screen.getByRole("tab", { name: "Join with code" }));
  await user.click(screen.getByRole("button", { name: "Enter ShareRoom" }));
  expect(screen.getByRole("alert")).toHaveTextContent("Enter four digits followed by one letter.");
  expect(request).toHaveBeenCalledTimes(1);
  fireEvent.change(screen.getByLabelText("Five-character access code"), { target: { value: "48-21k" } });
  await user.click(screen.getByRole("button", { name: "Enter ShareRoom" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Wrong password");
  await user.type(screen.getByLabelText(/Room password/), "secret");
  await user.click(screen.getByRole("button", { name: "Enter ShareRoom" }));
  await waitFor(() => expect(push).toHaveBeenCalledWith("/room/4821K"));
  expect(request).toHaveBeenLastCalledWith("/api/rooms/enter", {
    method: "POST", body: JSON.stringify({ accessCode: "4821K", password: "secret" }),
  });
  await user.click(screen.getByRole("tab", { name: "Create a room" }));
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
});
