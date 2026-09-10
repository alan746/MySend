import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { SiteHeader } from "../../app/ui/SiteHeader";
import { account, captureNavigation } from "./fixtures";

const request = vi.hoisted(() => vi.fn());
vi.mock("../../app/lib/api", () => ({ api: request }));
vi.mock("next/navigation", () => ({ usePathname: () => "/settings" }));
beforeEach(() => { request.mockReset(); });

it("offers guest navigation when no account is available", async () => {
  request.mockRejectedValue(new Error("No session"));
  render(<SiteHeader />);
  expect(await screen.findByRole("link", { name: "Sign up" })).toHaveAttribute("href", "/signup");
  expect(screen.getByRole("link", { name: "Log in" })).toHaveAttribute("href", "/login");
  expect(screen.queryByRole("button", { name: "Log out" })).not.toBeInTheDocument();
});

it("marks the current account route and retries a failed logout", async () => {
  const navigation = captureNavigation();
  request.mockResolvedValueOnce(account).mockRejectedValueOnce(new Error("Offline")).mockResolvedValueOnce(undefined);
  render(<SiteHeader compact />);
  const user = userEvent.setup();
  const logout = await screen.findByRole("button", { name: "Log out" });
  expect(screen.getByRole("link", { name: "Settings" })).toHaveAttribute("aria-current", "page");
  await user.click(logout);
  expect(await screen.findByRole("button", { name: "Log out" })).toBeEnabled();
  expect(navigation.replace).not.toHaveBeenCalled();
  await user.click(logout);
  await waitFor(() => expect(navigation.replace).toHaveBeenCalledWith("/"));
  expect(request).toHaveBeenLastCalledWith("/api/auth/logout", { method: "POST" });
});
