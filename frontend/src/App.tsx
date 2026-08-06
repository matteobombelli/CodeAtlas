import { useQuery } from '@tanstack/react-query'
import { RepositoryPanel } from './features/repositories/RepositoryPanel'
import styles from './App.module.css'

type HealthResponse = {
  status: string
  components?: {
    db?: { status: string }
  }
}

async function getHealth(): Promise<HealthResponse> {
  const response = await fetch('/actuator/health')
  if (!response.ok) {
    throw new Error(`Health request failed with ${response.status}`)
  }
  return response.json() as Promise<HealthResponse>
}

function Status({ label, status }: { label: string; status: string }) {
  const healthy = status === 'UP'
  const checking = status === 'CHECKING'
  return (
    <li className={styles.status}>
      <span
        className={healthy ? styles.up : checking ? styles.checking : styles.down}
        aria-hidden="true"
      />
      <span>{label}</span>
      <strong>{healthy ? 'online' : checking ? 'checking' : 'unavailable'}</strong>
    </li>
  )
}

export function App() {
  const health = useQuery({
    queryKey: ['health'],
    queryFn: getHealth,
    refetchInterval: 10_000,
    retry: 1,
  })

  const backendStatus = health.isError ? 'DOWN' : (health.data?.status ?? 'CHECKING')
  const databaseStatus = health.isError
    ? 'UNKNOWN'
    : (health.data?.components?.db?.status ?? 'CHECKING')

  return (
    <div className={styles.app}>
      <header className={styles.header}>
        <a className={styles.brand} href="/" aria-label="Code Atlas home">
          <span className={styles.mark} aria-hidden="true">
            <i />
          </span>
          Code Atlas
        </a>
        <div className={styles.headerMeta}>
          <span>Local Spring code browser</span>
          <ul aria-label="System status" className={styles.statusList}>
            <Status label="API" status={backendStatus} />
            <Status label="Database" status={databaseStatus} />
          </ul>
        </div>
      </header>

      <main className={styles.main}>
        <section className={styles.intro} aria-labelledby="page-title">
          <p className={styles.kicker}>Spring Boot, from route to database</p>
          <h1 id="page-title">Read the request path.</h1>
          <p>
            Choose an endpoint to see the methods, repositories, entities, tests,
            and source lines connected to it. Imported code is read, never run.
          </p>
        </section>
        <RepositoryPanel />
      </main>
    </div>
  )
}
