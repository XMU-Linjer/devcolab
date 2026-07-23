import { http } from './http';

export type WorkspaceRole = 'ADMIN' | 'MEMBER';

export interface Workspace {
  id: string;
  name: string;
  currentUserRole: WorkspaceRole;
  createdAt: string;
  updatedAt: string;
}

export interface CreateWorkspacePayload {
  name: string;
}

export interface RenameWorkspacePayload {
  name: string;
}

export async function listWorkspaces(): Promise<Workspace[]> {
  const { data } = await http.get<Workspace[]>('/workspaces');
  return data;
}

export async function createWorkspace(
  payload: CreateWorkspacePayload,
): Promise<Workspace> {
  const { data } = await http.post<Workspace>('/workspaces', payload);
  return data;
}

export async function getWorkspace(workspaceId: string): Promise<Workspace> {
  const { data } = await http.get<Workspace>(`/workspaces/${workspaceId}`);
  return data;
}

export async function renameWorkspace(
  workspaceId: string,
  payload: RenameWorkspacePayload,
): Promise<Workspace> {
  const { data } = await http.patch<Workspace>(
    `/workspaces/${workspaceId}`,
    payload,
  );
  return data;
}

export async function deleteWorkspace(workspaceId: string): Promise<void> {
  await http.delete(`/workspaces/${workspaceId}`);
}
