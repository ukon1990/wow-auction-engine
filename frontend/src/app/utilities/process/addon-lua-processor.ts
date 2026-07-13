import {
  LuaAssignments,
  LuaProcessingError,
  processLuaAssignments,
} from './lua-assignment-processor';

export type ProcessingDiagnostic = Readonly<{
  code: string;
  detail: string;
  fileName: string;
  recipeId?: number;
}>;

export type AddonLuaResult<T> = Readonly<{
  adapterId: string;
  data: T;
  diagnostics: ProcessingDiagnostic[];
  fileName: string;
}>;

export interface AddonLuaAdapter<T = unknown> {
  readonly id: string;
  readonly fileNames: readonly string[];
  readonly assignmentNames: readonly string[];
  canProcess(fileName: string, assignments: LuaAssignments): boolean;
  normalize(fileName: string, assignments: LuaAssignments): AddonLuaResult<T>;
}

export class AddonLuaProcessor {
  constructor(private readonly adapters: readonly AddonLuaAdapter[]) {}

  process<T = unknown>(fileName: string, source: string): AddonLuaResult<T> {
    const candidates = this.adapters.filter((candidate) =>
      candidate.fileNames.some((name) => name.toLowerCase() === fileName.toLowerCase()),
    );
    const assignments = processLuaAssignments(
      source,
      undefined,
      new Set(candidates.flatMap((candidate) => candidate.assignmentNames)),
    );
    const adapter = candidates.find((candidate) => candidate.canProcess(fileName, assignments));
    if (!adapter) {
      throw new LuaProcessingError(
        'UNSUPPORTED_LUA',
        `No registered addon adapter supports ${fileName}.`,
      );
    }
    return adapter.normalize(fileName, assignments) as AddonLuaResult<T>;
  }
}
