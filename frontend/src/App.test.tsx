import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, test, vi } from 'vitest'
import { App } from './App'

vi.mock('./features/graph/ExecutionGraphView', () => ({
  ExecutionGraphView: ({
    repositoryId,
    endpoint,
  }: {
    repositoryId: string
    endpoint: { path: string } | null
  }) => (
    <div>
      {endpoint
        ? `Open graph: ${repositoryId}:${endpoint.path}`
        : `Open code browser: ${repositoryId}`}
    </div>
  ),
}))

const projects = [
  {
    id: 'other-project',
    displayName: 'Analysis Tasks',
    relativePath: 'demo-app',
    status: 'READY',
  },
  {
    id: 'self-project',
    displayName: 'Spring Boot Static Analysis source',
    relativePath: 'spring-boot-static-analysis',
    status: 'READY',
  },
] as const

function json(body: unknown) {
  return Promise.resolve(
    new Response(JSON.stringify(body), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }),
  )
}

function renderApp() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })

  return render(
    <QueryClientProvider client={client}>
      <App />
    </QueryClientProvider>,
  )
}

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

test('opens the self-analysis project directly in the map', async () => {
  vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
    const url = input.toString()
    if (url === 'api/config') {
      return json({ readOnly: true })
    }
    if (url === 'api/repositories') {
      return json(projects)
    }
    if (url === 'api/repositories/self-project/http-endpoints') {
      return json([
        {
          id: 'endpoint-search',
          httpMethod: 'GET',
          path: '/api/repositories/{repositoryId}/search',
        },
        {
          id: 'endpoint-index',
          httpMethod: 'POST',
          path: '/api/repositories/{repositoryId}/index',
        },
      ])
    }
    return json([])
  })

  renderApp()

  expect(
    screen.getByRole('heading', { level: 1, name: 'Spring Boot Static Analysis Map' }),
  ).toBeInTheDocument()
  await waitFor(() => {
    expect(screen.getByRole('combobox', { name: 'Project' })).toHaveValue(
      'self-project',
    )
  })
  expect(
    await screen.findByText(
      'Open graph: self-project:/api/repositories/{repositoryId}/search',
    ),
  ).toBeInTheDocument()
  expect(screen.queryByText('Add repository')).not.toBeInTheDocument()
  expect(screen.queryByText('Rescan repository')).not.toBeInTheDocument()
  expect(screen.queryByText('Remove repository')).not.toBeInTheDocument()
  expect(
    screen.queryByRole('option', { name: 'Add another project…' }),
  ).not.toBeInTheDocument()
})

test('switches the map when another indexed project is selected', async () => {
  vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
    const url = input.toString()
    if (url === 'api/config') {
      return json({ readOnly: true })
    }
    if (url === 'api/repositories') {
      return json(projects)
    }
    if (url === 'api/repositories/self-project/http-endpoints') {
      return json([
        {
          id: 'endpoint-search',
          httpMethod: 'GET',
          path: '/api/repositories/{repositoryId}/search',
        },
      ])
    }
    if (url === 'api/repositories/other-project/http-endpoints') {
      return json([
        {
          id: 'endpoint-issues',
          httpMethod: 'GET',
          path: '/api/issues',
        },
      ])
    }
    return json([])
  })

  renderApp()
  const projectPicker = await screen.findByRole('combobox', { name: 'Project' })
  await screen.findByText(
    'Open graph: self-project:/api/repositories/{repositoryId}/search',
  )

  await userEvent.selectOptions(projectPicker, 'other-project')

  expect(await screen.findByText('Open graph: other-project:/api/issues')).toBeInTheDocument()
})

test('opens the code browser for an indexed project with no endpoints', async () => {
  vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
    const url = input.toString()
    if (url === 'api/config') {
      return json({ readOnly: true })
    }
    if (url === 'api/repositories') {
      return json([
        {
          id: 'library-project',
          displayName: 'Shared library',
          relativePath: 'shared-library',
          status: 'READY',
        },
      ])
    }
    return json([])
  })

  renderApp()

  expect(
    await screen.findByText('Open code browser: library-project'),
  ).toBeInTheDocument()
})

test('registers and indexes another local project from the selector', async () => {
  let created = false
  const requests: Array<{ url: string; method: string; body?: string }> = []
  vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
    const url = input.toString()
    requests.push({
      url,
      method: init?.method ?? 'GET',
      body: typeof init?.body === 'string' ? init.body : undefined,
    })
    if (url === 'api/config') {
      return json({ readOnly: false })
    }
    if (url === 'api/repositories' && init?.method === 'POST') {
      created = true
      return json({
        id: 'new-project',
        displayName: 'demo-app',
        relativePath: 'workspace/demo-app',
        status: 'REGISTERED',
      })
    }
    if (url === 'api/repositories/new-project/index?mode=FULL') {
      return json({ id: 'index-run' })
    }
    if (url === 'api/repositories') {
      return json(created
        ? [
            ...projects,
            {
              id: 'new-project',
              displayName: 'demo-app',
              relativePath: 'workspace/demo-app',
              status: 'INDEXING',
            },
          ]
        : projects)
    }
    return json([])
  })

  renderApp()
  const picker = screen.getByRole('combobox', { name: 'Project' })
  await waitFor(() => expect(picker).toHaveValue('self-project'))

  await userEvent.selectOptions(picker, '__add-project__')
  expect(screen.getByRole('dialog', { name: 'Add local project' })).toBeInTheDocument()

  await userEvent.type(
    screen.getByRole('textbox', { name: 'Relative path' }),
    'workspace/demo-app',
  )
  await userEvent.click(screen.getByRole('button', { name: 'Add and index' }))

  await waitFor(() => expect(picker).toHaveValue('new-project'))
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  expect(requests).toContainEqual({
    url: 'api/repositories',
    method: 'POST',
    body: JSON.stringify({
      displayName: 'demo-app',
      relativePath: 'workspace/demo-app',
    }),
  })
  expect(requests).toContainEqual({
    url: 'api/repositories/new-project/index?mode=FULL',
    method: 'POST',
    body: undefined,
  })
})
