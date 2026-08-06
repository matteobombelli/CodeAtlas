import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, test, vi } from 'vitest'
import type { HttpEndpoint } from '../../api/repositories'
import { CodeNavigator } from './CodeNavigator'
import { endpointTarget } from './graphTargets'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

const endpoint: HttpEndpoint = {
  id: 'endpoint-1',
  httpMethod: 'POST',
  path: '/api/repositories/{repositoryId}/index',
  controllerMethodId: 'method-1',
  controller: 'dev.codeatlas.api.IndexRunController',
  method: 'start',
  signature: 'start(UUID)',
  sourcePath: 'backend/src/main/java/dev/codeatlas/api/IndexRunController.java',
  startLine: 20,
  endLine: 30,
  requestType: null,
  responseType: null,
}

test('groups search results into endpoints, methods, and files', async () => {
  vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
    Promise.resolve(new Response(
      JSON.stringify({
        endpoints: [
          {
            id: 'endpoint-1',
            label: endpoint.path,
            detail: 'IndexRunController.start',
            sourcePath: endpoint.sourcePath,
            startLine: 20,
            endLine: 30,
            httpMethod: 'POST',
          },
        ],
        methods: [
          {
            id: 'method-2',
            label: 'execute(UUID)',
            detail: 'dev.codeatlas.indexing.IndexingService.execute',
            sourcePath:
              'backend/src/main/java/dev/codeatlas/indexing/IndexingService.java',
            startLine: 80,
            endLine: 120,
            httpMethod: null,
          },
        ],
        files: [
          {
            id: 'file-1',
            label: 'backend/src/main/java/dev/codeatlas/indexing/IndexingService.java',
            detail: 'dev.codeatlas.indexing',
            sourcePath:
              'backend/src/main/java/dev/codeatlas/indexing/IndexingService.java',
            startLine: 1,
            endLine: 220,
            httpMethod: null,
          },
        ],
      }),
      { status: 200, headers: { 'Content-Type': 'application/json' } },
    )),
  )
  const onSelect = vi.fn()
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })

  render(
    <QueryClientProvider client={client}>
      <CodeNavigator
        repositoryId="repository-1"
        endpoints={[endpoint]}
        active={endpointTarget(endpoint)}
        onSelect={onSelect}
      />
    </QueryClientProvider>,
  )

  await userEvent.type(screen.getByRole('searchbox'), 'index')

  expect(await screen.findByRole('heading', { name: 'Endpoints' })).toBeInTheDocument()
  expect(screen.getByRole('heading', { name: 'Methods' })).toBeInTheDocument()
  expect(screen.getByRole('heading', { name: 'Files' })).toBeInTheDocument()
  expect(screen.getByText('execute(UUID)')).toBeInTheDocument()
  expect(screen.getAllByText(/IndexingService.java/).length).toBeGreaterThan(0)

  await userEvent.click(screen.getByText('execute(UUID)'))
  expect(onSelect).toHaveBeenCalledWith(
    expect.objectContaining({ category: 'METHOD', id: 'method-2' }),
  )
})
