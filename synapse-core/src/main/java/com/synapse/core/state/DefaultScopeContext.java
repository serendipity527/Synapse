package com.synapse.core.state;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Collections.unmodifiableMap;
import static java.util.Optional.ofNullable;

/**
 * ScopeContext 的默认实现，提供三级状态隔离。
 * <p>
 * 该实现确保：
 * <ul>
 * <li>全局作用域 (Global scope) 在初始化后是不可变的。</li>
 * <li>流程作用域 (Flow scope) 在执行期间是可变的。</li>
 * <li>节点作用域 (Node scope) 按节点 ID 进行隔离。</li>
 * </ul>
 * <p>
 * Phase 4 更新：使用 ConcurrentHashMap 以支持线程安全。
 */
public class DefaultScopeContext implements ScopeContext {

    private final Map<String, Object> globalContext;
    private final Map<String, Object> flowContext;
    private final Map<String, Map<String, Object>> nodeContexts;

    /**
     * 使用初始的全局和流程数据创建一个新的 DefaultScopeContext。
     *
     * @param globalData 初始全局配置（创建后不可变）
     * @param flowData   初始流程变量
     */
    public DefaultScopeContext(Map<String, Object> globalData, Map<String, Object> flowData) {
        this.globalContext = globalData != null ? new ConcurrentHashMap<>(globalData) : new ConcurrentHashMap<>();
        this.flowContext = flowData != null ? new ConcurrentHashMap<>(flowData) : new ConcurrentHashMap<>();
        this.nodeContexts = new ConcurrentHashMap<>();
    }

    /**
     * 创建一个空的 DefaultScopeContext。
     */
    public DefaultScopeContext() {
        this(null, null);
    }

    // ========================
    // 全局作用域 (Global Scope)
    // ========================

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getGlobal(String key) {
        return ofNullable((T) globalContext.get(key));
    }

    @Override
    public Map<String, Object> getGlobalData() {
        return unmodifiableMap(globalContext);
    }

    // ========================
    // 流程作用域 (Flow Scope)
    // ========================

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getFlow(String key) {
        return ofNullable((T) flowContext.get(key));
    }

    @Override
    public void putFlow(String key, Object value) {
        Objects.requireNonNull(key, "流程键不能为空");
        flowContext.put(key, value);
    }

    @Override
    public Map<String, Object> getFlowData() {
        return unmodifiableMap(flowContext);
    }

    // ========================
    // 节点作用域 (Node Scope)
    // ========================

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getNodeOutput(String nodeId, String key) {
        Objects.requireNonNull(nodeId, "nodeId 不能为空");
        Objects.requireNonNull(key, "key 不能为空");

        Map<String, Object> nodeData = nodeContexts.get(nodeId);
        if (nodeData == null) {
            return Optional.empty();
        }
        return ofNullable((T) nodeData.get(key));
    }

    @Override
    public void putNodeOutput(String nodeId, String key, Object value) {
        Objects.requireNonNull(nodeId, "nodeId 不能为空");
        Objects.requireNonNull(key, "key 不能为空");

        nodeContexts.computeIfAbsent(nodeId, k -> new ConcurrentHashMap<>())
                .put(key, value);
    }

    @Override
    public Map<String, Object> getNodeOutputs(String nodeId) {
        Objects.requireNonNull(nodeId, "nodeId 不能为空");

        Map<String, Object> nodeData = nodeContexts.get(nodeId);
        if (nodeData == null) {
            return Collections.emptyMap();
        }
        return unmodifiableMap(nodeData);
    }

    @Override
    public Map<String, Map<String, Object>> getAllNodeModels() {
        // 创建只读视图
        return unmodifiableMap(nodeContexts);
    }

    @Override
    public String toString() {
        return "DefaultScopeContext{" +
                "global=" + globalContext +
                ", flow=" + flowContext +
                ", nodes=" + nodeContexts +
                '}';
    }
}
