import { http } from './http';

export interface NotificationItem {
  id: string;
  workspaceId: string;
  documentId: string | null;
  type: string;
  title: string;
  content: string | null;
  unread: boolean;
  readAt: string | null;
  createdAt: string;
}

export async function listNotifications(params?: {
  unreadOnly?: boolean;
  limit?: number;
}): Promise<NotificationItem[]> {
  const { data } = await http.get<NotificationItem[]>('/notifications', {
    params,
  });
  return data;
}

export async function markNotificationRead(
  notificationId: string,
): Promise<NotificationItem> {
  const { data } = await http.patch<NotificationItem>(
    `/notifications/${notificationId}/read`,
  );
  return data;
}
