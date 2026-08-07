export type Repository = {
  id: string
  displayName: string
  relativePath: string
  status: 'REGISTERED' | 'INDEXING' | 'READY' | 'FAILED'
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

export type CodeSearchResult = {
  id: string
  symbolId: string | null
  kind: string
  label: string
  detail: string
  sourcePath: string
  startLine: number
  endLine: number
  httpMethod: string | null
}

export type CodeSearchResponse = {
  endpoints: CodeSearchResult[]
  callables: CodeSearchResult[]
  files: CodeSearchResult[]
}

export type ClientConfig = {
  readOnly: boolean
}

/**
 * Paths are relative to the served page, so the same bundle works at the site
 * root and behind a reverse proxy that adds a prefix.
 */
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, init)
  if (!response.ok) {
    const problem = (await response.json().catch(() => null)) as { detail?: string } | null
    throw new Error(problem?.detail ?? `Request failed with ${response.status}`)
  }
  return response.json() as Promise<T>
}

export const repositoryApi = {
  config: () => request<ClientConfig>('api/config'),
  list: () => request<Repository[]>('api/repositories'),
  create: (displayName: string, relativePath: string) =>
    request<Repository>('api/repositories', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ displayName, relativePath }),
    }),
  index: (id: string) =>
    request<unknown>(`api/repositories/${id}/index?mode=FULL`, {
      method: 'POST',
    }),
  endpoints: (id: string) =>
    request<HttpEndpoint[]>(`api/repositories/${id}/http-endpoints`),
  endpointGraph: (repositoryId: string, endpointId: string, maxDepth = 4) =>
    request<ExecutionGraph>(
      `api/repositories/${repositoryId}/graphs/endpoint/${endpointId}?maxDepth=${maxDepth}`,
    ),
  symbolGraph: (repositoryId: string, symbolId: string, maxDepth = 4) =>
    request<ExecutionGraph>(
      `api/repositories/${repositoryId}/graphs/symbol/${symbolId}?maxDepth=${maxDepth}`,
    ),
  fileGraph: (repositoryId: string, fileId: string) =>
    request<ExecutionGraph>(
      `api/repositories/${repositoryId}/graphs/file/${fileId}`,
    ),
  blastRadius: (repositoryId: string, symbolId: string, maxDepth = 4) =>
    request<ExecutionGraph>(
      `api/repositories/${repositoryId}/graphs/blast-radius/${symbolId}?maxDepth=${maxDepth}`,
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
      `api/repositories/${repositoryId}/source?${query.toString()}`,
    )
  },
  search: (repositoryId: string, query: string) => {
    const parameters = new URLSearchParams({ q: query })
    return request<CodeSearchResponse>(
      `api/repositories/${repositoryId}/search?${parameters.toString()}`,
    )
  },
}
