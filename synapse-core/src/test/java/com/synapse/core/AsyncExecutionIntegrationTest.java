package com.synapse.core;

import com.synapse.core.graph.Graph;
import com.synapse.core.graph.NodeDefinition;
import com.synapse.core.node.AsyncNode;
import com.synapse.core.node.Node;
import com.synapse.core.runtime.SynapseEngine;
import com.synapse.core.state.ScopeContext;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 异步执行集成测试。
 */
class AsyncExecutionIntegrationTest {

    // 模拟一个耗时的异步节点
    static class SlowAsyncNode implements AsyncNode {
        @Override
        public CompletableFuture<Map<String, Object>> executeAsync(Map<String, Object> inputs) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    // 模拟耗时操作
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                String inputVal = (String) inputs.get("val");
                return Map.of("result", inputVal + "_processed");
            });
        }
    }

    @Test
    void testAsyncExecutionFlow() throws Exception {
        // 1. 构建图
        Graph graph = new Graph("async_test_graph");

        // 节点 1: 异步处理
        NodeDefinition node1 = new NodeDefinition();
        node1.setId("slow_node");
        node1.setType("SlowAsyncNode");
        node1.setInputMappings(Map.of("val", "flow.input_data"));
        node1.setOutputMappings(null); // 默认写入 node scope
        graph.addNode(node1);

        // 边: Start -> Node1 -> End
        graph.addEdge(Graph.START, "slow_node");
        graph.addEdge("slow_node", Graph.END);

        // 2. 引擎配置
        Function<String, Node> nodeFactory = type -> {
            if ("SlowAsyncNode".equals(type))
                return new SlowAsyncNode();
            return null;
        };
        SynapseEngine engine = new SynapseEngine(graph, nodeFactory);

        // 3. 准备数据
        Map<String, Object> flowData = new HashMap<>();
        flowData.put("input_data", "test");

        // 4. 异步执行
        long startTime = System.currentTimeMillis();
        CompletableFuture<ScopeContext> future = engine.executeAsync(new HashMap<>(), flowData);

        // 验证此时 future 可能还未完成 (取决于执行速度，但逻辑上它是非阻塞返回的)
        // assertFalse(future.isDone()); // 这一点很难在单测中百分百保证，取决于调度器，但通常是的

        // 等待结果
        ScopeContext resultContext = future.get(2, TimeUnit.SECONDS);
        long duration = System.currentTimeMillis() - startTime;

        // 5. 验证
        assertTrue(duration >= 100, "执行时间应该至少包含异步节点的耗时");
        assertEquals("test_processed", resultContext.getNodeOutput("slow_node", "result").orElse(null));
    }
}
