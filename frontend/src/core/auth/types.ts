export type UserRole = "ADMINISTRADOR" | "DOCENTE" | "ESTUDIANTE" | "APODERADO";

export type AuthenticatedUser = {
  id: string;
  username: string;
  fullName: string;
  role: UserRole;
  permissions?: string[];
  requiresPasswordChange?: boolean;
};

export type SessionUser = Omit<AuthenticatedUser, "role"> & {
  role: UserRole | null;
  availableRoles: UserRole[];
  sessionId: string;
  contextVersion: number;
};

export type AuthSession = {
  accessToken: string;
  expiresAt?: string;
  user: SessionUser;
};

export type LoginCredentials = {
  username: string;
  password: string;
};

export type LoginResult =
  | { ok: true; session: AuthSession }
  | { ok: false; message: string };

export interface AuthService {
  login(credentials: LoginCredentials): Promise<LoginResult>;
  switchRole(role: UserRole): Promise<AuthSession>;
  logout(): Promise<void>;
  /** Sesión nueva desde la cookie de renovación; null si no hay sesión que renovar. */
  refresh(): Promise<AuthSession | null>;
}
