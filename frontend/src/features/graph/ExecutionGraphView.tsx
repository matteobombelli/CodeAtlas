import {
  useEffect,
  useMemo,
  useRef,
  useState,
  type CSSProperties,
  type KeyboardEvent as ReactKeyboardEvent,
  type PointerEvent as ReactPointerEvent,
} from 'react'
import { useQuery } from '@tanstack/react-query'
import { Highlight, themes } from 'prism-react-renderer'
import {
  Background,
  MarkerType,
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
  FUNCTION: '#596f7e',
  METHOD: '#596f7e',
  CONSTRUCTOR: '#596f7e',
}

function fileName(path: string) {
  return path.split('/').at(-1) ?? path
}

function humanize(value: string) {
  return value.toLowerCase().replaceAll('_', ' ')
}

function clampPanelWidth(width: number, minimum: number, maximum: number) {
  return Math.min(maximum, Math.max(minimum, width))
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
  if (target.category === 'CALLABLE') {
    return repositoryApi.symbolGraph(repositoryId, target.id, depth)
  }
  if (target.category === 'FILE') {
    return repositoryApi.fileGraph(repositoryId, target.id)
  }
  return repositoryApi.endpointGraph(repositoryId, target.id, depth)
}

function TargetPath({ target }: { target: GraphTarget }) {
  const callableKind =
    target.kind === 'CONSTRUCTOR'
      ? 'Constructor'
      : target.kind === 'FUNCTION'
        ? 'Function'
        : 'Function / method'

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
          <span>{target.category === 'CALLABLE' ? callableKind : 'Method'}</span>
          <strong>{target.detail}</strong>
        </li>
      )}
      <li>
        <span>File</span>
        <strong
          className={target.category === 'FILE' ? styles.pathTail : undefined}
          title={target.sourcePath}
        >
          {target.category === 'FILE' ? target.label : fileName(target.sourcePath)}
        </strong>
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
  endpoint: HttpEndpoint | null
  endpoints: HttpEndpoint[]
}) {
  const [depth, setDepth] = useState(3)
  const [nodes, setNodes] = useState<Node[]>([])
  const [flow, setFlow] = useState<ReactFlowInstance | null>(null)
  const [activeTarget, setActiveTarget] = useState<GraphTarget | null>(() =>
    endpoint ? endpointTarget(endpoint) : null,
  )
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [incomingRootId, setIncomingRootId] = useState<string | null>(null)
  const [navigatorWidth, setNavigatorWidth] = useState(274)
  const [inspectorWidth, setInspectorWidth] = useState(320)
  const resize = useRef<{
    panel: 'navigator' | 'inspector'
    pointerId: number
    startX: number
    startWidth: number
  } | null>(null)

  useEffect(() => {
    setActiveTarget(endpoint ? endpointTarget(endpoint) : null)
    setIncomingRootId(null)
  }, [endpoint])

  useEffect(() => {
    setNodes([])
    setSelectedId(null)
  }, [activeTarget?.category, activeTarget?.id, depth, incomingRootId])

  const selectedGraph = useQuery({
    queryKey: [
      'code-graph',
      repositoryId,
      activeTarget?.category,
      activeTarget?.id,
      depth,
    ],
    queryFn: () => targetGraph(repositoryId, activeTarget!, depth),
    enabled: activeTarget !== null && incomingRootId === null,
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
  function selectTarget(target: GraphTarget) {
    setActiveTarget(target)
    setIncomingRootId(target.category === 'CALLABLE' ? target.symbolId : null)
    setSelectedId(null)
  }

  function inspectImpact(node: GraphNode) {
    setActiveTarget({
      category: 'CALLABLE',
      id: node.id,
      symbolId: node.id,
      kind: node.kind,
      label: node.label,
      detail: node.subtitle,
      sourcePath: node.source.path,
      startLine: node.source.startLine,
      endLine: node.source.endLine,
      httpMethod: null,
    })
    setIncomingRootId(node.id)
    setSelectedId(null)
  }

  function fitCurrentGraph() {
    requestAnimationFrame(() => {
      void flow?.fitView({ padding: 0.16, duration: 160 })
    })
  }

  function startPanelResize(
    panel: 'navigator' | 'inspector',
    event: ReactPointerEvent<HTMLDivElement>,
  ) {
    event.preventDefault()
    event.currentTarget.setPointerCapture(event.pointerId)
    resize.current = {
      panel,
      pointerId: event.pointerId,
      startX: event.clientX,
      startWidth: panel === 'navigator' ? navigatorWidth : inspectorWidth,
    }
  }

  function movePanelResize(event: ReactPointerEvent<HTMLDivElement>) {
    const active = resize.current
    if (!active || active.pointerId !== event.pointerId) return
    const movement = event.clientX - active.startX
    if (active.panel === 'navigator') {
      setNavigatorWidth(clampPanelWidth(active.startWidth + movement, 220, 480))
    } else {
      setInspectorWidth(clampPanelWidth(active.startWidth - movement, 260, 520))
    }
  }

  function stopPanelResize(event: ReactPointerEvent<HTMLDivElement>) {
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId)
    }
    resize.current = null
    fitCurrentGraph()
  }

  function resizePanelWithKeyboard(
    panel: 'navigator' | 'inspector',
    event: ReactKeyboardEvent<HTMLDivElement>,
  ) {
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return
    event.preventDefault()
    const movement = event.key === 'ArrowRight' ? 16 : -16
    if (panel === 'navigator') {
      setNavigatorWidth((width) => clampPanelWidth(width + movement, 220, 480))
    } else {
      setInspectorWidth((width) => clampPanelWidth(width - movement, 260, 520))
    }
    fitCurrentGraph()
  }

  return (
    <section
      className={styles.atlas}
      aria-label={activeTarget ? `Code graph for ${activeTarget.label}` : 'Code graph'}
    >
      <header className={styles.graphHeader}>
        {activeTarget ? (
          <TargetPath target={activeTarget} />
        ) : (
          <div className={styles.emptyTarget}>
            <span>Code explorer</span>
            <strong>Search for the code you are changing</strong>
          </div>
        )}
        <div className={styles.graphControls}>
          {activeTarget?.symbolId && (
            <div className={styles.viewSwitch} aria-label="Graph view">
              <button
                className={!incomingRootId ? styles.activeView : undefined}
                type="button"
                aria-pressed={!incomingRootId}
                onClick={() => setIncomingRootId(null)}
              >
                Dependencies
                <small>what it calls</small>
              </button>
              <button
                className={incomingRootId ? styles.activeView : undefined}
                type="button"
                aria-pressed={Boolean(incomingRootId)}
                onClick={() => setIncomingRootId(activeTarget.symbolId)}
              >
                Blast radius
                <small>what may depend on it</small>
              </button>
            </div>
          )}
          {activeTarget && activeTarget.category !== 'FILE' && (
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

      <div
        className={styles.explorerGrid}
        style={{ '--navigator-width': `${navigatorWidth}px` } as CSSProperties}
      >
        <CodeNavigator
          repositoryId={repositoryId}
          endpoints={endpoints}
          active={activeTarget}
          onSelect={selectTarget}
        />
        <div
          className={styles.resizeHandle}
          role="separator"
          aria-label="Resize code browser"
          aria-orientation="vertical"
          aria-valuemin={220}
          aria-valuemax={480}
          aria-valuenow={navigatorWidth}
          tabIndex={0}
          onDoubleClick={() => {
            setNavigatorWidth(274)
            fitCurrentGraph()
          }}
          onKeyDown={(event) => resizePanelWithKeyboard('navigator', event)}
          onPointerDown={(event) => startPanelResize('navigator', event)}
          onPointerMove={movePanelResize}
          onPointerUp={stopPanelResize}
          onPointerCancel={stopPanelResize}
        />

        <div className={styles.graphArea}>
          <div className={styles.graphMessages}>
            {graph.isError && <p className={styles.error}>{graph.error.message}</p>}
            {graph.data?.warnings.map((warning) => (
              <p className={styles.warning} key={warning.type}>
                {warning.message}
              </p>
            ))}
          </div>

          <div
            className={styles.workspace}
            style={{ '--inspector-width': `${inspectorWidth}px` } as CSSProperties}
          >
            <div className={styles.canvas}>
              {!activeTarget ? (
                <div className={styles.emptyCanvas}>
                  <strong>Start with a function, method, endpoint, or file.</strong>
                  <p>
                    Search the current index to inspect dependencies and potential
                    change impact.
                  </p>
                </div>
              ) : (
                <>
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
                  </ReactFlow>
                  <div className={styles.edgeLegend} aria-label="Relationship confidence">
                    <span><i /> exact or high confidence</span>
                    <span><i className={styles.dashedLine} /> inferred</span>
                  </div>
                </>
              )}
            </div>

            <div
              className={styles.resizeHandle}
              role="separator"
              aria-label="Resize selection details"
              aria-orientation="vertical"
              aria-valuemin={260}
              aria-valuemax={520}
              aria-valuenow={inspectorWidth}
              tabIndex={0}
              onDoubleClick={() => {
                setInspectorWidth(320)
                fitCurrentGraph()
              }}
              onKeyDown={(event) => resizePanelWithKeyboard('inspector', event)}
              onPointerDown={(event) => startPanelResize('inspector', event)}
              onPointerMove={movePanelResize}
              onPointerUp={stopPanelResize}
              onPointerCancel={stopPanelResize}
            />

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
                  {selected.resourceType === 'SYMBOL' && selected.id !== incomingRootId && (
                    <button
                      className={styles.incomingButton}
                      type="button"
                      onClick={() => inspectImpact(selected)}
                    >
                      Inspect this symbol's blast radius
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
