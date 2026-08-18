export type ApiProblem = {
  code?: string;
  message?: string;
  fields?: Record<string, string>;
};

export type Room = {
  id: string;
  accessCode: string;
  plan: "GUEST" | "FREE" | "PREMIUM";
  visibility: "PUBLIC" | "PRIVATE";
  passwordProtected: boolean;
  accessLimit: number;
  accessCount: number;
  remainingEntries: number;
  clipboardText: string;
  fileBytes: number;
  fileLimitBytes: number;
  clipboardLimit: number;
  createdAt: string;
  expiresAt: string;
  owner: boolean;
  version: number;
};

export type RoomFile = {
  id: string;
  name: string;
  contentType: string;
  sizeBytes: number;
  uploadedAt: string;
};

export type Account = {
  id: string;
  email: string;
  plan: "FREE" | "PREMIUM";
  activeRoomLimit: number;
  roomMinutes: number;
  clipboardCharacters: number;
  roomFileBytes: number;
  billingProfileAvailable: boolean;
};

const configuredBase = process.env.NEXT_PUBLIC_API_BASE_URL?.replace(/\/$/, "");
export const API_BASE = configuredBase ?? "";

export async function api<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const headers = new Headers(init.headers);
  if (init.body && !(init.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }
  headers.set("X-Requested-With", "MySendWeb");

  let response: Response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      ...init,
      credentials: "include",
      headers,
    });
  } catch {
    throw new Error(
      "The MySend service is not connected yet. Check the API address and try again.",
    );
  }

  if (!response.ok) {
    const problem = (await response.json().catch(() => ({}))) as ApiProblem;
    throw new Error(problem.message || `Request failed (${response.status})`);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

export function roomDownloadUrl(code: string, fileId: string) {
  return `${API_BASE}/api/rooms/${encodeURIComponent(code)}/files/${encodeURIComponent(fileId)}`;
}
