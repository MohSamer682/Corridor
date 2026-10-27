export type FieldViolation = { field: string; message: string };

type ProblemBody = { code?: string; detail?: string; errors?: FieldViolation[] };

export class ProblemError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly detail?: string;
  readonly errors?: FieldViolation[];

  constructor(status: number, body: ProblemBody) {
    super(body.detail ?? `Request failed with status ${status}`);
    this.name = 'ProblemError';
    this.status = status;
    this.code = body.code;
    this.detail = body.detail;
    this.errors = body.errors;
  }

  static async from(response: Response): Promise<ProblemError> {
    const body = (await response.json().catch(() => ({}))) as ProblemBody;
    return new ProblemError(response.status, body);
  }
}
