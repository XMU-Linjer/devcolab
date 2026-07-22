import type { DocumentBlock } from '@/api/block';
import type { GitRepositoryFile } from '@/api/git';

export type WorkbenchMode =
  | 'LINKED'
  | 'CODE_FOCUS'
  | 'DOCUMENT_FOCUS'
  | 'DRIFT_REVIEW';

export type LinkActivationSource =
  | 'code'
  | 'rail'
  | 'document'
  | 'inspector'
  | 'system';

export interface CodeAnchor {
  id: string;
  repositoryId: string;
  branch: string;
  commitSha: string;
  filePath: string;
  language: string;
  symbolName?: string;
  qualifiedSymbol?: string;
  startLine: number;
  endLine: number;
  contentHash?: string;
  status: 'VALID' | 'DRIFTED' | 'BROKEN';
}

export interface CodeDocumentLink {
  id: string;
  codeAnchorId: string;
  blockId: string;
  relationType:
    | 'IMPLEMENTS'
    | 'DESCRIBES'
    | 'TESTS'
    | 'EVIDENCE'
    | 'CONFLICTS_WITH';
}

export interface EngineeringIssue {
  id: string;
  linkId: string;
  title: string;
  description: string;
  severity: 'LOW' | 'MEDIUM' | 'HIGH';
  status: 'OPEN' | 'ACCEPTED' | 'FALSE_POSITIVE' | 'RESOLVED';
  blockId?: string;
  codeAnchorId?: string;
  commitSha?: string;
  sourceType: 'RULE' | 'HUMAN' | 'AI';
  sourceKey?: string;
}

export interface LinkedEvidence {
  id: string;
  linkId: string;
  title: string;
  summary: string;
  kind: 'COMMIT' | 'TEST' | 'REVIEW' | 'RULE';
  commitSha?: string;
}

export type LinkedDocumentBlock = DocumentBlock;

export interface LinkedFixture {
  codeAnchors: CodeAnchor[];
  links: CodeDocumentLink[];
  issues: EngineeringIssue[];
  evidence: LinkedEvidence[];
}

export interface LinkedFileTreeNode {
  key: string;
  label: string;
  children?: LinkedFileTreeNode[];
  file?: GitRepositoryFile;
}

export interface LinkedDocumentChoice {
  id: string;
  title: string;
  depth: number;
  version?: number;
  reviewStatus?: string;
}
