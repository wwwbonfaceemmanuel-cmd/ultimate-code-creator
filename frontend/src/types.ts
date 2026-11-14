export interface ProjectFile {
  path: string;
  content: string;
}

export interface ProjectSummary {
  id: string;
  name: string;
  description: string;
  language: string;
  file_count: number;
  created_at: string;
  updated_at: string;
}

export interface Project extends ProjectSummary {
  prompt: string;
  files: ProjectFile[];
}

export interface Issue {
  path: string;
  line: number;
  column: number;
  severity: 'error' | 'warning';
  message: string;
}

export interface ValidateResult {
  issues: Issue[];
  files_checked: number;
  note: string;
}

export interface EditResult {
  summary: string;
  files: ProjectFile[];
  changed: string[];
  deleted: string[];
}

export interface SystemStatus {
  ai: { configured: boolean; model: string | null; missing: string[] };
  auth: { enabled: boolean; required: boolean; supabase_url: string | null; supabase_anon_key: string | null };
  limits: { per_hour: number; per_day: number };
  storage: { kind: 'local' | 'supabase'; ok: boolean; error: string | null };
  languages: { id: string; label: string }[];
}

export interface Usage {
  hour: { used: number; limit: number };
  day: { used: number; limit: number };
}
