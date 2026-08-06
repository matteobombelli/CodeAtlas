import { useEffect, useMemo, useState, type CSSProperties } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Highlight, themes } from 'prism-react-renderer'
import {
  Background,
  Controls,
  MarkerType,
  MiniMap,
  ReactFlow,
  type Edge,
  type Node,
  type ReactFlowInstance,
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import {
  repositoryApi,
  type GraphEdge,
  type GraphNode,
  type HttpEndpoint,
} from '../../api/repositories'
import { CodeNavigator } from './CodeNavigator'
import { endpointTarget, type GraphTarget } from './graphTargets'
import styles from './ExecutionGraphView.module.css'

const nodeColors: Record<string, string> = {
  ENDPOINT: '#2f6684',
  CONTROLLER: '#456f93',
  SERVICE: '#6e6089',
  REPOSITORY: '#8a6333',
  ENTITY: '#94534d',
  TEST: '#4d765b',
  EXTERNAL: '#7a8085',
  FILE: '#426b66',
  METHOD: '#596f7e',
  CONSTRUCTOR: '#596f7e',
}

function fileName(path: string) {
  return path.split('/').at(-1) ?? path
}

function humanize(value: string) {
  return value.toLowerCase().replaceAll('_', ' ')
}

async function layout(nodes: GraphNode[], edges: GraphEdge[]) {
  const { default: ELK } = await import('elkjs/lib/elk.bundled.js')
  const elk = new ELK()
  const fileGraph = nodes.some((node) => node.resourceType === 'FILE')
  const graph = await elk.layout({
    id: 'root',
    layoutOptions: {
      'elk.algorithm': 'layered',
      'elk.direction': fileGraph ? 'RIGHT' : 'DOWN',
      'elk.edgeRouting': 'ORTHOGONAL',
      'elk.spacing.nodeNode': fileGraph ? '24' : '44',
      'elk.layered.spacing.nodeNodeBetweenLayers': '72',
      'elk.layered.nodePlacement.strategy': 'NETWORK_SIMPLEX',
    },
    children: nodes.map((node) => ({ id: node.id, width: 228, height: 78 })),
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
      className: styles.graphNode,
      style: {
        '--node-accent': nodeColors[node.kind] ?? nodeColors.METHOD,
      } as CSSProperties,
      data: {
        kind: node.kind,
        label: (
          <div className={styles.nodeLabel}>
            <span>{humanize(node.kind)}</span>
            <strong>{node.label}</strong>
            <small title={node.source.path}>{fileName(node.source.path)}</small>
          </div>
        ),
      },
    }
  })
}

function flowEdges(edges: GraphEdge[]): Edge[] {
  return edges.map((edge) => {
    const exact = edge.confidence >= 0.9
    const color = exact ? '#6a8797' : '#81768b'
    return {
      id: edge.id,
      source: edge.source,
      target: edge.target,
      label: edge.kind === 'DECLARES' ? undefined : humanize(edge.kind),
      markerEnd: { type: MarkerType.ArrowClosed, color },
      className: exact ? styles.exactEdge : styles.uncertainEdge,
      style: { stroke: color, strokeDasharray: exact ? undefined : '6 5' },
      labelStyle: { fill: '#5f6d79', fontSize: 10, fontWeight: 500 },
      labelBgStyle: { fill: '#f9f8f4', fillOpacity: 0.92 },
      labelBgPadding: [5, 3] as [number, number],
      labelBgBorderRadius: 2,
    }
  })
}

function targetGraph(
  repositoryId: string,
  target: GraphTarget,
  depth: number,
) {
  if (target.category === 'METHOD') {
    return repositoryApi.symbolGraph(repositoryId, target.id, depth)
  }
  if (target.category === 'FILE') {
    return repositoryApi.fileGraph(repositoryId, target.id)
  }
  return repositoryApi.endpointGraph(repositoryId, target.id, depth)
}

