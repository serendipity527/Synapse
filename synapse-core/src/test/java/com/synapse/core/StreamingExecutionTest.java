package com.synapse.core;

import com.synapse.core.graph.Graph;
import com.synapse.core.graph.NodeDefinition;
import com.synapse.core.node.DataStreamer;
import com.synapse.core.node.Node;
import com.synapse.core.node.StreamingNode;
import com.synapse.core.runtime.SynapseEngine;
import com.synapse.core.state.ScopeContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 流式输出集成测试 (Phase 5)。
 */
public class StreamingExecutionTest {

    /**
     * 模拟 LLM 流式输出的节点。
     */
    static class MockLlmStreamingNode implements StreamingNode {
        @Override
        public CompletableFuture<Map<String, Object>> executeStream(Map<String, Object> inputs, DataStreamer streamer) {
            return CompletableFuture.supplyAsync(() -> {
                String prompt = (String) inputs.getOrDefault("prompt", "Hello");

                // 模拟 LLM Token 流式输出
                String[] tokens = { "Hello", " ", "World", "!", " I", " am", " Synapse", "." };
                StringBuilder fullResponse = new StringBuilder();

                for (String token : tokens) {
                    streamer.stream(token); // 发送每个 token
                    fullResponse.append(token);
                    try {
                        Thread.sleep(10); // 模拟网络延迟
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                return Map.of("response", fullResponse.toString());
            });
        }
    }

    @Test
    void testStreamingNodeExecution() throws Exception {
        // 1. 构建图
        Graph graph = new Graph("streaming_test");

        NodeDefinition llmNode = new NodeDefinition();
        llmNode.setId("llm_node");
        llmNode.setType("MockLlmStreamingNode");
        llmNode.setInputMappings(Map.of("prompt", "flow.user_input"));
        graph.addNode(llmNode);

        graph.addEdge(Graph.START, "llm_node");
        graph.addEdge("llm_node", Graph.END);

        // 2. 收集流式输出
        List<Object> streamedChunks = Collections.synchronizedList(new ArrayList<>());

        Function<String, Node> factory = type -> {
            if ("MockLlmStreamingNode".equals(type))
                return new MockLlmStreamingNode();
            return null;
        };

        SynapseEngine engine = new SynapseEngine(graph, factory);

        // 3. 执行
        Map<String, Object> flowData = Map.of("user_input", "Tell me something");
        ScopeContext context = engine.execute(Collections.emptyMap(), flowData);

        // 4. 验证最终结果
        String response = (String) context.getNodeOutput("llm_node", "response").orElse(null);
        assertEquals("Hello World! I am Synapse.", response);

        // 注意：当前测试中 streamer 没有被外部传入（引擎默认使用空 streamer），
        // 流式数据被丢弃。完整的流式测试需要扩展 SynapseEngine 以支持全局 StreamListener。
        // 这个测试主要验证 StreamingNode 接口的正确性和兼容性。
    }
}
