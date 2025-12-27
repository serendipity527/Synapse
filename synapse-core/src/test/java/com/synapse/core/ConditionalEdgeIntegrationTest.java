package com.synapse.core;

import com.synapse.core.config.YamlGraphBuilder;
import com.synapse.core.graph.Graph;
import com.synapse.core.node.Node;
import com.synapse.core.runtime.SynapseEngine;
import com.synapse.core.state.ScopeContext;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 条件边功能的集成测试 (Phase 2)。
 */
class ConditionalEdgeIntegrationTest {

    /**
     * 测试条件边路由到 "tool_executor" 分支。
     */
    @Test
    void testConditionalEdge_ToolBranch() throws Exception {
        InputStream yamlStream = getClass().getResourceAsStream("/conditional_flow.yaml");
        assertNotNull(yamlStream, "条件边 YAML 未找到");

        YamlGraphBuilder builder = new YamlGraphBuilder();
        Graph graph = builder.build(yamlStream);

        // 验证图结构
        assertEquals("conditional_flow", graph.getName());
        assertEquals(4, graph.getNodes().size());
        assertEquals(5, graph.getEdges().size());

        // 创建节点工厂
        Map<String, Node> nodeRegistry = new HashMap<>();

        nodeRegistry.put("InputParseNode", inputs -> {
            String raw = (String) inputs.get("raw");
            // 模拟解析：请求中包含 "tool" 关键字则返回 use_tool
            String intent = raw.contains("tool") ? "use_tool" : "chat";
            return Map.of("intent", intent, "data", "Parsed: " + raw);
        });

        nodeRegistry.put("ToolExecutorNode", inputs -> {
            String data = (String) inputs.get("data");
            return Map.of("result", "Tool Result for: " + data);
        });

        nodeRegistry.put("ResponseGeneratorNode", inputs -> {
            String data = (String) inputs.get("data");
            return Map.of("response", "Chat Response for: " + data);
        });

        nodeRegistry.put("EmergencyHandlerNode", inputs -> {
            String data = (String) inputs.get("data");
            return Map.of("alert", "Emergency Alert for: " + data);
        });

        // 执行引擎
        SynapseEngine engine = new SynapseEngine(graph, type -> nodeRegistry.get(type));

        // 测试 tool 分支
        Map<String, Object> flowData = Map.of("request", "please use tool to search");
        ScopeContext result = engine.execute(flowData);

        // 验证走了 tool 分支
        String toolResult = result.<String>getFlow("tool_result").orElse(null);
        assertNotNull(toolResult, "应该有 tool_result");
        assertTrue(toolResult.contains("Tool Result"), "应该返回工具结果");

        // 验证没有走 chat 分支
        assertTrue(result.getFlow("final_response").isEmpty(), "不应该有 chat 响应");

        System.out.println("工具分支测试通过: " + toolResult);
    }

    /**
     * 测试条件边路由到 "chat" 分支。
     */
    @Test
    void testConditionalEdge_ChatBranch() throws Exception {
        InputStream yamlStream = getClass().getResourceAsStream("/conditional_flow.yaml");
        YamlGraphBuilder builder = new YamlGraphBuilder();
        Graph graph = builder.build(yamlStream);

        Map<String, Node> nodeRegistry = new HashMap<>();

        nodeRegistry.put("InputParseNode", inputs -> {
            String raw = (String) inputs.get("raw");
            // 普通对话不包含特殊关键字
            return Map.of("intent", "chat", "data", "Parsed: " + raw);
        });

        nodeRegistry.put("ToolExecutorNode", inputs -> Map.of("result", "Tool Result"));
        nodeRegistry.put("ResponseGeneratorNode", inputs -> Map.of("response", "Hello! How can I help?"));
        nodeRegistry.put("EmergencyHandlerNode", inputs -> Map.of("alert", "Emergency!"));

        SynapseEngine engine = new SynapseEngine(graph, type -> nodeRegistry.get(type));

        Map<String, Object> flowData = Map.of("request", "hello how are you");
        ScopeContext result = engine.execute(flowData);

        // 验证走了 chat 分支
        String response = result.<String>getFlow("final_response").orElse(null);
        assertNotNull(response, "应该有 final_response");
        assertEquals("Hello! How can I help?", response);

        // 验证没有走 tool 分支
        assertTrue(result.getFlow("tool_result").isEmpty(), "不应该有 tool_result");

        System.out.println("对话分支测试通过: " + response);
    }

    /**
     * 测试条件边路由到 "emergency" 分支。
     */
    @Test
    void testConditionalEdge_EmergencyBranch() throws Exception {
        InputStream yamlStream = getClass().getResourceAsStream("/conditional_flow.yaml");
        YamlGraphBuilder builder = new YamlGraphBuilder();
        Graph graph = builder.build(yamlStream);

        Map<String, Node> nodeRegistry = new HashMap<>();

        nodeRegistry.put("InputParseNode", inputs -> {
            String raw = (String) inputs.get("raw");
            // 检测紧急请求
            String intent = raw.contains("urgent") || raw.contains("emergency") ? "emergency" : "chat";
            return Map.of("intent", intent, "data", "Parsed: " + raw);
        });

        nodeRegistry.put("ToolExecutorNode", inputs -> Map.of("result", "Tool Result"));
        nodeRegistry.put("ResponseGeneratorNode", inputs -> Map.of("response", "Normal Response"));
        nodeRegistry.put("EmergencyHandlerNode", inputs -> Map.of("alert", "URGENT: Immediate attention required!"));

        SynapseEngine engine = new SynapseEngine(graph, type -> nodeRegistry.get(type));

        Map<String, Object> flowData = Map.of("request", "this is an emergency situation");
        ScopeContext result = engine.execute(flowData);

        // 验证走了 emergency 分支
        String alert = result.<String>getFlow("emergency_alert").orElse(null);
        assertNotNull(alert, "应该有 emergency_alert");
        assertTrue(alert.contains("URGENT"), "应该返回紧急响应");

        System.out.println("紧急分支测试通过: " + alert);
    }

    /**
     * 测试条件边不匹配时抛出异常。
     */
    @Test
    void testConditionalEdge_UnmappedCondition() throws Exception {
        InputStream yamlStream = getClass().getResourceAsStream("/conditional_flow.yaml");
        YamlGraphBuilder builder = new YamlGraphBuilder();
        Graph graph = builder.build(yamlStream);

        Map<String, Node> nodeRegistry = new HashMap<>();

        nodeRegistry.put("InputParseNode", inputs -> {
            // 返回一个未在映射表中定义的 intent
            return Map.of("intent", "unknown_action", "data", "Some data");
        });

        nodeRegistry.put("ToolExecutorNode", inputs -> Map.of("result", "Tool"));
        nodeRegistry.put("ResponseGeneratorNode", inputs -> Map.of("response", "Chat"));
        nodeRegistry.put("EmergencyHandlerNode", inputs -> Map.of("alert", "Emergency"));

        SynapseEngine engine = new SynapseEngine(graph, type -> nodeRegistry.get(type));

        Map<String, Object> flowData = Map.of("request", "test");

        // 应该抛出异常，因为 "unknown_action" 不在映射表中
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            engine.execute(flowData);
        });

        assertTrue(exception.getMessage().contains("条件边评估失败"),
                "异常消息应包含 '条件边评估失败'");
        System.out.println("未匹配条件异常测试通过: " + exception.getMessage());
    }
}
