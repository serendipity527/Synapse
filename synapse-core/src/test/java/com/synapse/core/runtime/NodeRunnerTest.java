package com.synapse.core.runtime;

import com.synapse.core.graph.NodeDefinition;
import com.synapse.core.node.Node;
import com.synapse.core.state.DefaultScopeContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NodeRunner I/O 映射的单元测试。
 */
class NodeRunnerTest {

    private final NodeRunner runner = new NodeRunner();

    @Test
    void testInputMappingFromFlow() throws Exception {
        DefaultScopeContext context = new DefaultScopeContext();
        context.putFlow("x", 10);
        context.putFlow("y", 20);

        NodeDefinition def = new NodeDefinition(
                "addNode",
                "TestAddNode",
                Map.of("a", "flow.x", "b", "flow.y"),
                null);

        // 简单的加法节点
        Node addNode = inputs -> {
            int a = (Integer) inputs.get("a");
            int b = (Integer) inputs.get("b");
            return Map.of("sum", a + b);
        };

        Map<String, Object> result = runner.run(def, addNode, context);

        assertEquals(30, result.get("sum"));
        // 默认输出应进入节点作用域
        assertEquals(30, context.<Integer>getNodeOutput("addNode", "sum").orElse(0));
    }

    @Test
    void testInputMappingFromNodeOutput() throws Exception {
        DefaultScopeContext context = new DefaultScopeContext();
        context.putNodeOutput("prevNode", "data", "hello");

        NodeDefinition def = new NodeDefinition(
                "currentNode",
                "TestNode",
                Map.of("input", "nodes.prevNode.data"),
                null);

        Node node = inputs -> {
            String input = (String) inputs.get("input");
            return Map.of("output", input.toUpperCase());
        };

        runner.run(def, node, context);

        assertEquals("HELLO", context.<String>getNodeOutput("currentNode", "output").orElse(null));
    }

    @Test
    void testOutputMappingToFlow() throws Exception {
        DefaultScopeContext context = new DefaultScopeContext();

        NodeDefinition def = new NodeDefinition(
                "myNode",
                "TestNode",
                null,
                List.of(new NodeDefinition.OutputMapping("result", "flow", "finalResult")));

        Node node = inputs -> Map.of("result", "success");

        runner.run(def, node, context);

        // 应在流程作用域中，而不是节点作用域
        assertEquals("success", context.<String>getFlow("finalResult").orElse(null));
        assertTrue(context.getNodeOutput("myNode", "result").isEmpty());
    }

    @Test
    void testMixedOutputMappings() throws Exception {
        DefaultScopeContext context = new DefaultScopeContext();

        NodeDefinition def = new NodeDefinition(
                "mixedNode",
                "TestNode",
                null,
                List.of(
                        new NodeDefinition.OutputMapping("status", "flow", "processStatus"),
                        new NodeDefinition.OutputMapping("data", "node", "processedData")));

        Node node = inputs -> Map.of("status", "ok", "data", 123);

        runner.run(def, node, context);

        assertEquals("ok", context.<String>getFlow("processStatus").orElse(null));
        assertEquals(123, context.<Integer>getNodeOutput("mixedNode", "processedData").orElse(0));
    }

    @Test
    void testGlobalScopeWriteBlocked() throws Exception {
        DefaultScopeContext context = new DefaultScopeContext();

        NodeDefinition def = new NodeDefinition(
                "badNode",
                "TestNode",
                null,
                List.of(new NodeDefinition.OutputMapping("secret", "global", "apiKey")));

        Node node = inputs -> Map.of("secret", "my-secret-key");

        // 这应该记录一条警告，并且不写入全局作用域
        runner.run(def, node, context);

        // 全局作用域应仍为空
        assertTrue(context.getGlobal("apiKey").isEmpty());
    }
}
