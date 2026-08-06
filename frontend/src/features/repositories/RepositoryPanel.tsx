import { FormEvent, lazy, Suspense, useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  repositoryApi,
  type HttpEndpoint,
  type IndexRun,
  type Repository,
} from '../../api/repositories'
import styles from './RepositoryPanel.module.css'

const ExecutionGraphView = lazy(() =>
  import('../graph/ExecutionGraphView').then((module) => ({
    default: module.ExecutionGraphView,
  })),
)

function RunProgress({
  initialRun,
  onFinished,
}: {
  initialRun: IndexRun
  onFinished: (run: IndexRun) => void
}) {
  const run = useQuery({
    queryKey: ['index-run', initialRun.id],
    queryFn: () => repositoryApi.run(initialRun.id),
    initialData: initialRun,
    refetchInterval: (query) => {
      const status = query.state.data?.status
      return status === 'COMPLETE' || status === 'FAILED' ? false : 1_000
    },
  })

  const terminal = run.data.status === 'COMPLETE' || run.data.status === 'FAILED'
  useEffect(() => {
    if (terminal) {
      onFinished(run.data)
    }
  }, [terminal, onFinished, run.data])

  return (
    <div className={styles.progress} role="status">
      <span>{run.data.phase.replaceAll('_', ' ').toLowerCase()}</span>
      <strong>
        {run.data.filesProcessed}/{run.data.filesDiscovered || '-'} files
      </strong>
      {run.data.errorSummary && <p>{run.data.errorSummary}</p>}
    </div>
  )
}

function RepositoryCard({
  repository,
  onChanged,
  onOpenEndpoint,
}: {
  repository: Repository
  onChanged: () => void
  onOpenEndpoint: (endpoint: HttpEndpoint) => void
}) {
  const [run, setRun] = useState<IndexRun | null>(null)
  const [lastRun, setLastRun] = useState<IndexRun | null>(null)
  const [endpointsOpen, setEndpointsOpen] = useState(false)
  const endpoints = useQuery({
    queryKey: ['endpoints', repository.id],
    queryFn: () => repositoryApi.endpoints(repository.id),
    enabled: endpointsOpen,
  })
  const index = useMutation({
    mutationFn: () => repositoryApi.index(repository.id),
    onSuccess: setRun,
  })
  const remove = useMutation({
    mutationFn: () => repositoryApi.remove(repository.id),
    onSuccess: onChanged,
  })

  return (
    <article className={styles.card}>
      <div className={styles.cardTop}>
        <div>
          <span className={styles.badge}>{repository.status}</span>
          <h4>{repository.displayName}</h4>
          <p>{repository.relativePath}</p>
        </div>
        <span className={styles.count}>{repository.sourceFileCount} Java files</span>
      </div>
      <dl>
        <div>
          <dt>Build</dt>
          <dd>{repository.buildSystem}</dd>
        </div>
        <div>
          <dt>Git</dt>
          <dd>
            {repository.defaultBranch ?? 'detached'} ·{' '}
            {repository.headSha?.slice(0, 8) ?? 'no commit'}
            {repository.dirty ? ' · modified' : ''}
          </dd>
        </div>
      </dl>
      {run && (
        <RunProgress
          initialRun={run}
          onFinished={(finished) => {
            setLastRun(finished)
            setRun(null)
            onChanged()
          }}
        />
      )}
      {lastRun?.status === 'COMPLETE' && (
        <p className={styles.summary}>
          Processed {lastRun.filesProcessed} of {lastRun.filesDiscovered} files ·{' '}
          {lastRun.filesAdded} added · {lastRun.filesModified} modified ·{' '}
          {lastRun.filesDeleted} deleted
        </p>
      )}
      {(index.error || remove.error) && (
        <p className={styles.error}>{(index.error ?? remove.error)?.message}</p>
      )}
      <div className={styles.actions}>
        <button disabled={index.isPending || !!run} onClick={() => index.mutate()}>
          {index.isPending
            ? 'Queueing'
            : repository.activeIndexRunId
              ? 'Rescan repository'
              : 'Index repository'}
        </button>
        {repository.status === 'READY' && (
          <button
            className={styles.secondary}
            onClick={() => setEndpointsOpen((open) => !open)}
          >
            {endpointsOpen ? 'Hide endpoints' : 'Browse endpoints'}
          </button>
        )}
        <button
          className={styles.secondary}
          disabled={remove.isPending}
          onClick={() => remove.mutate()}
        >
          Remove
        </button>
      </div>
      {endpointsOpen && (
        <div className={styles.endpoints}>
          {endpoints.isLoading && <p>Loading endpoints</p>}
          {endpoints.data?.map((endpoint) => (
            <button
              key={endpoint.id}
              type="button"
              onClick={() => onOpenEndpoint(endpoint)}
            >
              <span className={styles.method}>{endpoint.httpMethod}</span>
              <strong>{endpoint.path}</strong>
              <small>
                {endpoint.controller}.{endpoint.method}
              </small>
            </button>
          ))}
          {endpoints.data?.length === 0 && (
            <p>No Spring HTTP endpoints were detected.</p>
          )}
        </div>
      )}
    </article>
  )
}

