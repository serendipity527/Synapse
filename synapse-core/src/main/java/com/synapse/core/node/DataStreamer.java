package com.synapse.core.node;

/**
 * DataStreamer 允许节点在执行过程中实时发送数据块。
 * 通常用于 LLM 的 Token 流式输出。
 */
@FunctionalInterface
public interface DataStreamer {
    /**
     * 发送一个数据块。
     *
     * @param content 数据块内容（通常是 String，但也支持其他对象）
     */
    void stream(Object content);
}
