package com.synapse.core.graph;

import java.util.*;

/**
 * Graph 代表一个完整的工作流定义，包含节点和边。
 * <p>
 * 它提供了查找入口点和遍历图的实用方法。
 */
public class Graph {

    public static final String START = "__start__";
    public static final String END = "__end__";

    private String name;
    private Map<String, NodeDefinition> nodes = new LinkedHashMap<>();
    private List<EdgeDefinition> edges = new ArrayList<>();

    public Graph() {
    }

    public Graph(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void addNode(NodeDefinition node) {
        Objects.requireNonNull(node, "NodeDefinition 不能为空");
        Objects.requireNonNull(node.getId(), "节点 ID 不能为空");
        nodes.put(node.getId(), node);
    }

    public NodeDefinition getNode(String nodeId) {
        return nodes.get(nodeId);
    }

    public Collection<NodeDefinition> getNodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    public void addEdge(EdgeDefinition edge) {
        Objects.requireNonNull(edge, "EdgeDefinition 不能为空");
        edges.add(edge);
    }

    public void addEdge(String source, String target) {
        addEdge(new EdgeDefinition(source, target));
    }

    public List<EdgeDefinition> getEdges() {
        return Collections.unmodifiableList(edges);
    }

    /**
     * 查找入口节点 ID（从 START 连接的节点）。
     *
     * @return 入口节点 ID，如果未找到则返回 null
     */
    public String findEntryNodeId() {
        for (EdgeDefinition edge : edges) {
            if (START.equals(edge.getSourceNodeId())) {
                return edge.getTargetNodeId();
            }
        }
        return null;
    }

    /**
     * 根据源节点 ID 查找下一条边的定义。
     * <p>
     * 返回边定义而不是直接返回目标节点 ID，
     * 因为条件边需要额外的评估逻辑。
     *
     * @param sourceNodeId 当前节点 ID
     * @return 边定义，如果未找到则返回 null
     */
    public EdgeDefinition findEdgeFrom(String sourceNodeId) {
        for (EdgeDefinition edge : edges) {
            if (Objects.equals(edge.getSourceNodeId(), sourceNodeId)) {
                return edge;
            }
        }
        return null;
    }

    /**
     * 根据源节点 ID 查找所有出边的定义（用于并行执行）。
     *
     * @param sourceNodeId 当前节点 ID
     * @return 边定义列表，从不返回 null
     */
    public List<EdgeDefinition> findAllEdgesFrom(String sourceNodeId) {
        List<EdgeDefinition> matchingEdges = new ArrayList<>();
        for (EdgeDefinition edge : edges) {
            if (Objects.equals(edge.getSourceNodeId(), sourceNodeId)) {
                matchingEdges.add(edge);
            }
        }
        return matchingEdges;
    }

    /**
     * 根据源节点 ID 查找下一个节点 ID（仅适用于简单边）。
     * <p>
     * 注意：对于条件边，请使用 findEdgeFrom() 并结合 ConditionEvaluator。
     *
     * @param sourceNodeId 当前节点 ID
     * @return 下一个节点 ID，如果是终点则返回 null/END
     * @deprecated 推荐使用 findEdgeFrom() 以支持条件边
     */
    @Deprecated
    public String findNextNodeId(String sourceNodeId) {
        EdgeDefinition edge = findEdgeFrom(sourceNodeId);
        if (edge == null) {
            return null;
        }
        if (edge.isConditional()) {
            throw new IllegalStateException(
                    "节点 '" + sourceNodeId + "' 使用条件边，请使用 SynapseEngine 进行评估。");
        }
        return edge.getTargetNodeId();
    }

    @Override
    public String toString() {
        return "Graph{" +
                "name='" + name + '\'' +
                ", nodes=" + nodes.keySet() +
                ", edges=" + edges +
                '}';
    }
}
