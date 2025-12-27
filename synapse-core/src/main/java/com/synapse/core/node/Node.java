package com.synapse.core.node;

import java.util.Map;

/**
 * Node（节点）代表 Synapse 工作流中的单个执行单元。
 * <p>
 * Node 是一个纯函数：它接收映射后的输入并产生输出。
 * 它不应该感知 ScopeContext；输入/输出的映射由 NodeRunner 处理。
 * <p>
 * 这种设计确保了业务逻辑与编排框架之间的解耦。
 */
@FunctionalInterface
public interface Node {

    /**
     * 使用提供的输入执行节点逻辑。
     *
     * @param inputs 输入键值对的 Map（已由运行器从 Context 映射）
     * @return 输出键值对的 Map（将由运行器分发）
     * @throws Exception 如果执行过程中发生任何错误
     */
    Map<String, Object> execute(Map<String, Object> inputs) throws Exception;
}
