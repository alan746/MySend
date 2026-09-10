import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { account, captureNavigation, deferred } from "./fixtures";

const request = vi.hoisted(() => vi.fn());
vi.mock("../../app/lib/api", () => ({ api: request }));
vi.mock("../../app/ui/SiteHeader", () => ({ SiteHeader: () => null }));
beforeEach(() => { request.mockReset(); vi.resetModules(); });

async function openSettings(enabled = true) {
  vi.stubEnv("NEXT_PUBLIC_BILLING_ENABLED", String(enabled));
  const { SettingsExperience } = await import("../../app/ui/SettingsExperience");
  render(<SettingsExperience />);
}

it("keeps account actions hidden until authentication completes", async () => {
  const pending = deferred<never>();
  request.mockReturnValue(pending.promise);
  await openSettings();
  expect(screen.getByRole("status")).toHaveTextContent("Opening account settings");
  expect(screen.queryByRole("button", { name: /Upgrade/ })).not.toBeInTheDocument();
  pending.reject(new Error("Sign in required"));
  expect(await screen.findByRole("heading", { name: "Log in to manage your account." })).toBeInTheDocument();
});

it("hides purchase and portal actions when billing is disabled", async () => {
  request.mockResolvedValue(account);
  await openSettings(false);
  expect(await screen.findByText("Premium is being updated.")).toBeInTheDocument();
  expect(screen.queryByRole("button", { name: /Upgrade|Manage billing/ })).not.toBeInTheDocument();
  expect(request).toHaveBeenCalledExactlyOnceWith("/api/auth/me");
});

it.each([
  ["FREE", "Upgrade to Premium", "/api/billing/checkout"],
  ["PREMIUM", "Manage billing", "/api/billing/portal"],
] as const)("opens hosted billing for %s and allows retry on provider failure", async (plan, buttonName, endpoint) => {
  const navigation = captureNavigation();
  const pending = deferred<{ url: string }>();
  request.mockResolvedValueOnce({ ...account, plan })
    .mockRejectedValueOnce(new Error("Billing unavailable"))
    .mockReturnValueOnce(pending.promise);
  await openSettings();
  const user = userEvent.setup();
  const button = await screen.findByRole("button", { name: new RegExp(buttonName) });
  await user.click(button);
  expect(await screen.findByRole("alert")).toHaveTextContent("Billing unavailable");
  expect(button).toBeEnabled();
  await user.click(screen.getByRole("button", { name: "Dismiss" }));
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  await user.click(button);
  expect(button).toBeDisabled();
  expect(navigation.assign).not.toHaveBeenCalled();
  pending.resolve({ url: "https://checkout.stripe.com/test-session" });
  await waitFor(() => expect(navigation.assign).toHaveBeenCalledWith("https://checkout.stripe.com/test-session"));
  expect(request).toHaveBeenLastCalledWith(endpoint, { method: "POST" });
  expect(screen.getByRole("heading", { name: `${plan === "FREE" ? "Free" : "Premium"} plan` })).toBeInTheDocument();
});

it("does not grant Premium just because the checkout return URL says success", async () => {
  window.history.replaceState({}, "", "/settings?payment=success");
  request.mockResolvedValue(account);
  await openSettings();
  expect(await screen.findByRole("heading", { name: "Free plan" })).toBeInTheDocument();
  expect(screen.queryByRole("button", { name: /Manage billing/ })).not.toBeInTheDocument();
  window.history.replaceState({}, "", "/");
});

it("changes a password after code validation and clears sensitive fields", async () => {
  request.mockResolvedValueOnce(account)
    .mockRejectedValueOnce(new Error("Delivery unavailable"))
    .mockResolvedValueOnce({ developmentCode: "123456" })
    .mockRejectedValueOnce(new Error("Code expired"))
    .mockResolvedValueOnce(account);
  await openSettings();
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: /Email a password code/ }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Delivery unavailable");
  await user.click(screen.getByRole("button", { name: /Email a password code/ }));
  expect(await screen.findByText("123456")).toBeInTheDocument();
  const submit = screen.getByRole("button", { name: /Change password/ });
  expect(submit).toBeDisabled();
  fireEvent.change(screen.getByLabelText("Password code"), { target: { value: "12a345678" } });
  expect(screen.getByLabelText("Password code")).toHaveValue("123456");
  expect(submit).toBeDisabled();
  await user.type(screen.getByLabelText(/^New password/), "changed-password");
  await user.click(submit);
  expect(await screen.findByRole("alert")).toHaveTextContent("Code expired");
  await user.click(submit);
  expect(await screen.findByRole("status")).toHaveTextContent("Password changed. Other sessions have been signed out.");
  expect(screen.queryByLabelText(/^New password/)).not.toBeInTheDocument();
  expect(screen.queryByText("123456")).not.toBeInTheDocument();
  expect(request).toHaveBeenLastCalledWith("/api/auth/password/change", {
    method: "POST", body: JSON.stringify({ code: "123456", newPassword: "changed-password" }),
  });
});

it("cancels a password change without submitting and clears the previous code", async () => {
  request.mockResolvedValueOnce(account).mockResolvedValue({});
  await openSettings();
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: /Email a password code/ }));
  await user.type(await screen.findByLabelText("Password code"), "123456");
  await user.click(screen.getByRole("button", { name: "Cancel" }));
  expect(request).toHaveBeenCalledTimes(2);
  await user.click(screen.getByRole("button", { name: /Email a password code/ }));
  expect(await screen.findByLabelText("Password code")).toHaveValue("");
});
