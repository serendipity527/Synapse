package com.synapse.core.node;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * StreamingNode 是一种特殊的 AsyncNode，支持实时流式输出。
 */
public interface StreamingNode extends AsyncNode {

    /**
     * 执行节点逻辑，支持流式输出。
     *
     * @param inputs   输入参数
     * @param streamer 用于发送数据块的回调
     * @return 最终的输出 Map (Future)
     */
    CompletableFuture<Map<String, Object>> executeStream(Map<String, Object> inputs, DataStreamer streamer);

    @Override
    default CompletableFuture<Map<String, Object>> executeAsync(Map<String, Object> inputs) {
        // 如果通过旧接口调用，使用空 Streamer (丢弃流式数据)
        return executeStream(inputs, content -> {
        });
    }

    @Override
    default Map<String, Object> execute(Map<String, Object> inputs) {
        // 阻塞等待异步结果
        return executeAsync(inputs).join();
    }
}
