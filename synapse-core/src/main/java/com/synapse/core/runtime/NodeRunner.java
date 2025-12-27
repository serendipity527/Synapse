package com.synapse.core.runtime;

import com.synapse.core.graph.NodeDefinition;
import com.synapse.core.node.Node;
import com.synapse.core.state.ScopeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * NodeRunner 负责使用正确的输入/输出映射执行节点（Node）。
 * <p>
 * 它充当 ScopeContext 与 Node 纯逻辑之间的桥梁：
 * <ol>
 * <li>执行前：将 Context 中的输入映射到 Node 参数。</li>
 * <li>执行中：调用 Node.execute()。</li>
 * <li>执行后：将输出分发到相应的 Context 作用域。</li>
 * </ol>
 */
public class NodeRunner {

    private static final Logger log = LoggerFactory.getLogger(NodeRunner.class);

    /**
     * 异步运行节点。
     *
     * @param definition   节点的元数据
     * @param nodeInstance 节点实例
     * @param context      执行上下文
     * @return 包含节点输出的 CompletableFuture
     */
    public CompletableFuture<Map<String, Object>> runAsync(NodeDefinition definition, Node nodeInstance,
            ScopeContext context) {
        return runAsync(definition, nodeInstance, context, null);
    }

    /**
     * 异步运行节点，支持流式输出。
     *
     * @param definition   节点的元数据
     * @param nodeInstance 节点实例
     * @param context      执行上下文
     * @param streamer     流式回调（可为 null）
     * @return 包含节点输出的 CompletableFuture
     */
    public CompletableFuture<Map<String, Object>> runAsync(NodeDefinition definition, Node nodeInstance,
            ScopeContext context, com.synapse.core.node.DataStreamer streamer) {
        Objects.requireNonNull(definition, "NodeDefinition 不能为空");
        Objects.requireNonNull(nodeInstance, "节点实例不能为空");
        Objects.requireNonNull(context, "ScopeContext 不能为空");

        String nodeId = definition.getId();
        log.debug("正在运行节点 (Async): {}", nodeId);

        // 1. 映射输入
        Map<String, Object> inputs = mapInputs(definition.getInputMappings(), context);
        log.trace("节点 {} 输入: {}", nodeId, inputs);

        // 使用安全的空 Streamer 防止 NPE
        com.synapse.core.node.DataStreamer safeStreamer = streamer != null ? streamer : content -> {
        };

        // 2. 执行节点 (异步/流式)
        CompletableFuture<Map<String, Object>> futureResult;
        if (nodeInstance instanceof com.synapse.core.node.StreamingNode) {
            futureResult = ((com.synapse.core.node.StreamingNode) nodeInstance).executeStream(inputs, safeStreamer);
        } else if (nodeInstance instanceof com.synapse.core.node.AsyncNode) {
            futureResult = ((com.synapse.core.node.AsyncNode) nodeInstance).executeAsync(inputs);
        } else {
            // 同步节点包装
            futureResult = CompletableFuture.supplyAsync(() -> {
                try {
                    return nodeInstance.execute(inputs);
                } catch (Exception e) {
                    throw new java.util.concurrent.CompletionException(e);
                }
            });
        }

        // 3. 执行完成后分发输出，并透传结果
        return futureResult.thenApply(outputs -> {
            log.trace("节点 {} 输出: {}", nodeId, outputs);
            dispatchOutputs(nodeId, definition.getOutputMappings(), outputs, context);
            return outputs;
        });
    }

    /**
     * 同步运行节点 (保留以向后兼容).
     */
    public Map<String, Object> run(NodeDefinition definition, Node nodeInstance, ScopeContext context)
            throws Exception {
        try {
            return runAsync(definition, nodeInstance, context).join();
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw e;
        }
    }

    /**
     * 根据输入映射表达式从上下文中映射输入。
     */
    private Map<String, Object> mapInputs(Map<String, String> inputMappings, ScopeContext context) {
        if (inputMappings == null || inputMappings.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, Object> inputs = new HashMap<>();
        for (Map.Entry<String, String> entry : inputMappings.entrySet()) {
            String paramName = entry.getKey();
            String expression = entry.getValue();

            Object value = evaluateExpression(expression, context);
            inputs.put(paramName, value);
        }
        return inputs;
    }

    /**
     * 解析简单表达式以从上下文中检索值。
     */
    private Object evaluateExpression(String expression, ScopeContext context) {
        if (expression == null || expression.isBlank()) {
            return null;
        }

        String[] parts = expression.split("\\.", 3);
        if (parts.length < 2) {
            log.warn("表达式格式无效: {}。预期为 'scope.key' 或 'nodes.nodeId.key'", expression);
            return null;
        }

        String scope = parts[0];
        switch (scope) {
            case "global":
                return context.getGlobal(parts[1]).orElse(null);
            case "flow":
                return context.getFlow(parts[1]).orElse(null);
            case "nodes":
                if (parts.length < 3) {
                    log.warn("nodes 表达式无效: {}。预期为 'nodes.nodeId.key'", expression);
                    return null;
                }
                return context.getNodeOutput(parts[1], parts[2]).orElse(null);
            default:
                log.warn("表达式中未知的作用域: {}", scope);
                return null;
        }
    }

    /**
     * 将节点输出分发到相应的作用域上下文。
     */
    private void dispatchOutputs(String nodeId, List<NodeDefinition.OutputMapping> outputMappings,
            Map<String, Object> outputs, ScopeContext context) {
        if (outputs == null || outputs.isEmpty()) {
            return;
        }

        // 如果没有显式的输出映射，默认将所有输出放入节点作用域
        if (outputMappings == null || outputMappings.isEmpty()) {
            for (Map.Entry<String, Object> entry : outputs.entrySet()) {
                context.putNodeOutput(nodeId, entry.getKey(), entry.getValue());
            }
            return;
        }

        // 处理显式输出映射
        for (NodeDefinition.OutputMapping mapping : outputMappings) {
            String sourceKey = mapping.getSourceKey();
            Object value = outputs.get(sourceKey);

            if (value == null) {
                log.trace("在节点 {} 结果中未找到输出键 '{}'，跳过。", nodeId, sourceKey);
                continue;
            }

            String targetScope = mapping.getTargetScope();
            String targetKey = mapping.getTargetKey();

            if (targetScope == null || targetScope.isBlank() || "node".equalsIgnoreCase(targetScope)) {
                // 默认：输出到当前节点的命名空间
                context.putNodeOutput(nodeId, targetKey, value);
            } else if ("flow".equalsIgnoreCase(targetScope)) {
                context.putFlow(targetKey, value);
            } else if ("global".equalsIgnoreCase(targetScope)) {
                log.warn("限制从节点 {} 写入全局作用域。键: {}", nodeId, targetKey);
            } else {
                log.warn("节点 {} 的输出映射中未知目标作用域 '{}'", nodeId, targetScope);
            }
        }
    }
}
