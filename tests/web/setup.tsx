import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach, vi } from "vitest";
import type { AnchorHTMLAttributes } from "react";

vi.mock("next/link", () => ({
  default: (props: AnchorHTMLAttributes<HTMLAnchorElement>) => <a {...props} />,
}));

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});
