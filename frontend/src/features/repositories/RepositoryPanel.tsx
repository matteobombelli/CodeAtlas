import { FormEvent, useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  repositoryApi,
  type HttpEndpoint,
  type IndexRun,
  type Repository,
} from '../../api/repositories'
import { ExecutionGraphView } from '../graph/ExecutionGraphView'
import styles from './RepositoryPanel.module.css'

function RunProgress({
  initialRun,
  onFinished,
}: {
  initialRun: IndexRun
  onFinished: () => void
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
      onFinished()
    }
  }, [terminal, onFinished])

  return (
    <div className={styles.progress} role="status">
      <span>{run.data.phase.replaceAll('_', ' ').toLowerCase()}</span>
      <strong>
        {run.data.filesProcessed}/{run.data.filesDiscovered || '—'} files
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
          onFinished={() => {
            setRun(null)
            onChanged()
          }}
        />
      )}
      {(index.error || remove.error) && (
        <p className={styles.error}>{(index.error ?? remove.error)?.message}</p>
      )}
      <div className={styles.actions}>
        <button disabled={index.isPending || !!run} onClick={() => index.mutate()}>
          {index.isPending ? 'Queueing…' : 'Index repository'}
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
          {endpoints.isLoading && <p>Loading endpoints…</p>}
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
  const [selected, setSelected] = useState<{
    repositoryId: string
    endpoint: HttpEndpoint
  } | null>(null)
  const repositories = useQuery({
    queryKey: ['repositories'],
    queryFn: repositoryApi.list,
  })
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
      <div className={styles.titleRow}>
        <div>
          <p>Approved local roots only</p>
          <h3 id="repositories-title">Repositories</h3>
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
            placeholder="Atlas Tasks"
          />
        </label>
        <label>
          Relative path
          <input
            required
            value={relativePath}
            onChange={(event) => setRelativePath(event.target.value)}
            placeholder="code-atlas"
          />
        </label>
        <button disabled={create.isPending} type="submit">
          {create.isPending ? 'Adding…' : 'Add repository'}
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
              setSelected({ repositoryId: repository.id, endpoint })
            }
          />
        ))}
        {repositories.data?.length === 0 && (
          <p className={styles.empty}>Register a mounted Git repository to begin.</p>
        )}
      </div>
      {selected && (
        <ExecutionGraphView
          repositoryId={selected.repositoryId}
          endpoint={selected.endpoint}
        />
      )}
    </section>
  )
}
