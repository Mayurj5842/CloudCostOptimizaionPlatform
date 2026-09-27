export type CurrentUserProfile = {
  name: string | null;
  email: string | null;
  username: string | null;
  role: string | null;
  account: string | null;
};

export function getCurrentUserProfile(user: { username: string; email: string } | null): CurrentUserProfile {
  return {
    name: user?.username ?? null,
    email: user?.email ?? null,
    username: user?.username ?? null,
    role: null,
    account: null,
  };
}
