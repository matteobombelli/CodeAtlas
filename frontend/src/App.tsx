import {
  lazy,
  Suspense,
  useEffect,
  useMemo,
  useRef,
  useState,
  type FormEvent,
  type KeyboardEvent as ReactKeyboardEvent,
} from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  repositoryApi,
  type HttpEndpoint,
  type Repository,
} from './api/repositories'
import styles from './App.module.css'

const ExecutionGraphView = lazy(() =>
  import('./features/graph/ExecutionGraphView').then((module) => ({
    default: module.ExecutionGraphView,
  })),
)

const ADD_PROJECT = '__add-project__'

function selfAnalysisProject(repositories: Repository[]) {
  return (
    repositories.find(
      (repository) => repository.displayName === 'Spring Boot Static Analysis source',
    ) ??
    repositories.find(
      (repository) => repository.relativePath === 'spring-boot-static-analysis',
    ) ??
    repositories.find((repository) => repository.status === 'READY') ??
    repositories[0]
  )
}

function initialEndpoint(endpoints: HttpEndpoint[]) {
  return (
    endpoints.find(
      (endpoint) =>
        endpoint.httpMethod === 'GET' &&
        endpoint.path === '/api/repositories/{repositoryId}/search',
    ) ??
    endpoints.find(
      (endpoint) =>
        endpoint.httpMethod === 'POST' &&
        endpoint.path === '/api/repositories/{repositoryId}/index',
    ) ??
    endpoints[0] ??
    null
  )
}

