export type RepositoryStatus = 'REGISTERED' | 'INDEXING' | 'READY' | 'FAILED'

export type Repository = {
  id: string
  displayName: string
  relativePath: string
  defaultBranch: string | null
  headSha: string | null
  dirty: boolean
  buildSystem: string
  status: RepositoryStatus
  activeIndexRunId: string | null
  createdAt: string
  lastIndexedAt: string | null
  sourceFileCount: number
}

export type IndexRun = {
  id: string
  repositoryId: string
  mode: 'FULL' | 'INCREMENTAL'
  status: 'QUEUED' | 'RUNNING' | 'COMPLETE' | 'FAILED'
  phase: string
  filesDiscovered: number
  filesProcessed: number
  warningsCount: number
  symbolsCreated: number
  endpointsCreated: number
  errorSummary: string | null
}

export type HttpEndpoint = {
  id: string
  httpMethod: string
  path: string
  controllerMethodId: string
  controller: string
  method: string
  signature: string
  sourcePath: string
  startLine: number
  endLine: number
  requestType: string | null
  responseType: string | null
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init?.headers },
  })
  if (!response.ok) {
    const problem = (await response.json().catch(() => null)) as { detail?: string } | null
    throw new Error(problem?.detail ?? `Request failed with ${response.status}`)
  }
  return response.status === 204 ? (undefined as T) : (response.json() as Promise<T>)
}

export const repositoryApi = {
  list: () => request<Repository[]>('/api/repositories'),
  create: (displayName: string, relativePath: string) =>
    request<Repository>('/api/repositories', {
      method: 'POST',
      body: JSON.stringify({ displayName, relativePath }),
    }),
  remove: (id: string) =>
    request<void>(`/api/repositories/${id}`, { method: 'DELETE' }),
  index: (id: string) =>
    request<IndexRun>(`/api/repositories/${id}/index`, { method: 'POST' }),
  run: (id: string) => request<IndexRun>(`/api/index-runs/${id}`),
  endpoints: (id: string) =>
    request<HttpEndpoint[]>(`/api/repositories/${id}/http-endpoints`),
}
