import type { HttpEndpoint } from '../../api/repositories'

export type GraphTarget = {
  category: 'ENDPOINT' | 'METHOD' | 'FILE'
  id: string
  label: string
  detail: string
  sourcePath: string
  startLine: number
  endLine: number
  httpMethod: string | null
}

export function endpointTarget(endpoint: HttpEndpoint): GraphTarget {
  return {
    category: 'ENDPOINT',
    id: endpoint.id,
    label: endpoint.path,
    detail: `${endpoint.controller}.${endpoint.method}`,
    sourcePath: endpoint.sourcePath,
    startLine: endpoint.startLine,
    endLine: endpoint.endLine,
    httpMethod: endpoint.httpMethod,
  }
}
