import { http } from './http';

export type ReviewIssueType =
  | 'REQUIREMENT_GAP'
  | 'API_CONTRACT'
  | 'SECURITY'
  | 'PERFORMANCE'
  | 'CONSISTENCY'
  | 'STYLE'
  | 'OTHER';

export type ReviewIssueSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'BLOCKER';
export type ReviewIssueStatus = 'OPEN' | 'RESOLVED' | 'ACCEPTED' | 'REJECTED';

export interface ReviewIssue {
  id: string;
  documentVersionId: string;
  type: ReviewIssueType;
  severity: ReviewIssueSeverity;
  status: ReviewIssueStatus;
  assigneeId: string | null;
  title: string;
  description: string | null;
  createdBy: string;
  createdAt: string;
}

export interface CreateReviewIssuePayload {
  type: ReviewIssueType;
  severity: ReviewIssueSeverity;
  assigneeId?: string | null;
  title: string;
  description?: string | null;
}

export async function listReviewIssues(
  documentId: string,
  versionId: string,
): Promise<ReviewIssue[]> {
  const { data } = await http.get<ReviewIssue[]>(
    `/documents/${documentId}/versions/${versionId}/review-issues`,
  );
  return data;
}

export async function createReviewIssue(
  documentId: string,
  versionId: string,
  payload: CreateReviewIssuePayload,
): Promise<ReviewIssue> {
  const { data } = await http.post<ReviewIssue>(
    `/documents/${documentId}/versions/${versionId}/review-issues`,
    payload,
  );
  return data;
}

export async function updateReviewIssueStatus(
  documentId: string,
  issueId: string,
  status: ReviewIssueStatus,
): Promise<ReviewIssue> {
  const { data } = await http.patch<ReviewIssue>(
    `/documents/${documentId}/review-issues/${issueId}`,
    { status },
  );
  return data;
}
