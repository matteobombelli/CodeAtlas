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
  ENDPOINT: '#76bfb0',
  CONTROLLER: '#73a6d8',
  SERVICE: '#9b8dd1',
  REPOSITORY: '#e0a96d',
  ENTITY: '#d77c74',
  TEST: '#78b681',
  EXTERNAL: '#75818b',
  METHOD: '#7f98a4',
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
        borderRadius: 12,
        background: '#111d2d',
        color: '#e9f0ed',
        padding: 11,
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
      stroke: edge.confidence >= 0.9 ? '#6f9e9b' : '#7b718e',
      strokeDasharray: edge.confidence < 0.9 ? '6 5' : undefined,
    },
    labelStyle: { fill: '#91a0a7', fontSize: 10 },
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

  return (
    <section className={styles.atlas} aria-label={`Execution graph for ${endpoint.path}`}>
      <header>
        <div>
          <p>{blastRootId ? 'POTENTIAL BLAST RADIUS' : endpoint.httpMethod}</p>
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
          {graph.isLoading && <div className={styles.loading}>Building execution map…</div>}
          <ReactFlow
            nodes={nodes}
            edges={edges}
            fitView
            minZoom={0.2}
            maxZoom={1.8}
            onNodeClick={(_, node) => setSelectedId(node.id)}
          >
            <Background color="#233347" gap={24} />
            <Controls />
            <MiniMap
              nodeColor={(node) => colors[String(node.data.kind)] ?? '#6f8792'}
              maskColor="rgb(5 11 19 / 70%)"
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
                  Show potential blast radius
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
                  theme={themes.nightOwl}
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
            </>
          ) : (
            <p className={styles.emptyInspector}>
              Select a node to inspect source evidence and confidence.
            </p>
          )}
        </aside>
      </div>
    </section>
  )
}
