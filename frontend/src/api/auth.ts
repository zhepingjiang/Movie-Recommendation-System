import type { LoginPayload, RegisterPayload, UpdateProfilePayload, UserProfile } from '../types/user';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

async function authFetch(path: string, options: RequestInit = {}): Promise<Response> {
  const res = await fetch(`${API_BASE_URL}${path}`, { ...options, credentials: 'include' });
  if (!res.ok) {
    throw new Error(`Request to ${path} failed with status ${res.status}`);
  }
  return res;
}

export async function registerUser(payload: RegisterPayload): Promise<UserProfile> {
  const res = await authFetch('/api/auth/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  return res.json();
}

export async function loginUser(payload: LoginPayload): Promise<UserProfile> {
  const res = await authFetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  return res.json();
}

export async function logoutUser(): Promise<void> {
  await authFetch('/api/auth/logout', { method: 'POST' });
}

export async function fetchProfile(): Promise<UserProfile> {
  const res = await authFetch('/api/users/me');
  return res.json();
}

export async function updateProfile(payload: UpdateProfilePayload): Promise<UserProfile> {
  const res = await authFetch('/api/users/me', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  return res.json();
}

export async function uploadAvatar(file: File): Promise<UserProfile> {
  const formData = new FormData();
  formData.append('file', file);
  const res = await authFetch('/api/users/me/avatar', { method: 'POST', body: formData });
  return res.json();
}
