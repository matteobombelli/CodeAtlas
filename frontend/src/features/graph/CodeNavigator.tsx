import { useDeferredValue, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  repositoryApi,
  type CodeSearchResult,
  type HttpEndpoint,
} from '../../api/repositories'
import { endpointTarget, type GraphTarget } from './graphTargets'
import styles from './ExecutionGraphView.module.css'

function resultTarget(
  category: GraphTarget['category'],
  result: CodeSearchResult,
): GraphTarget {
  return { category, ...result }
}

function ResultGroup({
  title,
  category,
  results,
  active,
  onSelect,
}: {
  title: string
  category: GraphTarget['category']
  results: CodeSearchResult[]
  active: GraphTarget
  onSelect: (target: GraphTarget) => void
}) {
  return (
    <section className={styles.resultGroup} aria-labelledby={`search-${category}`}>
      <div className={styles.resultHeading}>
        <h4 id={`search-${category}`}>{title}</h4>
        <span>{results.length}</span>
      </div>
      {results.map((result) => {
        const target = resultTarget(category, result)
        return (
          <button
            key={`${category}:${result.id}`}
            className={
              active.category === category && active.id === result.id
                ? styles.activeNavItem
                : undefined
            }
            type="button"
            onClick={() => onSelect(target)}
          >
            <span className={styles.resultLabel}>
              {result.httpMethod && (
                <b data-method={result.httpMethod}>{result.httpMethod}</b>
              )}
              <strong>{result.label}</strong>
            </span>
            <small>{result.sourcePath}</small>
          </button>
        )
      })}
      {results.length === 0 && <p>No matches</p>}
    </section>
  )
}

export function CodeNavigator({
  repositoryId,
  endpoints,
  active,
  onSelect,
}: {
  repositoryId: string
  endpoints: HttpEndpoint[]
  active: GraphTarget
  onSelect: (target: GraphTarget) => void
}) {
  const [query, setQuery] = useState('')
  const deferredQuery = useDeferredValue(query.trim())
  const search = useQuery({
    queryKey: ['code-search', repositoryId, deferredQuery],
    queryFn: () => repositoryApi.search(repositoryId, deferredQuery),
    enabled: deferredQuery.length >= 2,
  })
  const searching = query.trim().length >= 2

  return (
    <nav className={styles.navigator} aria-label="Code browser">
      <label className={styles.searchBox}>
        <span>Search code</span>
        <input
          type="search"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Endpoint, method, or file"
        />
      </label>

      <div className={styles.navigationBody}>
        {searching ? (
          <>
            {search.isLoading && <p className={styles.navMessage}>Searching</p>}
            {search.isError && (
              <p className={styles.navError}>{search.error.message}</p>
            )}
            {search.data && (
              <div className={styles.searchResults}>
                <ResultGroup
                  title="Endpoints"
                  category="ENDPOINT"
                  results={search.data.endpoints}
                  active={active}
                  onSelect={onSelect}
                />
                <ResultGroup
                  title="Methods"
                  category="METHOD"
                  results={search.data.methods}
                  active={active}
                  onSelect={onSelect}
                />
                <ResultGroup
                  title="Files"
                  category="FILE"
                  results={search.data.files}
                  active={active}
                  onSelect={onSelect}
                />
              </div>
            )}
          </>
        ) : (
          <section className={styles.endpointList} aria-labelledby="endpoint-list-title">
            <div className={styles.resultHeading}>
              <h4 id="endpoint-list-title">Endpoints</h4>
              <span>{endpoints.length}</span>
            </div>
            {endpoints.map((endpoint) => {
              const target = endpointTarget(endpoint)
              return (
                <button
                  key={endpoint.id}
                  className={
                    active.category === 'ENDPOINT' && active.id === endpoint.id
                      ? styles.activeNavItem
                      : undefined
                  }
                  type="button"
                  onClick={() => onSelect(target)}
                >
                  <span className={styles.resultLabel}>
                    <b data-method={endpoint.httpMethod}>{endpoint.httpMethod}</b>
                    <strong>{endpoint.path}</strong>
                  </span>
                  <small>
                    {endpoint.controller}.{endpoint.method}
                  </small>
                </button>
              )
            })}
          </section>
        )}
      </div>
      <p className={styles.searchHint}>
        Search uses the current index. Enter at least two characters.
      </p>
    </nav>
  )
}