export function App() {
  const [activeProjectId, setActiveProjectId] = useState<string | null>(null)
  const [showAddProject, setShowAddProject] = useState(false)
  const [projectPath, setProjectPath] = useState('')
  const [addProjectError, setAddProjectError] = useState<string | null>(null)
  const [addingProject, setAddingProject] = useState(false)
  const projectPicker = useRef<HTMLSelectElement>(null)
  const config = useQuery({
    queryKey: ['config'],
    queryFn: repositoryApi.config,
    staleTime: Infinity,
  })
  // Fail closed: mutation controls stay hidden until the server confirms this
  // is a writable local deployment.
  const readOnly = config.data?.readOnly ?? true
  const projects = useQuery({
    queryKey: ['repositories'],
    queryFn: repositoryApi.list,
    refetchInterval: (query) => {
      const data = query.state.data
      return !data?.length || data.some((project) => project.status === 'INDEXING')
        ? 2_000
        : false
    },
  })
  const defaultProject = useMemo(
    () => selfAnalysisProject(projects.data ?? []),
    [projects.data],
  )
  const activeProject =
    projects.data?.find((project) => project.id === activeProjectId) ?? defaultProject
  const endpoints = useQuery({
    queryKey: ['endpoints', activeProject?.id],
    queryFn: () => repositoryApi.endpoints(activeProject!.id),
    enabled: activeProject?.status === 'READY',
  })
  const endpoint = useMemo(
    () => initialEndpoint(endpoints.data ?? []),
    [endpoints.data],
  )

  useEffect(() => {
    if (!activeProjectId && defaultProject) {
      setActiveProjectId(defaultProject.id)
    }
  }, [activeProjectId, defaultProject])

  useEffect(() => {
    if (readOnly) {
      setShowAddProject(false)
      setAddProjectError(null)
    }
  }, [readOnly])

  function closeAddProject() {
    if (addingProject) return
    setShowAddProject(false)
    setAddProjectError(null)
    queueMicrotask(() => projectPicker.current?.focus())
  }

  function handleDialogKeyDown(event: ReactKeyboardEvent<HTMLElement>) {
    if (event.key === 'Escape') {
      event.preventDefault()
      closeAddProject()
      return
    }
    if (event.key !== 'Tab') return
    const focusable = Array.from(
      event.currentTarget.querySelectorAll<HTMLElement>(
        'input:not(:disabled), button:not(:disabled)',
      ),
    )
    const first = focusable[0]
    const last = focusable.at(-1)
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault()
      last?.focus()
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault()
      first?.focus()
    }
  }

  async function addProject(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const relativePath = projectPath
      .trim()
      .replaceAll('\\', '/')
      .replace(/\/+$/, '')
    const displayName = relativePath.split('/').filter(Boolean).at(-1)
    if (!displayName) {
      setAddProjectError('Enter a project path relative to the configured root.')
      return
    }

    setAddingProject(true)
    setAddProjectError(null)
    try {
      const created = await repositoryApi.create(displayName, relativePath)
      await repositoryApi.index(created.id)
      setActiveProjectId(created.id)
      setProjectPath('')
      setShowAddProject(false)
      await projects.refetch()
    } catch (error) {
      setAddProjectError(
        error instanceof Error ? error.message : 'Could not add the project.',
      )
    } finally {
      setAddingProject(false)
    }
  }

  return (
    <div className={styles.app}>
      <header className={styles.header}>
        <h1>Spring Boot Static Analysis Map</h1>
        <label className={styles.projectPicker}>
          <span>Project</span>
          <select
            ref={projectPicker}
            value={activeProject?.id ?? ''}
            disabled={projects.isLoading || config.isLoading}
            onChange={(event) => {
              if (event.target.value === ADD_PROJECT) {
                setShowAddProject(true)
                return
              }
              setActiveProjectId(event.target.value)
            }}
          >
            {!projects.data?.length && <option value="">No indexed project</option>}
            {projects.data?.map((project) => (
              <option key={project.id} value={project.id}>
                {project.displayName}
              </option>
            ))}
            {!readOnly && (
              <option value={ADD_PROJECT}>Add another project…</option>
            )}
          </select>
        </label>
      </header>

      <main className={styles.map}>
        {projects.isLoading && (
          <p className={styles.state} role="status">Loading project map</p>
        )}
        {config.isError && (
          <p className={styles.error} role="alert">
            Could not load deployment configuration.
          </p>
        )}
        {projects.isError && (
          <p className={styles.error} role="alert">
            Could not load projects: {projects.error.message}
          </p>
        )}
        {projects.data?.length === 0 && (
          <p className={styles.state}>No project is configured for analysis.</p>
        )}
        {activeProject && activeProject.status !== 'READY' && (
          <p className={styles.state} role="status">
            {activeProject.status === 'INDEXING'
              ? `Indexing ${activeProject.displayName}`
              : `${activeProject.displayName} is not indexed`}
          </p>
        )}
        {endpoints.isLoading && (
          <p className={styles.state} role="status">Loading project map</p>
        )}
        {endpoints.isError && (
          <p className={styles.error} role="alert">
            Could not load map: {endpoints.error.message}
          </p>
        )}
        {activeProject?.status === 'READY' && endpoints.data && (
          <Suspense fallback={<p className={styles.state}>Loading project map</p>}>
            <ExecutionGraphView
              key={activeProject.id}
              repositoryId={activeProject.id}
              endpoint={endpoint}
              endpoints={endpoints.data}
            />
          </Suspense>
        )}
      </main>

      {showAddProject && (
        <div className={styles.dialogBackdrop} onMouseDown={(event) => {
          if (event.target === event.currentTarget) closeAddProject()
        }}>
          <section
            className={styles.dialog}
            role="dialog"
            aria-modal="true"
            aria-labelledby="add-project-title"
            onKeyDown={handleDialogKeyDown}
          >
            <form onSubmit={addProject}>
              <h2 id="add-project-title">Add local project</h2>
              <p>
                Enter a directory beneath the backend&apos;s configured project root.
              </p>
              <label>
                <span>Relative path</span>
                <input
                  autoFocus
                  value={projectPath}
                  onChange={(event) => setProjectPath(event.target.value)}
                  placeholder="my-spring-project"
                  disabled={addingProject}
                />
              </label>
              {addProjectError && <p className={styles.dialogError} role="alert">
                {addProjectError}
              </p>}
              <div className={styles.dialogActions}>
                <button type="button" onClick={closeAddProject} disabled={addingProject}>
                  Cancel
                </button>
                <button type="submit" disabled={addingProject}>
                  {addingProject ? 'Adding project' : 'Add and index'}
                </button>
              </div>
            </form>
          </section>
        </div>
      )}
    </div>
  )
}
