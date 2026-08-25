export type UserRole = 'USER' | 'ADMIN';

export interface UserProfile {
  id: number;
  username: string;
  email: string;
  displayName: string | null;
  avatarUrl: string | null;
  role: UserRole;
  createdAt: string;
  preferredGenres: string[];
}

export interface RegisterPayload {
  fullName: string;
  email: string;
  password: string;
  genres: string[];
}

export interface LoginPayload {
  identifier: string;
  password: string;
}

export interface UpdateProfilePayload {
  displayName: string;
}
