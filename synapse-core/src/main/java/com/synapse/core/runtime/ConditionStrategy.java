package com.synapse.core.runtime;

import com.synapse.core.state.ScopeContext;

/**
 * ConditionStrategy 定义了评估条件的策略接口。
 */
public interface ConditionStrategy {

    /**
     * 评估表达式并返回结果对象。
     *
     * @param expression 表达式内容
     * @param context    当前上下文
     * @return 评估结果（通常是 String, Boolean 等，toString 后用于匹配映射）
     */
    Object evaluate(String expression, ScopeContext context);
}
