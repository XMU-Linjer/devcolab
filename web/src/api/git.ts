import { http } from './http';

export type GitProvider = 'GITHUB' | 'GITLAB' | 'GITEE' | 'GENERIC';
export type GitChangeType = 'COMMIT' | 'PULL_REQUEST';
export type GitFileChangeType = 'ADDED' | 'MODIFIED' | 'DELETED' | 'RENAMED';

export interface GitRepository {
  id: string;
  workspaceId: string;
  name: string;
  provider: GitProvider;
  remoteUrl: string;
  defaultBranch: string;
  createdAt: string;
  syncStatus: 'REGISTERED' | 'SYNC_PENDING' | 'SYNCING' | 'READY' | 'FAILED';
  lastSyncedCommit: string | null;
  lastSyncedAt: string | null;
  lastSyncError: string | null;
}

export interface GitRepositoryFile {
  id: string;
  path: string;
  blobSha: string;
  sizeBytes: number;
  language: string | null;
}

export interface GitFileDiff {
  id: string;
  path: string;
  oldPath: string | null;
  changeType: GitFileChangeType;
  additions: number;
  deletions: number;
  binaryFile: boolean;
  patchExcerpt: string | null;
}

export interface GitChange {
  id: string;
  repositoryId: string;
  changeType: GitChangeType;
  externalId: string;
  title: string;
  commitSha: string;
  baseRef: string | null;
  headRef: string | null;
  authorName: string | null;
  authorEmail: string | null;
  authoredAt: string | null;
  committerName: string | null;
  committerEmail: string | null;
  parentCommitSha: string | null;
  webUrl: string | null;
  occurredAt: string;
  duplicate: boolean;
  files: GitFileDiff[];
}

export interface CodeDocumentBinding {
  id: string;
  workspaceId: string;
  repositoryId: string;
  documentId: string;
  blockId: string | null;
  pathPattern: string;
  createdAt: string;
}

export async function listGitRepositories(workspaceId: string) {
  const { data } = await http.get<GitRepository[]>(
    `/workspaces/${workspaceId}/git/repositories`,
  );
  return data;
}

export async function registerGitRepository(
  workspaceId: string,
  payload: {
    name: string;
    provider: GitProvider;
    remoteUrl: string;
    defaultBranch: string;
  },
) {
  const { data } = await http.post<GitRepository>(
    `/workspaces/${workspaceId}/git/repositories`,
    payload,
  );
  return data;
}

export async function listGitChanges(workspaceId: string, repositoryId: string) {
  const { data } = await http.get<GitChange[]>(
    `/workspaces/${workspaceId}/git/repositories/${repositoryId}/changes`,
  );
  return data;
}

export async function syncGitRepository(workspaceId: string, repositoryId: string) {
  const { data } = await http.post<GitRepository>(
    `/workspaces/${workspaceId}/git/repositories/${repositoryId}/sync`,
  );
  return data;
}

export async function deleteGitRepository(workspaceId: string, repositoryId: string) {
  await http.delete(`/workspaces/${workspaceId}/git/repositories/${repositoryId}`);
}

export async function listGitRepositoryFiles(workspaceId: string, repositoryId: string) {
  const { data } = await http.get<GitRepositoryFile[]>(
    `/workspaces/${workspaceId}/git/repositories/${repositoryId}/files`,
  );
  return data;
}

export async function ingestGitChange(
  workspaceId: string,
  repositoryId: string,
  payload: {
    changeType: GitChangeType;
    externalId: string;
    title: string;
    commitSha: string;
    baseRef?: string | null;
    headRef?: string | null;
    authorName?: string | null;
    authorEmail?: string | null;
    authoredAt?: string | null;
    committerName?: string | null;
    committerEmail?: string | null;
    parentCommitSha?: string | null;
    webUrl?: string | null;
    occurredAt: string;
    files: Array<{
      path: string;
      oldPath?: string | null;
      changeType: GitFileChangeType;
      additions: number;
      deletions: number;
      binaryFile?: boolean;
      patchExcerpt?: string | null;
    }>;
  },
) {
  const { data } = await http.post<GitChange>(
    `/workspaces/${workspaceId}/git/repositories/${repositoryId}/changes`,
    payload,
  );
  return data;
}

export async function listCodeBindings(documentId: string) {
  const { data } = await http.get<CodeDocumentBinding[]>(
    `/documents/${documentId}/code-bindings`,
  );
  return data;
}

export async function createCodeBinding(
  documentId: string,
  payload: { repositoryId: string; blockId?: string | null; pathPattern: string },
) {
  const { data } = await http.post<CodeDocumentBinding>(
    `/documents/${documentId}/code-bindings`,
    payload,
  );
  return data;
}

export async function deleteCodeBinding(bindingId: string) {
  await http.delete(`/code-bindings/${bindingId}`);
}
