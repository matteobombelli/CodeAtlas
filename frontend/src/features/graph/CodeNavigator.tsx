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

function pathTail(path: string) {
  return `…/${path.split('/').at(-1) ?? path}`
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
  active: GraphTarget | null
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
              active?.category === category && active.id === result.id
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
              {!result.httpMethod && category === 'CALLABLE' && (
                <b data-kind={result.kind}>{result.kind.toLowerCase()}</b>
              )}
              <strong
                className={category === 'FILE' ? styles.pathTail : undefined}
                title={category === 'FILE' ? result.sourcePath : undefined}
              >
                {category === 'FILE' ? pathTail(result.sourcePath) : result.label}
              </strong>
            </span>
            <small className={styles.pathTail} title={result.sourcePath}>
              {pathTail(result.sourcePath)}
            </small>
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
  active: GraphTarget | null
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
        <span>Find code to inspect</span>
        <input
          type="search"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Function, method, endpoint, or file"
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
                  title="Functions & methods"
                  category="CALLABLE"
                  results={search.data.callables}
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
          <>
            <p className={styles.navigatorIntro}>
              Search for the code you plan to change, then inspect what it calls or
              what may depend on it.
            </p>
            <section className={styles.endpointList} aria-labelledby="endpoint-list-title">
              <div className={styles.resultHeading}>
                <h4 id="endpoint-list-title">Browse endpoints</h4>
                <span>{endpoints.length}</span>
              </div>
              {endpoints.map((endpoint) => {
                const target = endpointTarget(endpoint)
                return (
                  <button
                    key={endpoint.id}
                    className={
                      active?.category === 'ENDPOINT' && active.id === endpoint.id
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
                    <small className={styles.pathTail} title={endpoint.sourcePath}>
                      {pathTail(endpoint.sourcePath)}
                    </small>
                  </button>
                )
              })}
              {endpoints.length === 0 && <p>No HTTP endpoints in this index.</p>}
            </section>
          </>
        )}
      </div>
      <p className={styles.searchHint}>
        Search covers named functions, methods, constructors, endpoints, and files.
      </p>
    </nav>
  )
}
