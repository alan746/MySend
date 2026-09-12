import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { api } from "../../app/lib/api";
import { AuthExperience } from "../../app/ui/AuthExperience";
import { PasswordResetExperience } from "../../app/ui/PasswordResetExperience";
import { account, captureNavigation, deferred } from "./fixtures";

vi.mock("../../app/lib/api", () => ({ api: vi.fn() }));
vi.mock("../../app/ui/SiteHeader", () => ({ SiteHeader: () => null }));
const request = vi.mocked(api);
beforeEach(() => { request.mockReset(); });

async function enterDetails() {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText("Email"), account.email);
  await user.type(screen.getByLabelText(/^Password/), "secure-password");
  return user;
}

it("submits login once while pending and redirects after success", async () => {
  const navigation = captureNavigation();
  const pending = deferred<typeof account>();
  request.mockReturnValue(pending.promise);
  render(<AuthExperience mode="login" />);
  const user = await enterDetails();
  await user.click(screen.getByRole("button", { name: "Log in" }));
  expect(screen.getByRole("button", { name: "Logging in..." })).toBeDisabled();
  expect(navigation.replace).not.toHaveBeenCalled();
  expect(request).toHaveBeenCalledExactlyOnceWith("/api/auth/login", {
    method: "POST", body: JSON.stringify({ email: account.email, password: "secure-password" }),
  });
  pending.resolve(account);
  await waitFor(() => expect(navigation.replace).toHaveBeenCalledWith("/dashboard"));
});

it.each([new Error("Invalid credentials"), "unknown"])("allows login retry after %s", async (error) => {
  request.mockRejectedValue(error);
  render(<AuthExperience mode="login" />);
  const user = await enterDetails();
  await user.click(screen.getByRole("button", { name: "Log in" }));
  expect(await screen.findByRole("alert")).toHaveTextContent(error instanceof Error ? error.message : "Something went wrong.");
  expect(screen.getByRole("button", { name: "Log in" })).toBeEnabled();
});

it.each([true, false])("registers using a six-digit code (delivered: %s)", async (delivered) => {
  const navigation = captureNavigation();
  request.mockResolvedValueOnce({ delivered, developmentCode: delivered ? undefined : "123456" })
    .mockRejectedValueOnce(new Error("Code expired"))
    .mockResolvedValueOnce(account);
  render(<AuthExperience mode="signup" />);
  const user = await enterDetails();
  await user.click(screen.getByRole("button", { name: "Send verification code" }));
  expect(await screen.findByRole("status")).toHaveTextContent(delivered ? "Check your email" : "Local delivery is active");
  expect(screen.getByText("(Please check Junk or Promotions. The code may be there.)")).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Finish setup" })).toBeDisabled();
  fireEvent.change(screen.getByLabelText("Verification code"), { target: { value: "12a345678" } });
  expect(screen.getByLabelText("Verification code")).toHaveValue("123456");
  await user.click(screen.getByRole("button", { name: "Finish setup" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Code expired");
  expect(navigation.replace).not.toHaveBeenCalled();
  await user.click(screen.getByRole("button", { name: "Finish setup" }));
  await waitFor(() => expect(navigation.replace).toHaveBeenCalledWith("/dashboard"));
  expect(request).toHaveBeenLastCalledWith("/api/auth/register/verify", {
    method: "POST", body: JSON.stringify({ email: account.email, code: "123456" }),
  });
});

it("recovers from registration delivery failure and can return to account details", async () => {
  request.mockRejectedValueOnce(new Error("Delivery unavailable"))
    .mockResolvedValueOnce({ delivered: true });
  render(<AuthExperience mode="signup" />);
  const user = await enterDetails();
  await user.click(screen.getByRole("button", { name: "Send verification code" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Delivery unavailable");
  await user.click(screen.getByRole("button", { name: "Send verification code" }));
  await user.click(await screen.findByRole("button", { name: "Back to account details" }));
  expect(screen.getByLabelText("Email")).toHaveValue(account.email);
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  expect(screen.queryByRole("status")).not.toBeInTheDocument();
});

it("requires a valid code and password before submitting password recovery", async () => {
  const navigation = captureNavigation();
  request.mockResolvedValueOnce({ developmentCode: "123456" })
    .mockRejectedValueOnce(new Error("Code expired"))
    .mockResolvedValueOnce(account);
  render(<PasswordResetExperience />);
  const user = userEvent.setup();
  await user.type(screen.getByLabelText("Email"), account.email);
  await user.click(screen.getByRole("button", { name: "Send password code" }));
  expect(await screen.findByText("123456")).toBeInTheDocument();
  const submit = screen.getByRole("button", { name: "Set new password" });
  expect(submit).toBeDisabled();
  fireEvent.change(screen.getByLabelText("Password code"), { target: { value: "1a234567" } });
  expect(submit).toBeDisabled();
  await user.type(screen.getByLabelText(/^New password/), "new-secure-password");
  await user.click(submit);
  expect(await screen.findByRole("alert")).toHaveTextContent("Code expired");
  await user.click(submit);
  await waitFor(() => expect(navigation.replace).toHaveBeenCalledWith("/dashboard"));
  expect(request).toHaveBeenLastCalledWith("/api/auth/password/reset", {
    method: "POST", body: JSON.stringify({ email: account.email, code: "123456", newPassword: "new-secure-password" }),
  });
});

it("allows password-code request retry and changing the recovery email", async () => {
  request.mockRejectedValueOnce("unknown").mockResolvedValueOnce({});
  render(<PasswordResetExperience />);
  const user = userEvent.setup();
  await user.type(screen.getByLabelText("Email"), account.email);
  await user.click(screen.getByRole("button", { name: "Send password code" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Something went wrong.");
  await user.click(screen.getByRole("button", { name: "Send password code" }));
  await user.click(await screen.findByRole("button", { name: "Use a different email" }));
  expect(screen.getByLabelText("Email")).toBeEnabled();
  expect(screen.queryByLabelText("Password code")).not.toBeInTheDocument();
});
