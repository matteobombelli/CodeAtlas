import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { afterEach, expect, test, vi } from 'vitest'
import { App } from './App'

afterEach(() => {
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
