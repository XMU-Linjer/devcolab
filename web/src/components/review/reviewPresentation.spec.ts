import { describe, expect, it } from 'vitest';

import type {
  DocumentChangeEvidence,
  DocumentChangeOperation,
} from '@/api/documentChange';
import {
  evidenceRangeLabel,
  operationDocumentTitle,
  selectedEvidence,
} from './reviewPresentation';

function evidence(id: string): DocumentChangeEvidence {
  return {
    id,
    repository: { id: 'r1', name: 'repo' },
    filePath: 'src/App.java',
    commitHash: 'abc',
    startLine: 10,
    endLine: 12,
    description: 'proof',
    excerptText: 'line',
  };
}

function operation(operationEvidence: DocumentChangeEvidence[]): DocumentChangeOperation {
  return {
    operationId: 'o1',
    clientOperationId: 'client-o1',
    sequenceNumber: 1,
    operationType: 'UPDATE_BLOCK',
    target: {
      documentId: 'd1',
      documentTitle: 'API Design',
      blockId: 'b1',
      blockType: 'PARAGRAPH',
    },
    baseSnapshot: {
      blockVersion: 2,
      blockType: 'PARAGRAPH',
      plainText: 'old',
      content: null,
      sortOrder: 0,
    },
    proposal: {
      documentTitle: null,
      documentType: null,
      parentDocumentId: null,
      blockType: 'PARAGRAPH',
      plainText: 'new',
      content: null,
    },
    currentBlockVersion: 2,
    conflict: {
      conflicted: false,
      reason: null,
      expectedVersion: 2,
      actualVersion: 2,
    },
    evidence: operationEvidence,
  };
}

describe('review presentation state', () => {
  it('prefers the route-selected evidence across operation and request scopes', () => {
    const operationEvidence = evidence('operation-evidence');
    const requestEvidence = evidence('request-evidence');
    expect(selectedEvidence(
      operation([operationEvidence]),
      [requestEvidence],
      'request-evidence',
    )).toBe(requestEvidence);
  });

  it('falls back deterministically to operation evidence then request evidence', () => {
    const operationEvidence = evidence('operation-evidence');
    const requestEvidence = evidence('request-evidence');
    expect(selectedEvidence(operation([operationEvidence]), [requestEvidence]))
      .toBe(operationEvidence);
    expect(selectedEvidence(operation([]), [requestEvidence])).toBe(requestEvidence);
  });

  it('keeps labels derived from real response fields', () => {
    const item = evidence('e1');
    expect(evidenceRangeLabel(item)).toBe('L10–12');
    expect(operationDocumentTitle(operation([item]))).toBe('API Design');
  });
});
