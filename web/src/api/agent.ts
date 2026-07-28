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

export type AgentJobStatus = 'QUEUED' | 'RUNNING' | 'READY_FOR_ANALYSIS' | 'COMPLETED' | 'PARTIALLY_COMPLETED' | 'FAILED' | 'CANCELLED';
export type AgentJobPhase =
  | 'LOADING_CONTEXT'
  | 'MODEL_RUNNING'
  | 'VALIDATING'
  | 'REPAIRING'
  | 'SUBMITTING_REVIEW'
  | 'DISCOVERING_FILES'
  | 'CLASSIFYING_FILES'
  | 'LOADING_CODE_METADATA'
  | 'LOADING_BINDINGS'
  | 'BUILDING_SEMANTIC_GRAPH'
  | 'BUILDING_ANALYSIS_UNITS'
  | 'READY_FOR_ANALYSIS'
  | 'PLANNING_UNITS'
  | 'VALIDATING_UNIT_PLAN'
  | 'EXECUTING_UNITS'
  | 'COMPLETED';

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

export interface CreateAgentJobPayload {
  workspaceId: string;
  repositoryId: string;
  scope:
    | { type: 'CURRENT_FILE'; filePath: string }
    | { type: 'PROJECT_INITIALIZATION' };
  userInstruction: string | null;
}

export interface QueuedAgentJob {
  jobId: string;
  status: 'QUEUED';
  createdAt: string;
}

export interface AgentJob {
  jobId: string;
  scopeType: 'CURRENT_FILE' | 'PROJECT_INITIALIZATION';
  scopePayload:
    | { type: 'CURRENT_FILE'; filePath: string }
    | { type: 'PROJECT_INITIALIZATION' };
  status: AgentJobStatus;
  result: 'NO_CHANGE' | 'REVIEW_SUBMITTED' | 'PARTIALLY_COMPLETED' | null;
  phase: AgentJobPhase | null;
  revision: string;
  totalUnits: number;
  completedUnits: number;
  failedUnits: number;
  reviewRequestIds: string[];
  errorCode: string | null;
  errorMessage: string | null;
  createdAt: string;
  startedAt: string | null;
  completedAt: string | null;
  updatedAt: string;
  discoveredFileCount: number;
  supportedCodeCount: number;
  skippedFileCount: number;
  skippedReasonCounts: Record<string, number>;
  metadataParsedCount: number;
  metadataFailedCount: number;
  boundFileCount: number;
  unboundFileCount: number;
  analysisUnitCount: number;
  overlappingFileCount: number;
  plannerStatus: string | null;
  plannedUnitCount: number;
  pendingUnitCount: number;
  runningUnitCount: number;
  completedUnitCount: number;
  failedUnitCount: number;
  noChangeUnitCount: number;
  reviewSubmittedUnitCount: number;
  currentPhase: string | null;
  currentUnitNames: string[];
}

export interface AgentSemanticUnit {
  unitId: string;
  semanticKey: string;
  displayName: string;
  semanticKind: string;
  status: 'PENDING' | 'CLAIMED' | 'RUNNING' | 'RETRY_WAITING' | 'READY_FOR_ANALYSIS' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
  primaryDirectory: string;
  primaryFiles: string[];
  supportingFiles: string[];
  boundDocumentIds: string[];
  boundDocuments: Array<{
    documentId: string;
    relationship: string;
    source: string;
    ordinal: number;
  }>;
  languageSet: string[];
  estimatedSizeBytes: number;
  groupingReasons: string[];
  unitFingerprint: string;
}

export interface AgentJobUnitsPage {
  jobId: string;
  offset: number;
  limit: number;
  total: number;
  units: AgentSemanticUnit[];
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

export async function createAgentJob(payload: CreateAgentJobPayload) {
  const { data } = await agentHttp.post<QueuedAgentJob>('/agent-jobs', payload);
  return data;
}

export async function getAgentJob(jobId: string) {
  const { data } = await agentHttp.get<AgentJob>(`/agent-jobs/${jobId}`);
  return data;
}

export async function listAgentJobUnits(jobId: string, offset = 0, limit = 20) {
  const { data } = await agentHttp.get<AgentJobUnitsPage>(
    `/agent-jobs/${jobId}/units`,
    { params: { offset, limit } },
  );
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
