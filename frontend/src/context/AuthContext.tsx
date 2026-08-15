import { createContext, useCallback, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import {
  fetchProfile,
  loginUser,
  logoutUser,
  registerUser,
  updateProfile as updateProfileRequest,
  uploadAvatar as uploadAvatarRequest,
} from '../api/auth';
import type { LoginPayload, RegisterPayload, UpdateProfilePayload, UserProfile } from '../types/user';

export interface AuthContextValue {
  user: UserProfile | null;
  loading: boolean;
  login: (payload: LoginPayload) => Promise<void>;
  register: (payload: RegisterPayload) => Promise<void>;
  logout: () => Promise<void>;
  refreshProfile: (payload: UpdateProfilePayload) => Promise<void>;
  updateAvatar: (file: File) => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined);

/**
 * Holds the logged-in user, if any. The JWT itself lives only in an httpOnly cookie that this
 * code never reads — login state is determined purely by whether `GET /api/users/me` succeeds.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;

    fetchProfile()
      .then((profile) => {
        if (!cancelled) setUser(profile);
      })
      .catch(() => {
        // No valid session cookie (or the backend is unreachable): treat as logged out.
        if (!cancelled) setUser(null);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback(async (payload: LoginPayload) => {
    setUser(await loginUser(payload));
  }, []);

  const register = useCallback(async (payload: RegisterPayload) => {
    setUser(await registerUser(payload));
  }, []);

  const logout = useCallback(async () => {
    await logoutUser();
    setUser(null);
  }, []);

  const refreshProfile = useCallback(async (payload: UpdateProfilePayload) => {
    setUser(await updateProfileRequest(payload));
  }, []);

  const updateAvatar = useCallback(async (file: File) => {
    setUser(await uploadAvatarRequest(file));
  }, []);

  return (
    <AuthContext.Provider value={{ user, loading, login, register, logout, refreshProfile, updateAvatar }}>
      {children}
    </AuthContext.Provider>
  );
}
