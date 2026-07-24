import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { afterEach, expect, test, vi } from 'vitest'
import { App } from './App'

afterEach(() => {
  vi.restoreAllMocks()
})

test('shows backend and database health', async () => {
  vi.spyOn(globalThis, 'fetch').mockResolvedValue(
    new Response(
      JSON.stringify({
        status: 'UP',
        components: { db: { status: 'UP' } },
      }),
      { status: 200, headers: { 'Content-Type': 'application/json' } },
    ),
  )

  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })

  render(
    <QueryClientProvider client={client}>
      <App />
    </QueryClientProvider>,
  )

  expect(screen.getByText('Backend API')).toBeInTheDocument()
  expect(screen.getByText('PostgreSQL')).toBeInTheDocument()
  expect(await screen.findAllByText('UP')).toHaveLength(2)
})
