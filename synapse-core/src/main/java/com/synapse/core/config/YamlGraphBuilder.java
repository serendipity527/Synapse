package com.synapse.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.synapse.core.graph.EdgeDefinition;
import com.synapse.core.graph.Graph;
import com.synapse.core.graph.NodeDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * YamlGraphBuilder 将 YAML 配置文件解析为 Graph 对象。
 * <p>
 * 预期的 YAML 结构：
 * 
 * <pre>
 * name: my_graph
 * nodes:
 *   - id: node1
 *     type: com.example.MyNode
 *     inputs:
 *       userId: flow.request.userId
 *     outputs:
 *       - sourceKey: result
 *         targetScope: flow
 *         targetKey: processedResult
 *
 * edges:
 *   # 简单边
 *   - source: __start__
 *     target: node1
 *
 *   # 条件边 (Phase 2)
 *   - source: decision_node
 *     condition: flow.next_action
 *     mappings:
 *       continue: node2
 *       stop: __end__
 *       retry: node1
 * </pre>
 */
public class YamlGraphBuilder {

    private static final Logger log = LoggerFactory.getLogger(YamlGraphBuilder.class);
    private final ObjectMapper yamlMapper;

    public YamlGraphBuilder() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    /**
     * 从 YAML 输入流构建图（Graph）。
     *
     * @param inputStream YAML 内容流
     * @return 解析后的 Graph
     * @throws Exception 如果解析失败
     */
    @SuppressWarnings("unchecked")
    public Graph build(InputStream inputStream) throws Exception {
        Map<String, Object> yamlData = yamlMapper.readValue(inputStream, Map.class);
        return buildFromMap(yamlData);
    }

    /**
     * 从 YAML 字符串构建图（Graph）。
     *
     * @param yamlContent YAML 内容字符串
     * @return 解析后的 Graph
     * @throws Exception 如果解析失败
     */
    @SuppressWarnings("unchecked")
    public Graph build(String yamlContent) throws Exception {
        Map<String, Object> yamlData = yamlMapper.readValue(yamlContent, Map.class);
        return buildFromMap(yamlData);
    }

    @SuppressWarnings("unchecked")
    private Graph buildFromMap(Map<String, Object> yamlData) {
        String graphName = (String) yamlData.getOrDefault("name", "unnamed_graph");
        Graph graph = new Graph(graphName);

        // 解析节点
        List<Map<String, Object>> nodesData = (List<Map<String, Object>>) yamlData.get("nodes");
        if (nodesData != null) {
            for (Map<String, Object> nodeData : nodesData) {
                NodeDefinition nodeDef = parseNodeDefinition(nodeData);
                graph.addNode(nodeDef);
                log.debug("解析到节点: {}", nodeDef.getId());
            }
        }

        // 解析边
        List<Map<String, Object>> edgesData = (List<Map<String, Object>>) yamlData.get("edges");
        if (edgesData != null) {
            for (Map<String, Object> edgeData : edgesData) {
                EdgeDefinition edge = parseEdgeDefinition(edgeData);
                graph.addEdge(edge);
                log.debug("解析到边: {}", edge);
            }
        }

        log.info("已解析图 '{}'，包含 {} 个节点和 {} 条边",
                graphName, graph.getNodes().size(), graph.getEdges().size());

        return graph;
    }

    @SuppressWarnings("unchecked")
    private NodeDefinition parseNodeDefinition(Map<String, Object> nodeData) {
        NodeDefinition def = new NodeDefinition();
        def.setId((String) nodeData.get("id"));
        def.setType((String) nodeData.get("type"));

        // 解析输入映射
        Map<String, String> inputs = (Map<String, String>) nodeData.get("inputs");
        def.setInputMappings(inputs);

        // 解析输出映射
        List<Map<String, String>> outputsData = (List<Map<String, String>>) nodeData.get("outputs");
        if (outputsData != null) {
            List<NodeDefinition.OutputMapping> outputMappings = new ArrayList<>();
            for (Map<String, String> outData : outputsData) {
                NodeDefinition.OutputMapping om = new NodeDefinition.OutputMapping(
                        outData.get("sourceKey"),
                        outData.get("targetScope"),
                        outData.get("targetKey"));
                outputMappings.add(om);
            }
            def.setOutputMappings(outputMappings);
        }

        return def;
    }

    /**
     * 解析边定义，支持简单边和条件边。
     * <p>
     * 简单边格式：
     * 
     * <pre>
     * - source: nodeA
     *   target: nodeB
     * </pre>
     * 
     * 条件边格式：
     * 
     * <pre>
     * - source: nodeA
     *   condition: flow.next_action
     *   mappings:
     *     action1: nodeB
     *     action2: nodeC
     * </pre>
     */
    @SuppressWarnings("unchecked")
    private EdgeDefinition parseEdgeDefinition(Map<String, Object> edgeData) {
        String source = (String) edgeData.get("source");
        String target = (String) edgeData.get("target");
        String condition = (String) edgeData.get("condition");
        Map<String, String> mappings = (Map<String, String>) edgeData.get("mappings");

        if (condition != null && mappings != null && !mappings.isEmpty()) {
            // 解析条件类型 (type 或 conditionType)
            String typeStr = (String) edgeData.getOrDefault("conditionType", edgeData.get("type"));
            EdgeDefinition.ConditionType conditionType = EdgeDefinition.ConditionType.KV; // 默认为 KV
            if (typeStr != null) {
                try {
                    conditionType = EdgeDefinition.ConditionType.valueOf(typeStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    log.warn("未知的条件类型 '{}', 将使用默认值 KV", typeStr);
                }
            }

            // 条件边
            log.debug("解析到条件边 [{}]: {} -[{}]-> {}", conditionType, source, condition, mappings);
            return new EdgeDefinition(source, conditionType, condition, mappings);
        } else {
            // 简单边
            return new EdgeDefinition(source, target);
        }
    }
}
