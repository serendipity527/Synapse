package com.synapse.core.state;

import java.util.Map;
import java.util.Optional;

/**
 * ScopeContext 定义了 Synapse 中管理三级状态隔离的协议。
 * <p>
 * 作用域：
 * <ul>
 * <li><b>Global</b>: 全局共享的配置或只读数据。</li>
 * <li><b>Flow</b>: 单次执行实例范围内的变量。</li>
 * <li><b>Node</b>: 每个节点隔离的输出区；其他节点只能读取，不能写入。</li>
 * </ul>
 */
public interface ScopeContext {

    // ========================
    // 全局作用域操作 (Global Scope)
    // ========================

    /**
     * 从全局作用域获取值。
     *
     * @param key 要获取的键
     * @param <T> 预期的类型
     * @return 包含值的 Optional（如果存在）
     */
    <T> Optional<T> getGlobal(String key);

    /**
     * 获取所有全局作用域数据。
     *
     * @return 全局数据的不可变 Map
     */
    Map<String, Object> getGlobalData();

    // ========================
    // 流程作用域操作 (Flow Scope)
    // ========================

    /**
     * 从流程作用域获取值。
     *
     * @param key 要获取的键
     * @param <T> 预期的类型
     * @return 包含值的 Optional（如果存在）
     */
    <T> Optional<T> getFlow(String key);

    /**
     * 向流程作用域存入值。
     *
     * @param key   键
     * @param value 值
     */
    void putFlow(String key, Object value);

    /**
     * 获取所有流程作用域数据。
     *
     * @return 流程数据的不可变 Map
     */
    Map<String, Object> getFlowData();

    // ========================
    // 节点作用域操作 (Node Scope)
    // ========================

    /**
     * 从特定节点的输出命名空间获取值。
     *
     * @param nodeId 节点标识符
     * @param key    输出键
     * @param <T>    预期的类型
     * @return 包含值的 Optional（如果存在）
     */
    <T> Optional<T> getNodeOutput(String nodeId, String key);

    /**
     * 向当前节点的输出命名空间存入值。
     * 应仅由 NodeRunner 为当前执行的节点调用。
     *
     * @param nodeId 节点标识符
     * @param key    输出键
     * @param value  值
     */
    void putNodeOutput(String nodeId, String key, Object value);

    /**
     * 获取特定节点的所有输出数据。
     *
     * @param nodeId 节点标识符
     * @return 节点输出的不可变 Map
     */
    Map<String, Object> getNodeOutputs(String nodeId);

    /**
     * 获取所有节点的输出数据（Phase 2.1 新增，用于表达式评估）。
     *
     * @return 所有节点数据的不可变 Map (Map<NodeId, Map<Key, Value>>)
     */
    Map<String, Map<String, Object>> getAllNodeModels();
}
