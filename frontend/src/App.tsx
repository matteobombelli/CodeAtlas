import { useQuery } from '@tanstack/react-query'
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
  return (
    <div className={styles.statusCard}>
      <span className={healthy ? styles.up : styles.down} aria-hidden="true" />
      <div>
        <p>{label}</p>
        <strong>{status}</strong>
      </div>
    </div>
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
    <main className={styles.shell}>
      <header className={styles.header}>
        <div className={styles.mark}>CA</div>
        <div>
          <p className={styles.eyebrow}>LOCAL CODE CARTOGRAPHY</p>
          <h1>Code Atlas</h1>
        </div>
      </header>

      <section className={styles.hero}>
        <p className={styles.kicker}>Interactive execution maps</p>
        <h2>Trace a Spring endpoint from request to data.</h2>
        <p className={styles.copy}>
          Explore calls, entities, tests, source evidence, and potential change
          impact without executing the imported repository.
        </p>
      </section>

      <section aria-label="System health" className={styles.health}>
        <h3>System health</h3>
        <div className={styles.statusGrid}>
          <Status label="Backend API" status={backendStatus} />
          <Status label="PostgreSQL" status={databaseStatus} />
        </div>
      </section>
    </main>
  )
}
