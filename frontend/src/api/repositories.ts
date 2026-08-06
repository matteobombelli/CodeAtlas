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
  edgesCreated: number
  filesAdded: number
  filesModified: number
  filesDeleted: number
  startedAt: string
  completedAt: string | null
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

export type GraphNode = {
  id: string
  resourceType: 'ENDPOINT' | 'SYMBOL' | 'FILE' | 'EXTERNAL'
  kind: string
  label: string
  subtitle: string
  source: { path: string; startLine: number; endLine: number }
  roles: string[]
}

export type GraphEdge = {
  id: string
  source: string
  target: string
  kind: string
  confidence: number
  confidenceLabel: string
  resolutionMethod: string
  evidence: { path: string; line: number; column: number; text: string }
}

export type ExecutionGraph = {
  rootNodeId: string
  nodes: GraphNode[]
  edges: GraphEdge[]
  warnings: { type: string; message: string }[]
  truncated: boolean
  truncationReason: string | null
}

export type SourceExcerpt = {
  path: string
  startLine: number
  endLine: number
  language: string
  content: string
  contentHash: string
}

export type GitFileHistory = {
  totalCommits: number
  commitsLast90Days: number
  lastModifiedAt: string | null
  lastAuthorName: string | null
  lastCommitSha: string | null
  contributorCount: number
  recentSubjects: string[]
}

export type CodeSearchResult = {
  id: string
  label: string
  detail: string
  sourcePath: string
  startLine: number
  endLine: number
  httpMethod: string | null
}

export type CodeSearchResponse = {
  endpoints: CodeSearchResult[]
  methods: CodeSearchResult[]
  files: CodeSearchResult[]
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
    request<IndexRun>(`/api/repositories/${id}/index?mode=INCREMENTAL`, {
      method: 'POST',
    }),
  run: (id: string) => request<IndexRun>(`/api/index-runs/${id}`),
  endpoints: (id: string) =>
    request<HttpEndpoint[]>(`/api/repositories/${id}/http-endpoints`),
  endpointGraph: (repositoryId: string, endpointId: string, maxDepth = 4) =>
    request<ExecutionGraph>(
      `/api/repositories/${repositoryId}/graphs/endpoint/${endpointId}?maxDepth=${maxDepth}`,
    ),
  symbolGraph: (repositoryId: string, symbolId: string, maxDepth = 4) =>
    request<ExecutionGraph>(
      `/api/repositories/${repositoryId}/graphs/symbol/${symbolId}?maxDepth=${maxDepth}`,
    ),
  fileGraph: (repositoryId: string, fileId: string) =>
    request<ExecutionGraph>(
      `/api/repositories/${repositoryId}/graphs/file/${fileId}`,
    ),
  blastRadius: (repositoryId: string, symbolId: string, maxDepth = 4) =>
    request<ExecutionGraph>(
      `/api/repositories/${repositoryId}/graphs/blast-radius/${symbolId}?maxDepth=${maxDepth}`,
    ),
  source: (
    repositoryId: string,
    path: string,
    startLine: number,
    endLine: number,
  ) => {
    const query = new URLSearchParams({
      path,
      startLine: String(startLine),
      endLine: String(endLine),
    })
    return request<SourceExcerpt>(
      `/api/repositories/${repositoryId}/source?${query.toString()}`,
    )
  },
  history: (repositoryId: string, symbolId: string) =>
    request<GitFileHistory>(
      `/api/repositories/${repositoryId}/symbols/${symbolId}/history`,
    ),
  search: (repositoryId: string, query: string) => {
    const parameters = new URLSearchParams({ q: query })
    return request<CodeSearchResponse>(
      `/api/repositories/${repositoryId}/search?${parameters.toString()}`,
    )
  },
}
