package com.synapse.core.graph;

import java.util.List;
import java.util.Map;

/**
 * NodeDefinition 保存了图中单个节点的元数据，
 * 包括从 YAML 配置解析而来的输入/输出映射。
 */
public class NodeDefinition {

    private String id;
    private String type; // Node 实现的类名或 Bean 名称
    private Map<String, String> inputMappings; // 键：节点输入参数名，值：表达式（例如 "flow.userId"）
    private List<OutputMapping> outputMappings; // 每个输出键分发到的位置

    public NodeDefinition() {
    }

    public NodeDefinition(String id, String type, Map<String, String> inputMappings,
            List<OutputMapping> outputMappings) {
        this.id = id;
        this.type = type;
        this.inputMappings = inputMappings;
        this.outputMappings = outputMappings;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, String> getInputMappings() {
        return inputMappings;
    }

    public void setInputMappings(Map<String, String> inputMappings) {
        this.inputMappings = inputMappings;
    }

    public List<OutputMapping> getOutputMappings() {
        return outputMappings;
    }

    public void setOutputMappings(List<OutputMapping> outputMappings) {
        this.outputMappings = outputMappings;
    }

    @Override
    public String toString() {
        return "NodeDefinition{" +
                "id='" + id + '\'' +
                ", type='" + type + '\'' +
                ", inputMappings=" + inputMappings +
                ", outputMappings=" + outputMappings +
                '}';
    }

    /**
     * OutputMapping 定义了单个输出键应分发到的位置。
     */
    public static class OutputMapping {
        private String sourceKey; // 节点输出 Map 中的键
        private String targetScope; // "global"、"flow" 或 "node"（默认）
        private String targetKey; // 目标作用域中的键名

        public OutputMapping() {
        }

        public OutputMapping(String sourceKey, String targetScope, String targetKey) {
            this.sourceKey = sourceKey;
            this.targetScope = targetScope;
            this.targetKey = targetKey;
        }

        public String getSourceKey() {
            return sourceKey;
        }

        public void setSourceKey(String sourceKey) {
            this.sourceKey = sourceKey;
        }

        public String getTargetScope() {
            return targetScope;
        }

        public void setTargetScope(String targetScope) {
            this.targetScope = targetScope;
        }

        public String getTargetKey() {
            return targetKey;
        }

        public void setTargetKey(String targetKey) {
            this.targetKey = targetKey;
        }

        @Override
        public String toString() {
            return "OutputMapping{" +
                    "sourceKey='" + sourceKey + '\'' +
                    ", targetScope='" + targetScope + '\'' +
                    ", targetKey='" + targetKey + '\'' +
                    '}';
        }
    }
}