function TargetPath({ target }: { target: GraphTarget }) {
  return (
    <ol className={styles.targetPath} aria-label="Current selection">
      {target.category === 'ENDPOINT' && (
        <li>
          <span>Endpoint</span>
          <strong>
            {target.httpMethod} {target.label}
          </strong>
        </li>
      )}
      {target.category !== 'FILE' && (
        <li>
          <span>Method</span>
          <strong>{target.detail}</strong>
        </li>
      )}
      <li>
        <span>File</span>
        <strong>{target.category === 'FILE' ? target.label : fileName(target.sourcePath)}</strong>
      </li>
    </ol>
  )
}

export function ExecutionGraphView({
  repositoryId,
  endpoint,
  endpoints,
}: {
  repositoryId: string
  endpoint: HttpEndpoint
  endpoints: HttpEndpoint[]
}) {
  const [depth, setDepth] = useState(3)
  const [nodes, setNodes] = useState<Node[]>([])
  const [flow, setFlow] = useState<ReactFlowInstance | null>(null)
  const [activeTarget, setActiveTarget] = useState<GraphTarget>(() =>
    endpointTarget(endpoint),
  )
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [incomingRootId, setIncomingRootId] = useState<string | null>(null)

  useEffect(() => {
    setActiveTarget(endpointTarget(endpoint))
    setIncomingRootId(null)
  }, [endpoint])

  const selectedGraph = useQuery({
    queryKey: [
      'code-graph',
      repositoryId,
      activeTarget.category,
      activeTarget.id,
      depth,
    ],
    queryFn: () => targetGraph(repositoryId, activeTarget, depth),
  })
  const incomingGraph = useQuery({
    queryKey: ['incoming-graph', repositoryId, incomingRootId, depth],
    queryFn: () => repositoryApi.blastRadius(repositoryId, incomingRootId!, depth),
    enabled: incomingRootId !== null,
  })
  const graph = incomingRootId ? incomingGraph : selectedGraph

  useEffect(() => {
    let active = true
    if (graph.data) {
      void layout(graph.data.nodes, graph.data.edges).then((positioned) => {
        if (!active) return
        setNodes(positioned)
        setSelectedId(graph.data.rootNodeId)
      })
    }
    return () => {
      active = false
    }
  }, [graph.data])

  useEffect(() => {
    if (!flow || nodes.length === 0) return
    const frame = requestAnimationFrame(() => {
      void flow.fitView({ padding: 0.16, duration: 220 })
    })
    return () => cancelAnimationFrame(frame)
  }, [flow, nodes])

  const edges = useMemo(() => flowEdges(graph.data?.edges ?? []), [graph.data?.edges])
  const selected = graph.data?.nodes.find((node) => node.id === selectedId)
  const selectedEdges = graph.data?.edges.filter(
    (edge) => edge.source === selectedId || edge.target === selectedId,
  )
  const sourceEnd = selected
    ? Math.min(selected.source.endLine, selected.source.startLine + 119)
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
    enabled: selected !== undefined && selected.resourceType !== 'EXTERNAL',
    retry: false,
  })
  const history = useQuery({
    queryKey: ['symbol-history', repositoryId, selected?.id],
    queryFn: () => repositoryApi.history(repositoryId, selected!.id),
    enabled: selected?.resourceType === 'SYMBOL',
  })

  function selectTarget(target: GraphTarget) {
    setActiveTarget(target)
    setIncomingRootId(null)
    setSelectedId(null)
  }

  return (
    <section className={styles.atlas} aria-label={`Code graph for ${activeTarget.label}`}>
      <header className={styles.graphHeader}>
        <TargetPath target={activeTarget} />
        <div className={styles.graphControls}>
          {incomingRootId && (
            <button type="button" onClick={() => setIncomingRootId(null)}>
              Back to forward graph
            </button>
          )}
          {activeTarget.category !== 'FILE' && (
            <label>
              Depth
              <select
                value={depth}
                onChange={(event) => setDepth(Number(event.target.value))}
              >
                {[2, 3, 4, 5, 6, 7, 8].map((value) => (
                  <option key={value}>{value}</option>
                ))}
              </select>
            </label>
          )}
        </div>
      </header>

      <div className={styles.explorerGrid}>
        <CodeNavigator
          repositoryId={repositoryId}
          endpoints={endpoints}
          active={activeTarget}
          onSelect={selectTarget}
        />

        <div className={styles.graphArea}>
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
                fitViewOptions={{ padding: 0.16 }}
                minZoom={0.25}
                maxZoom={1.65}
                nodesConnectable={false}
                onInit={setFlow}
                onNodeClick={(_, node) => setSelectedId(node.id)}
              >
                <Background color="#d8dad8" gap={24} size={1} />
                <Controls showInteractive={false} />
                <MiniMap
                  nodeColor={(node) => nodeColors[String(node.data.kind)] ?? '#6f8792'}
                  maskColor="rgb(246 244 238 / 76%)"
                  pannable
                  zoomable
                />
              </ReactFlow>
              <div className={styles.edgeLegend} aria-label="Relationship confidence">
                <span><i /> exact or high confidence</span>
                <span><i className={styles.dashedLine} /> inferred</span>
              </div>
            </div>

            <aside className={styles.inspector} aria-label="Selection details">
              {selected ? (
                <>
                  <span className={styles.kind}>{humanize(selected.kind)}</span>
                  <h4>{selected.label}</h4>
                  <p className={styles.subtitle}>{selected.subtitle}</p>
                  <dl>
                    <dt>File</dt>
                    <dd>{selected.source.path}</dd>
                    <dt>Lines</dt>
                    <dd>
                      {selected.source.startLine} to {selected.source.endLine}
                    </dd>
                    <dt>Relationships in view</dt>
                    <dd>{selectedEdges?.length ?? 0}</dd>
                  </dl>
                  {selected.resourceType === 'SYMBOL' && (
                    <button
                      className={styles.incomingButton}
                      type="button"
                      onClick={() => setIncomingRootId(selected.id)}
                    >
                      Trace incoming references
                    </button>
                  )}

                  <h5>Relationships</h5>
                  {selectedEdges?.map((edge) => (
                    <div className={styles.evidence} key={edge.id}>
                      <div>
                        <strong>{humanize(edge.kind)}</strong>
                        <span>{edge.confidenceLabel.toLowerCase()}</span>
                      </div>
                      <p>{edge.evidence.text}</p>
                      <small>
                        {Math.round(edge.confidence * 100)}% at {fileName(edge.evidence.path)}:
                        {edge.evidence.line}
                      </small>
                    </div>
                  ))}
                  {selectedEdges?.length === 0 && (
                    <p className={styles.inspectorNote}>No relationships touch this node.</p>
                  )}

                  <h5>Source</h5>
                  {source.isError && (
                    <p className={styles.sourceError}>{source.error.message}</p>
                  )}
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

                  {history.data && (
                    <>
                      <h5>Git</h5>
                      <dl>
                        <dt>Last author</dt>
                        <dd>{history.data.lastAuthorName ?? 'No committed history'}</dd>
                        <dt>Commits</dt>
                        <dd>
                          {history.data.totalCommits} total,{' '}
                          {history.data.commitsLast90Days} in 90 days
                        </dd>
                        <dt>Contributors</dt>
                        <dd>{history.data.contributorCount}</dd>
                        <dt>Last commit</dt>
                        <dd>{history.data.lastCommitSha?.slice(0, 10) ?? '-'}</dd>
                      </dl>
                    </>
                  )}
                </>
              ) : (
                <p className={styles.emptyInspector}>
                  Select a node to read its source and relationship evidence.
                </p>
              )}
            </aside>
          </div>
        </div>
      </div>
    </section>
  )
}
