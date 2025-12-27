package com.synapse.core.graph;

import java.util.Map;

/**
 * EdgeDefinition 代表图中两个节点之间的连接（边）。
 * <p>
 * 支持边类型：
 * <ul>
 * <li><b>简单边</b>：从源节点直接跳转到目标节点。</li>
 * <li><b>条件边</b>：根据条件表达式的结果，从映射表中选择目标节点。支持 KV, SPEL, GROOVY 三种表达式。</li>
 * </ul>
 */
public class EdgeDefinition {

    /**
     * 条件类型枚举。
     */
    public enum ConditionType {
        KV, // 简单的键值对匹配 (Phase 2 默认)
        SPEL, // Spring Expression Language
        GROOVY // Groovy Script
    }

    private String sourceNodeId;
    private String targetNodeId;

    // ========== 条件边字段 (Phase 2 & 2.1) ==========

    /**
     * 条件类型，默认为 KV。
     */
    private ConditionType conditionType = ConditionType.KV;

    /**
     * 条件表达式。
     * <ul>
     * <li>KV: "flow.key"</li>
     * <li>SpEL: "#flow['count'] > 5"</li>
     * <li>Groovy: "flow.count > 5"</li>
     * </ul>
     */
    private String conditionExpression;

    /**
     * 条件值到目标节点的映射。
     * 对于 SpEL/Groovy，表达式应返回映射表中的 key（如 "true"/"false" 或自定义字符串）。
     */
    private Map<String, String> conditionMappings;

    public EdgeDefinition() {
    }

    /**
     * 创建简单边。
     */
    public EdgeDefinition(String sourceNodeId, String targetNodeId) {
        this.sourceNodeId = sourceNodeId;
        this.targetNodeId = targetNodeId;
    }

    /**
     * 创建条件边（默认 KV 类型）。
     */
    public EdgeDefinition(String sourceNodeId, String conditionExpression, Map<String, String> conditionMappings) {
        this(sourceNodeId, ConditionType.KV, conditionExpression, conditionMappings);
    }

    /**
     * 创建指定类型的条件边。
     */
    public EdgeDefinition(String sourceNodeId, ConditionType conditionType, String conditionExpression,
            Map<String, String> conditionMappings) {
        this.sourceNodeId = sourceNodeId;
        this.conditionType = conditionType;
        this.conditionExpression = conditionExpression;
        this.conditionMappings = conditionMappings;
    }

    /**
     * 判断是否为条件边。
     */
    public boolean isConditional() {
        return conditionExpression != null && !conditionExpression.isBlank() && conditionMappings != null;
    }

    // ========== Getters and Setters ==========

    public String getSourceNodeId() {
        return sourceNodeId;
    }

    public void setSourceNodeId(String sourceNodeId) {
        this.sourceNodeId = sourceNodeId;
    }

    public String getTargetNodeId() {
        return targetNodeId;
    }

    public void setTargetNodeId(String targetNodeId) {
        this.targetNodeId = targetNodeId;
    }

    public ConditionType getConditionType() {
        return conditionType;
    }

    public void setConditionType(ConditionType conditionType) {
        this.conditionType = conditionType;
    }

    public String getConditionExpression() {
        return conditionExpression;
    }

    public void setConditionExpression(String conditionExpression) {
        this.conditionExpression = conditionExpression;
    }

    public Map<String, String> getConditionMappings() {
        return conditionMappings;
    }

    public void setConditionMappings(Map<String, String> conditionMappings) {
        this.conditionMappings = conditionMappings;
    }

    @Override
    public String toString() {
        if (isConditional()) {
            return "EdgeDefinition{" + sourceNodeId + " -[" + conditionType + ":" + conditionExpression + "]-> "
                    + conditionMappings + '}';
        }
        return "EdgeDefinition{" + sourceNodeId + " -> " + targetNodeId + '}';
    }
}
