package com.synapse.core.state;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DefaultScopeContext 的单元测试。
 */
class DefaultScopeContextTest {

    @Test
    void testGlobalScopeReadOnly() {
        Map<String, Object> globalData = Map.of("apiUrl", "https://api.example.com");
        DefaultScopeContext context = new DefaultScopeContext(globalData, null);

        assertEquals("https://api.example.com", context.<String>getGlobal("apiUrl").orElse(null));
        assertTrue(context.getGlobal("nonExistent").isEmpty());
    }

    @Test
    void testFlowScopeReadWrite() {
        DefaultScopeContext context = new DefaultScopeContext();

        context.putFlow("userId", "user123");
        assertEquals("user123", context.<String>getFlow("userId").orElse(null));

        context.putFlow("userId", "user456");
        assertEquals("user456", context.<String>getFlow("userId").orElse(null));
    }

    @Test
    void testNodeScopeIsolation() {
        DefaultScopeContext context = new DefaultScopeContext();

        // 节点 A 的输出
        context.putNodeOutput("nodeA", "result", 100);
        context.putNodeOutput("nodeA", "status", "success");

        // 节点 B 的输出
        context.putNodeOutput("nodeB", "result", 200);

        // 写入时节点 A 只能看到自己的输出
        // 但任何节点都可以“读取”其他任何节点的输出
        assertEquals(100, context.<Integer>getNodeOutput("nodeA", "result").orElse(0));
        assertEquals(200, context.<Integer>getNodeOutput("nodeB", "result").orElse(0));
        assertEquals("success", context.<String>getNodeOutput("nodeA", "status").orElse(null));

        // 不存在的键返回 empty
        assertTrue(context.getNodeOutput("nodeA", "unknown").isEmpty());
        assertTrue(context.getNodeOutput("nodeC", "result").isEmpty());
    }

    @Test
    void testGetAllNodeOutputs() {
        DefaultScopeContext context = new DefaultScopeContext();

        context.putNodeOutput("myNode", "key1", "value1");
        context.putNodeOutput("myNode", "key2", "value2");

        Map<String, Object> outputs = context.getNodeOutputs("myNode");
        assertEquals(2, outputs.size());
        assertEquals("value1", outputs.get("key1"));
        assertEquals("value2", outputs.get("key2"));
    }

    @Test
    void testContextToString() {
        DefaultScopeContext context = new DefaultScopeContext(
                Map.of("env", "prod"),
                Map.of("requestId", "req123"));
        context.putNodeOutput("node1", "data", "testData");

        String str = context.toString();
        assertTrue(str.contains("global"));
        assertTrue(str.contains("flow"));
        assertTrue(str.contains("nodes"));
    }
}
