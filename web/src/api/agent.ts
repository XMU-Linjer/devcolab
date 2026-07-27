import axios from 'axios';

import { createAuthenticatedHttp } from './http';

export type AgentRunStatus =
  | 'QUEUED'
  | 'BUILDING_CONTEXT'
  | 'PLANNING'
  | 'VALIDATING'
  | 'REPAIRING_PLAN'
  | 'SUBMITTING_REVIEW'
  | 'REVIEW_SUBMITTED'
  | 'NO_CHANGE'
  | 'FAILED';

export interface CreateAgentRunPayload {
  workspaceId: string;
  repositoryId: string;
  selectedPaths: string[];
  userInstruction: string | null;
}

export interface QueuedAgentRun {
  runId: string;
  status: 'QUEUED';
}

export interface AgentRun {
  runId: string;
  status: AgentRunStatus;
  workspaceId: string;
  repositoryId: string;
  selectedPaths: string[];
  currentNode: string;
  decision: 'NO_CHANGE' | 'SUBMIT_REVIEW' | null;
  summary: string | null;
  changeRequestId: string | null;
  errorCode: string | null;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
}

interface AgentErrorBody {
  code?: string;
  message?: string;
  detail?: {
    code?: string;
    message?: string;
  };
}

const agentHttp = createAuthenticatedHttp('/agent-api/api/v1');

export async function createAgentRun(payload: CreateAgentRunPayload) {
  const { data } = await agentHttp.post<QueuedAgentRun>('/agent-runs', payload);
  return data;
}

export async function getAgentRun(runId: string) {
  const { data } = await agentHttp.get<AgentRun>(`/agent-runs/${runId}`);
  return data;
}

export function readableAgentError(error: unknown, fallback: string) {
  if (!axios.isAxiosError<AgentErrorBody>(error)) {
    return error instanceof Error ? error.message : fallback;
  }

  return error.response?.data?.detail?.message
    || error.response?.data?.message
    || fallback;
}
