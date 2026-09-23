export type CurrentUserProfile = {
  name: string | null;
  email: string | null;
  username: string | null;
  role: string | null;
  account: string | null;
};

const fallbackProfile: CurrentUserProfile = {
  name: "Mayuresh Jadhav",
  email: null,
  username: null,
  role: "CloudCost Admin",
  account: "CloudCost AI Workspace",
};

export function getCurrentUserProfile(): CurrentUserProfile {
  if (typeof window === "undefined") {
    return fallbackProfile;
  }

  const stored = window.localStorage.getItem("cloudcost-current-user");

  if (!stored) {
    return fallbackProfile;
  }

  try {
    const parsed = JSON.parse(stored) as Partial<CurrentUserProfile>;
    return {
      ...fallbackProfile,
      ...parsed,
    };
  } catch {
    return fallbackProfile;
  }
}
