package com.synapse.core.runtime;

import com.synapse.core.graph.EdgeDefinition;
import com.synapse.core.graph.Graph;
import com.synapse.core.graph.NodeDefinition;
import com.synapse.core.node.Node;
import com.synapse.core.state.DefaultScopeContext;
import com.synapse.core.state.ScopeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * SynapseEngine 是运行图（Graph）的主要执行引擎。
 * <p>
 * 它根据边（Edge）的定义协调节点的顺序执行，并在整个运行过程中管理作用域上下文（ScopeContext）。
 * <p>
 * Phase 4 更新：支持并行执行 (Parallel Execution)
 */
public class SynapseEngine {

    private static final Logger log = LoggerFactory.getLogger(SynapseEngine.class);
    private static final int MAX_ITERATIONS = 100;

    private final Graph graph;
    private final Function<String, Node> nodeFactory;
    private final NodeRunner nodeRunner;
    private final ConditionEvaluator conditionEvaluator;

    /**
     * 为给定的图创建一个 SynapseEngine。
     */
    public SynapseEngine(Graph graph, Function<String, Node> nodeFactory) {
        this.graph = Objects.requireNonNull(graph, "图（Graph）不能为空");
        this.nodeFactory = Objects.requireNonNull(nodeFactory, "节点工厂（NodeFactory）不能为空");
        this.nodeRunner = new NodeRunner();
        this.conditionEvaluator = new ConditionEvaluator();
    }

    /**
     * 异步执行图。
     */
    public CompletableFuture<ScopeContext> executeAsync(Map<String, Object> globalData, Map<String, Object> flowData) {
        log.info("开始异步执行图: {}", graph.getName());

        ScopeContext context = new DefaultScopeContext(globalData, flowData);
        String currentNodeId = graph.findEntryNodeId();

        if (currentNodeId == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("图中未找到入口点。请确保存在从 __start__ 出发的边。"));
        }

        return executeNodeRecursive(currentNodeId, 0, context)
                .thenApply(v -> {
                    log.info("图执行已完成。");
                    return context;
                });
    }

    /**
     * 递归执行节点。
     */
    private CompletableFuture<Void> executeNodeRecursive(String currentNodeId, int iteration, ScopeContext context) {
        // 如果是特殊结束节点，直接返回完成
        if (currentNodeId == null || Graph.END.equals(currentNodeId)) {
            return CompletableFuture.completedFuture(null);
        }

        if (iteration > MAX_ITERATIONS) {
            return CompletableFuture
                    .failedFuture(new IllegalStateException("超过最大迭代次数 (" + MAX_ITERATIONS + ")。图中可能存在死循环。"));
        }

        log.debug("正在执行节点: {}", currentNodeId);

        NodeDefinition nodeDef = graph.getNode(currentNodeId);
        if (nodeDef == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("未找到节点: " + currentNodeId));
        }

        Node nodeInstance = nodeFactory.apply(nodeDef.getType());
        if (nodeInstance == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("节点工厂对类型返回了 null: " + nodeDef.getType()));
        }

        // 异步运行当前节点
        return nodeRunner.runAsync(nodeDef, nodeInstance, context)
                .thenCompose(outputs -> {
                    try {
                        // 节点运行完成后，计算所有可能的下一个节点
                        List<String> nextNodeIds = resolveNextNodes(currentNodeId, context);

                        if (nextNodeIds.isEmpty()) {
                            log.debug("节点 {} 执行完毕且无后续节点。", currentNodeId);
                            return CompletableFuture.completedFuture(null);
                        }

                        // 并行执行所有后续节点
                        if (nextNodeIds.size() > 1) {
                            log.info("节点 {} 触发并行分支: {}", currentNodeId, nextNodeIds);
                        }

                        List<CompletableFuture<Void>> futures = nextNodeIds.stream()
                                .map(nextId -> executeNodeRecursive(nextId, iteration + 1, context))
                                .collect(Collectors.toList());

                        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                    } catch (Exception e) {
                        return CompletableFuture.failedFuture(e);
                    }
                });
    }

    /**
     * 使用给定的初始输入执行图（同步阻塞）。
     */
    public ScopeContext execute(Map<String, Object> globalData, Map<String, Object> flowData) throws Exception {
        try {
            return executeAsync(globalData, flowData).join();
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw e;
        }
    }

    /**
     * 仅使用流程数据执行图（无全局配置）。
     */
    public ScopeContext execute(Map<String, Object> flowData) throws Exception {
        return execute(new HashMap<>(), flowData);
    }

    /**
     * 解析下一个节点 ID 列表，支持多条出边（并行）和条件边。
     */
    private List<String> resolveNextNodes(String currentNodeId, ScopeContext context) {
        List<EdgeDefinition> edges = graph.findAllEdgesFrom(currentNodeId);
        List<String> validTargets = new ArrayList<>();

        if (edges.isEmpty()) {
            return validTargets;
        }

        for (EdgeDefinition edge : edges) {
            String target = null;
            if (edge.isConditional()) {
                // 条件边：使用 ConditionEvaluator 评估
                target = conditionEvaluator.evaluate(
                        edge.getConditionType(),
                        edge.getConditionExpression(),
                        edge.getConditionMappings(),
                        context);
                // 注意：条件不满足时可能会返回 null，或者未配置的 mapping 返回 null
            } else {
                // 简单边
                target = edge.getTargetNodeId();
            }

            if (target != null) {
                // 记录日志：如果是条件分支，记录路由结果
                if (edge.isConditional()) {
                    log.debug("条件边路由: {} -> {}", currentNodeId, target);
                }
                validTargets.add(target);
            }
        }

        // 严格模式：如果有出边但没有任何一个命中，视为异常（防止死胡同）
        if (!edges.isEmpty() && validTargets.isEmpty()) {
            throw new IllegalStateException("条件边评估失败：节点 '" + currentNodeId + "' 有出边定义，但没有匹配到任何目标节点（条件均未满足，且无默认边）。");
        }

        // 去重 (防止配置多条相同的边导致重复执行)
        return validTargets.stream().distinct().collect(Collectors.toList());
    }
}