export function RepositoryPanel() {
  const queryClient = useQueryClient()
  const [displayName, setDisplayName] = useState('')
  const [relativePath, setRelativePath] = useState('')
  const [activeRepositoryId, setActiveRepositoryId] = useState<string | null>(null)
  const [selected, setSelected] = useState<{
    repositoryId: string
    endpoint: HttpEndpoint
  } | null>(null)
  const repositories = useQuery({
    queryKey: ['repositories'],
    queryFn: repositoryApi.list,
    refetchInterval: (query) => {
      const data = query.state.data
      return !data?.length || data.some((repository) => repository.status === 'INDEXING')
        ? 2_000
        : false
    },
  })
  const preferredRepository =
    repositories.data?.find((repository) => repository.relativePath === 'code-atlas') ??
    repositories.data?.find((repository) => repository.status === 'READY') ??
    repositories.data?.[0]
  const activeRepository =
    repositories.data?.find((repository) => repository.id === activeRepositoryId) ??
    preferredRepository
  const demoEndpoints = useQuery({
    queryKey: ['endpoints', activeRepository?.id],
    queryFn: () => repositoryApi.endpoints(activeRepository!.id),
    enabled: activeRepository?.status === 'READY',
  })

  useEffect(() => {
    if (!activeRepositoryId && preferredRepository) {
      setActiveRepositoryId(preferredRepository.id)
    }
  }, [activeRepositoryId, preferredRepository])

  useEffect(() => {
    if (!activeRepository || !demoEndpoints.data?.length) return
    if (selected?.repositoryId === activeRepository.id) return
    const preferredEndpoint =
      demoEndpoints.data.find(
        (endpoint) =>
          endpoint.httpMethod === 'GET' &&
          endpoint.path === '/api/repositories/{repositoryId}/search',
      ) ??
      demoEndpoints.data.find(
        (endpoint) =>
          endpoint.httpMethod === 'POST' &&
          endpoint.path === '/api/repositories/{repositoryId}/index',
      ) ??
      demoEndpoints.data[0]
    setSelected({ repositoryId: activeRepository.id, endpoint: preferredEndpoint })
  }, [activeRepository, demoEndpoints.data, selected?.repositoryId])

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['repositories'] })
  }
  const create = useMutation({
    mutationFn: () => repositoryApi.create(displayName, relativePath),
    onSuccess: () => {
      setDisplayName('')
      setRelativePath('')
      refresh()
    },
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    create.mutate()
  }

  return (
    <section className={styles.panel} aria-labelledby="repositories-title">
      <div className={styles.demoHeader}>
        <div>
          <p>Live repository</p>
          <h2>{activeRepository?.displayName ?? 'Waiting for a repository'}</h2>
        </div>
        {repositories.data && repositories.data.length > 1 && (
          <label>
            Repository
            <select
              value={activeRepository?.id ?? ''}
              onChange={(event) => {
                setActiveRepositoryId(event.target.value)
                setSelected(null)
              }}
            >
              {repositories.data.map((repository) => (
                <option key={repository.id} value={repository.id}>
                  {repository.displayName}
                </option>
              ))}
            </select>
          </label>
        )}
      </div>

      {repositories.isLoading && (
        <p className={styles.demoState}>Loading the mounted repository.</p>
      )}
      {activeRepository && activeRepository.status !== 'READY' && (
        <div className={styles.demoState} role="status">
          <strong>
            {activeRepository.status === 'INDEXING'
              ? 'Indexing this repository'
              : 'This repository is not indexed'}
          </strong>
          <span>
            {activeRepository.sourceFileCount} Java files found. The graph will open when
            the index is ready.
          </span>
        </div>
      )}
      {demoEndpoints.isLoading && (
        <p className={styles.demoState}>Loading indexed endpoints.</p>
      )}
      {selected && selected.repositoryId === activeRepository?.id && (
        <Suspense fallback={<p className={styles.demoState}>Loading graph.</p>}>
          <ExecutionGraphView
            repositoryId={selected.repositoryId}
            endpoint={selected.endpoint}
            endpoints={demoEndpoints.data ?? []}
          />
        </Suspense>
      )}

      <div className={styles.titleRow}>
        <div>
          <h3 id="repositories-title">Repositories</h3>
          <p>Only paths mounted under the configured repository root can be read.</p>
        </div>
        <span>{repositories.data?.length ?? 0} registered</span>
      </div>

      <form onSubmit={submit} className={styles.form}>
        <label>
          Display name
          <input
            required
            maxLength={200}
            value={displayName}
            onChange={(event) => setDisplayName(event.target.value)}
            placeholder="Billing service"
          />
        </label>
        <label>
          Relative path
          <input
            required
            value={relativePath}
            onChange={(event) => setRelativePath(event.target.value)}
            placeholder="billing-service"
          />
        </label>
        <button disabled={create.isPending} type="submit">
          {create.isPending ? 'Adding' : 'Add repository'}
        </button>
      </form>

      {create.error && <p className={styles.error}>{create.error.message}</p>}
      {repositories.isError && <p className={styles.error}>{repositories.error.message}</p>}

      <div className={styles.list}>
        {repositories.data?.map((repository) => (
          <RepositoryCard
            key={repository.id}
            repository={repository}
            onChanged={refresh}
            onOpenEndpoint={(endpoint) =>
              {
                setActiveRepositoryId(repository.id)
                setSelected({ repositoryId: repository.id, endpoint })
              }
            }
          />
        ))}
        {repositories.data?.length === 0 && (
          <p className={styles.empty}>Add a mounted Git repository to begin.</p>
        )}
      </div>
    </section>
  )
}
