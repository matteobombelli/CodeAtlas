import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, expect, test, vi } from 'vitest'
import { App } from './App'

vi.mock('./features/graph/ExecutionGraphView', () => ({
  ExecutionGraphView: ({ endpoint }: { endpoint: { path: string } }) => (
    <div>Open graph: {endpoint.path}</div>
  ),
}))

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

test('shows API and database health', async () => {
  vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
    const url = input.toString()
    const body = url.includes('/actuator/')
      ? { status: 'UP', components: { db: { status: 'UP' } } }
      : []
    return Promise.resolve(
      new Response(JSON.stringify(body), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
  })

  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })

  render(
    <QueryClientProvider client={client}>
      <App />
    </QueryClientProvider>,
  )

  expect(screen.getByText('API')).toBeInTheDocument()
  expect(screen.getByText('Database')).toBeInTheDocument()
  expect(await screen.findAllByText('online')).toHaveLength(2)
  expect(await screen.findByText('Add a mounted Git repository to begin.')).toBeInTheDocument()
})

test('opens the self-analysis repository without a user action', async () => {
  vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
    const url = input.toString()
    let body: unknown = []
    if (url.includes('/actuator/')) {
      body = { status: 'UP', components: { db: { status: 'UP' } } }
    } else if (url === '/api/repositories') {
      body = [
        {
          id: 'repository-1',
          displayName: 'Code Atlas source',
          relativePath: 'code-atlas',
          defaultBranch: 'main',
          headSha: 'abcdef123456',
          dirty: false,
          buildSystem: 'GRADLE',
          status: 'READY',
          activeIndexRunId: 'run-1',
          createdAt: '2026-08-06T00:00:00Z',
          lastIndexedAt: '2026-08-06T00:00:01Z',
          sourceFileCount: 120,
        },
      ]
    } else if (url === '/api/repositories/repository-1/http-endpoints') {
      body = [
        {
          id: 'endpoint-search',
          httpMethod: 'GET',
          path: '/api/repositories/{repositoryId}/search',
          controllerMethodId: 'symbol-search',
          controller: 'dev.codeatlas.api.SearchController',
          method: 'search',
          signature: 'search(UUID,String)',
          sourcePath: 'backend/src/main/java/dev/codeatlas/api/SearchController.java',
          startLine: 25,
          endLine: 31,
          requestType: null,
          responseType: 'CodeSearchResponse',
        },
        {
          id: 'endpoint-1',
          httpMethod: 'POST',
          path: '/api/repositories/{repositoryId}/index',
          controllerMethodId: 'symbol-1',
          controller: 'dev.codeatlas.api.IndexRunController',
          method: 'start',
          signature: 'start(UUID)',
          sourcePath: 'backend/src/main/java/dev/codeatlas/api/IndexRunController.java',
          startLine: 20,
          endLine: 30,
          requestType: null,
          responseType: null,
        },
      ]
    }
    return Promise.resolve(
      new Response(JSON.stringify(body), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
  })

  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })

  render(
    <QueryClientProvider client={client}>
      <App />
    </QueryClientProvider>,
  )

  expect(
    await screen.findByRole('heading', { level: 2, name: 'Code Atlas source' }),
  ).toBeInTheDocument()
  expect(
    await screen.findByText('Open graph: /api/repositories/{repositoryId}/search'),
  ).toBeInTheDocument()
})
