import type { CodeAnchor, CodeDocumentLink } from '@/types/linkedWorkbench';

export type CodeRangePosition = 'single' | 'start' | 'middle' | 'end';

export interface CodeRangeOverlay {
  key: string;
  anchorId: string;
  startLine: number;
  endLine: number;
  active: boolean;
  drifted: boolean;
}

export interface CodeLineRangeState {
  linked: boolean;
  active: boolean;
  drifted: boolean;
  position: CodeRangePosition;
  anchorId: string;
}

export interface CodeRangePresentation {
  overlays: CodeRangeOverlay[];
  lineStates: ReadonlyMap<number, CodeLineRangeState>;
  validAnchorIds: ReadonlySet<string>;
}

export function buildCodeRangePresentation(input: {
  lineCount: number;
  filePath: string;
  anchors: CodeAnchor[];
  links: CodeDocumentLink[];
  activeLinkId: string | null;
}): CodeRangePresentation {
  const linksByAnchor = new Map<string, CodeDocumentLink[]>();
  input.links.forEach((link) => {
    const current = linksByAnchor.get(link.codeAnchorId) ?? [];
    current.push(link);
    linksByAnchor.set(link.codeAnchorId, current);
  });

  const overlays = input.anchors
    .filter(anchor => isValidPreciseRange(anchor, input.filePath, input.lineCount))
    .flatMap((anchor): CodeRangeOverlay[] => {
      const anchorLinks = linksByAnchor.get(anchor.id) ?? [];
      if (anchorLinks.length === 0) return [];
      return [{
        key: anchor.bindingId ?? anchor.id,
        anchorId: anchor.id,
        startLine: anchor.startLine!,
        endLine: anchor.endLine!,
        active: anchorLinks.some(link => link.id === input.activeLinkId),
        drifted: anchor.status !== 'VALID',
      }];
    })
    .sort(compareOverlays);

  const lineStates = new Map<number, CodeLineRangeState>();
  for (let line = 1; line <= input.lineCount; line += 1) {
    const candidates = overlays.filter(range => line >= range.startLine && line <= range.endLine);
    if (candidates.length === 0) continue;
    const selected = candidates.sort(compareLineCandidates)[0]!;
    lineStates.set(line, {
      linked: true,
      active: selected.active,
      drifted: candidates.some(range => range.drifted),
      position: positionInRange(line, selected.startLine, selected.endLine),
      anchorId: selected.anchorId,
    });
  }

  return {
    overlays,
    lineStates,
    validAnchorIds: new Set(overlays.map(range => range.anchorId)),
  };
}

function isValidPreciseRange(anchor: CodeAnchor, filePath: string, lineCount: number) {
  if (anchor.filePath !== filePath || anchor.anchorKind === 'FILE') return false;
  if (anchor.startLine === null || anchor.endLine === null) return false;
  return Number.isInteger(anchor.startLine)
    && Number.isInteger(anchor.endLine)
    && anchor.startLine >= 1
    && anchor.endLine >= anchor.startLine
    && anchor.endLine <= lineCount;
}

function positionInRange(
  line: number,
  startLine: number,
  endLine: number,
): CodeRangePosition {
  if (startLine === endLine) return 'single';
  if (line === startLine) return 'start';
  if (line === endLine) return 'end';
  return 'middle';
}

function compareOverlays(left: CodeRangeOverlay, right: CodeRangeOverlay) {
  return left.startLine - right.startLine
    || left.endLine - right.endLine
    || left.key.localeCompare(right.key);
}

function compareLineCandidates(left: CodeRangeOverlay, right: CodeRangeOverlay) {
  if (left.active !== right.active) return left.active ? -1 : 1;
  return compareOverlays(left, right);
}
