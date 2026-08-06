import { useEffect, useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Highlight, themes } from 'prism-react-renderer'
import {
  Background,
  Controls,
  MiniMap,
  ReactFlow,
  MarkerType,
  type Edge,
  type Node,
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import {
  repositoryApi,
  type GraphEdge,
  type GraphNode,
  type HttpEndpoint,
} from '../../api/repositories'
import styles from './ExecutionGraphView.module.css'

const colors: Record<string, string> = {
  ENDPOINT: '#2f6684',
  CONTROLLER: '#456f93',
  SERVICE: '#6e6089',
  REPOSITORY: '#8a6333',
  ENTITY: '#94534d',
  TEST: '#4d765b',
  EXTERNAL: '#7a8085',
  METHOD: '#596f7e',
}

async function layout(nodes: GraphNode[], edges: GraphEdge[]) {
  const { default: ELK } = await import('elkjs/lib/elk.bundled.js')
  const elk = new ELK()
  const graph = await elk.layout({
    id: 'root',
    layoutOptions: {
      'elk.algorithm': 'layered',
      'elk.direction': 'DOWN',
      'elk.spacing.nodeNode': '55',
      'elk.layered.spacing.nodeNodeBetweenLayers': '80',
    },
    children: nodes.map((node) => ({ id: node.id, width: 210, height: 72 })),
    edges: edges.map((edge) => ({
      id: edge.id,
      sources: [edge.source],
      targets: [edge.target],
    })),
  })

  return nodes.map<Node>((node) => {
    const positioned = graph.children?.find((child) => child.id === node.id)
    return {
      id: node.id,
      position: { x: positioned?.x ?? 0, y: positioned?.y ?? 0 },
      data: {
        kind: node.kind,
        label: (
          <div className={styles.nodeLabel}>
            <span>{node.kind}</span>
            <strong>{node.label}</strong>
            <small>{node.subtitle}</small>
          </div>
        ),
      },
      style: {
        width: 210,
        minHeight: 72,
        border: `1px solid ${colors[node.kind] ?? colors.METHOD}`,
        borderRadius: 4,
        background: '#fbfaf7',
        color: '#102a4a',
        padding: 11,
        boxShadow: '0 2px 6px rgb(16 42 74 / 8%)',
      },
    }
  })
}

function flowEdges(edges: GraphEdge[]): Edge[] {
  return edges.map((edge) => ({
    id: edge.id,
    source: edge.source,
    target: edge.target,
    label: edge.kind.toLowerCase().replace('_', ' '),
    markerEnd: { type: MarkerType.ArrowClosed },
    animated: false,
    style: {
      stroke: edge.confidence >= 0.9 ? '#6a8797' : '#81768b',
      strokeDasharray: edge.confidence < 0.9 ? '6 5' : undefined,
    },
    labelStyle: { fill: '#607080', fontSize: 10 },
  }))
}

export function ExecutionGraphView({
  repositoryId,
  endpoint,
}: {
  repositoryId: string
  endpoint: HttpEndpoint
}) {
  const [depth, setDepth] = useState(4)
  const [nodes, setNodes] = useState<Node[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [blastRootId, setBlastRootId] = useState<string | null>(null)
  const endpointGraph = useQuery({
    queryKey: ['endpoint-graph', repositoryId, endpoint.id, depth],
    queryFn: () => repositoryApi.endpointGraph(repositoryId, endpoint.id, depth),
  })
  const blastGraph = useQuery({
    queryKey: ['blast-radius', repositoryId, blastRootId, depth],
    queryFn: () => repositoryApi.blastRadius(repositoryId, blastRootId!, depth),
    enabled: blastRootId !== null,
  })
  const graph = blastRootId ? blastGraph : endpointGraph

  useEffect(() => {
    let active = true
    if (graph.data) {
      void layout(graph.data.nodes, graph.data.edges).then((positioned) => {
        if (active) setNodes(positioned)
      })
    }
    return () => {
      active = false
    }
  }, [graph.data])

  const edges = useMemo(() => flowEdges(graph.data?.edges ?? []), [graph.data?.edges])
  const selected = graph.data?.nodes.find((node) => node.id === selectedId)
  const selectedEdges = graph.data?.edges.filter(
    (edge) => edge.source === selectedId || edge.target === selectedId,
  )
  const sourceEnd = selected
    ? Math.min(selected.source.endLine, selected.source.startLine + 80)
    : 1
  const source = useQuery({
    queryKey: [
      'source',
      repositoryId,
      selected?.source.path,
      selected?.source.startLine,
      sourceEnd,
    ],
    queryFn: () =>
      repositoryApi.source(
        repositoryId,
        selected!.source.path,
        selected!.source.startLine,
        sourceEnd,
      ),
    enabled: selected !== undefined,
    retry: false,
  })
  const history = useQuery({
    queryKey: ['symbol-history', repositoryId, selected?.id],
    queryFn: () => repositoryApi.history(repositoryId, selected!.id),
    enabled: selected?.resourceType === 'SYMBOL',
  })

  return (
    <section className={styles.atlas} aria-label={`Execution graph for ${endpoint.path}`}>
      <header>
        <div>
          <p>{blastRootId ? 'Incoming references' : endpoint.httpMethod}</p>
          <h3>{blastRootId ? selected?.label ?? 'Selected symbol' : endpoint.path}</h3>
          <span>
            {endpoint.controller}.{endpoint.method}
          </span>
        </div>
        <div className={styles.graphControls}>
          {blastRootId && (
            <button onClick={() => setBlastRootId(null)}>Back to endpoint</button>
          )}
          <label>
            Depth
            <select value={depth} onChange={(event) => setDepth(Number(event.target.value))}>
              {[2, 3, 4, 5, 6, 7, 8].map((value) => (
                <option key={value}>{value}</option>
              ))}
            </select>
          </label>
        </div>
      </header>

      {graph.isError && <p className={styles.error}>{graph.error.message}</p>}
      {graph.data?.warnings.map((warning) => (
        <p className={styles.warning} key={warning.type}>
          {warning.message}
        </p>
      ))}

      <div className={styles.workspace}>
        <div className={styles.canvas}>
          {graph.isLoading && <div className={styles.loading}>Laying out graph</div>}
          <ReactFlow
            nodes={nodes}
            edges={edges}
            fitView
            minZoom={0.2}
            maxZoom={1.8}
            onNodeClick={(_, node) => setSelectedId(node.id)}
          >
            <Background color="#d8dad8" gap={24} />
            <Controls />
            <MiniMap
              nodeColor={(node) => colors[String(node.data.kind)] ?? '#6f8792'}
              maskColor="rgb(246 244 238 / 72%)"
            />
          </ReactFlow>
        </div>

        <aside className={styles.inspector}>
          {selected ? (
            <>
              <span className={styles.kind}>{selected.kind}</span>
              <h4>{selected.label}</h4>
              <p className={styles.subtitle}>{selected.subtitle}</p>
              <dl>
                <dt>Source</dt>
                <dd>
                  {selected.source.path}:{selected.source.startLine}
                </dd>
                <dt>Relationships</dt>
                <dd>{selectedEdges?.length ?? 0}</dd>
              </dl>
              {selected.resourceType === 'SYMBOL' && (
                <button
                  className={styles.blastButton}
                  onClick={() => {
                    setBlastRootId(selected.id)
                    setSelectedId(selected.id)
                  }}
                >
                  Trace incoming references
                </button>
              )}
              <h5>Evidence</h5>
              {selectedEdges?.map((edge) => (
                <div className={styles.evidence} key={edge.id}>
                  <strong>
                    {edge.kind.replace('_', ' ')} · {edge.confidenceLabel}
                  </strong>
                  <span>{Math.round(edge.confidence * 100)}% confidence</span>
                  <p>{edge.evidence.text}</p>
                  <small>
                    {edge.evidence.path}:{edge.evidence.line}
                  </small>
                </div>
              ))}
              <h5>Source</h5>
              {source.isError && <p className={styles.sourceError}>{source.error.message}</p>}
              {source.data && (
                <Highlight
                  theme={themes.github}
                  code={source.data.content}
                  language="java"
                >
                  {({ tokens, getLineProps, getTokenProps }) => (
                    <pre className={styles.source}>
                      {tokens.map((line, lineIndex) => (
                        <div key={lineIndex} {...getLineProps({ line })}>
                          <span className={styles.lineNumber}>
                            {source.data.startLine + lineIndex}
                          </span>
                          {line.map((token, tokenIndex) => (
                            <span key={tokenIndex} {...getTokenProps({ token })} />
                          ))}
                        </div>
                      ))}
                    </pre>
                  )}
                </Highlight>
              )}
              <h5>Git</h5>
              {history.data && (
                <dl>
                  <dt>Last author</dt>
                  <dd>{history.data.lastAuthorName ?? 'No committed history'}</dd>
                  <dt>Commits</dt>
                  <dd>
                    {history.data.totalCommits} total · {history.data.commitsLast90Days}{' '}
                    in 90 days
                  </dd>
                  <dt>Contributors</dt>
                  <dd>{history.data.contributorCount}</dd>
                  <dt>Last commit</dt>
                  <dd>{history.data.lastCommitSha?.slice(0, 10) ?? '-'}</dd>
                </dl>
              )}
            </>
          ) : (
            <p className={styles.emptyInspector}>
              Select a node to read its source and relationship evidence.
            </p>
          )}
        </aside>
      </div>
    </section>
  )
}
