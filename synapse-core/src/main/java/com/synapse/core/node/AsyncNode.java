package com.synapse.core.node;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * AsyncNode 是 Node 的异步版本，支持非阻塞执行。
 * <p>
 * 如果节点执行涉及 I/O 操作（如 HTTP 请求、数据库查询），建议实现此接口。
 */
public interface AsyncNode extends Node {

    /**
     * 异步执行节点逻辑。
     *
     * @param inputs 节点的输入数据（由 InputMappings 解析而来）
     * @return 包含执行结果的 CompletableFuture
     */
    CompletableFuture<Map<String, Object>> executeAsync(Map<String, Object> inputs);

    /**
     * 同步执行方法的默认实现。
     * <p>
     * 为了兼容性，AsyncNode 仍然是一个 Node，但其 execute 方法应该阻塞等待 executeAsync 完成。
     * 引擎应当优先调用 executeAsync。
     */
    @Override
    default Map<String, Object> execute(Map<String, Object> inputs) {
        return executeAsync(inputs).join();
    }
}
