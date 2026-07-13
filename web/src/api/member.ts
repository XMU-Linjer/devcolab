import { http } from './http';
import type { WorkspaceRole } from './workspace';

export interface WorkspaceMember {
  userId: string;
  username: string;
  displayName: string;
  role: WorkspaceRole;
  joinedAt: string;
}

export interface InviteWorkspaceMemberPayload {
  username: string;
  role: WorkspaceRole;
}

export interface UpdateWorkspaceMemberRolePayload {
  role: WorkspaceRole;
}

export async function listWorkspaceMembers(
  workspaceId: string,
): Promise<WorkspaceMember[]> {
  const { data } = await http.get<WorkspaceMember[]>(
    `/workspaces/${workspaceId}/members`,
  );
  return data;
}

export async function inviteWorkspaceMember(
  workspaceId: string,
  payload: InviteWorkspaceMemberPayload,
): Promise<WorkspaceMember> {
  const { data } = await http.post<WorkspaceMember>(
    `/workspaces/${workspaceId}/members/invitations`,
    payload,
  );
  return data;
}

export async function updateWorkspaceMemberRole(
  workspaceId: string,
  memberUserId: string,
  payload: UpdateWorkspaceMemberRolePayload,
): Promise<WorkspaceMember> {
  const { data } = await http.patch<WorkspaceMember>(
    `/workspaces/${workspaceId}/members/${memberUserId}/role`,
    payload,
  );
  return data;
}

export async function removeWorkspaceMember(
  workspaceId: string,
  memberUserId: string,
): Promise<void> {
  await http.delete(`/workspaces/${workspaceId}/members/${memberUserId}`);
}
