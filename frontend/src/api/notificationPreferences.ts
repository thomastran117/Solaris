import api from "../api";
import type { NotificationPreferences } from "../types/notificationPreferences";

export const notificationPreferencesApi = {
  getPreferences: () =>
    api.get<NotificationPreferences>("/users/me/preferences/notifications"),

  updatePreferences: (data: Partial<NotificationPreferences>) =>
    api.patch<NotificationPreferences>("/users/me/preferences/notifications", data),

  registerPushToken: (platform: string, token: string) =>
    api.post<void>("/devices/push-token", { platform, token }),

  revokePushToken: (token: string) =>
    api.delete<void>(`/devices/push-token/${encodeURIComponent(token)}`),
};
