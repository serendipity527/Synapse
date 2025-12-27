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
 * Synapse 引擎端到端集成测试。
 */
class SynapseEngineIntegrationTest {

  @Test
  void testFullFlowExecution() throws Exception {
    // 加载示例 YAML
    InputStream yamlStream = getClass().getResourceAsStream("/sample_flow.yaml");
    assertNotNull(yamlStream, "示例 YAML 未找到");

    YamlGraphBuilder builder = new YamlGraphBuilder();
    Graph graph = builder.build(yamlStream);

    assertEquals("sample_flow", graph.getName());
    assertEquals(3, graph.getNodes().size());
    assertEquals(4, graph.getEdges().size());

    // 创建一个简单的节点工厂
    Map<String, Node> nodeRegistry = new HashMap<>();

    nodeRegistry.put("InputParseNode", inputs -> {
      String rawInput = (String) inputs.get("rawInput");
      return Map.of("parsedData", "Parsed: " + rawInput);
    });

    nodeRegistry.put("ProcessNode", inputs -> {
      String data = (String) inputs.get("data");
      String config = (String) inputs.getOrDefault("config", "default");
      return Map.of("result", data + " [Config: " + config + "]");
    });

    nodeRegistry.put("OutputNode", inputs -> {
      String processedData = (String) inputs.get("processedData");
      return Map.of("response", "Final: " + processedData);
    });

    // 运行引擎
    SynapseEngine engine = new SynapseEngine(graph, type -> nodeRegistry.get(type));

    Map<String, Object> globalData = Map.of("processConfig", "v1.0");
    Map<String, Object> flowData = Map.of("request", "Hello World");

    ScopeContext result = engine.execute(globalData, flowData);

    // 验证最终状态
    String finalResponse = result.<String>getFlow("finalResponse").orElse(null);
    assertNotNull(finalResponse);
    assertTrue(finalResponse.contains("Parsed: Hello World"));
    assertTrue(finalResponse.contains("Config: v1.0"));
    System.out.println("最终响应: " + finalResponse);
  }

  @Test
  void testDataIsolationBetweenNodes() throws Exception {
    String yaml = """
        name: isolation_test
        nodes:
          - id: nodeA
            type: NodeA
            inputs: {}
            outputs:
              - sourceKey: secret
                targetScope: node
                targetKey: privateSecret
          - id: nodeB
            type: NodeB
            inputs:
              attemptedAccess: nodes.nodeA.secret
        edges:
          - source: __start__
            target: nodeA
          - source: nodeA
            target: nodeB
          - source: nodeB
            target: __end__
        """;

    YamlGraphBuilder builder = new YamlGraphBuilder();
    Graph graph = builder.build(yaml);

    Map<String, Node> nodeRegistry = new HashMap<>();

    nodeRegistry.put("NodeA", inputs -> {
      return Map.of("secret", "top-secret-data");
    });

    nodeRegistry.put("NodeB", inputs -> {
      // 此处应为 null，因为 nodeA 的输出映射到了 "privateSecret"，而不是 "secret"
      Object attemptedAccess = inputs.get("attemptedAccess");
      return Map.of("accessResult", attemptedAccess == null ? "ACCESS_DENIED" : "LEAKED");
    });

    SynapseEngine engine = new SynapseEngine(graph, type -> nodeRegistry.get(type));
    ScopeContext result = engine.execute(Map.of());

    // 验证 nodeB 无法通过错误的键访问 nodeA 的数据
    String accessResult = result.<String>getNodeOutput("nodeB", "accessResult").orElse(null);
    assertEquals("ACCESS_DENIED", accessResult, "数据隔离失败！NodeB 访问了 NodeA 的私有数据。");
  }
}
