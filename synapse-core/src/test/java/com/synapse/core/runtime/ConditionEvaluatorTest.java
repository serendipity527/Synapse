package com.synapse.core.runtime;

import com.synapse.core.graph.EdgeDefinition;
import com.synapse.core.state.DefaultScopeContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ConditionEvaluator 的单元测试。
 */
class ConditionEvaluatorTest {

    private final ConditionEvaluator evaluator = new ConditionEvaluator();

    @Test
    void testEvaluateFlowCondition() {
        DefaultScopeContext context = new DefaultScopeContext();
        context.putFlow("next_action", "continue");

        Map<String, String> mappings = Map.of(
                "continue", "node_process",
                "stop", "__end__");

        String result = evaluator.evaluate(EdgeDefinition.ConditionType.KV, "flow.next_action", mappings, context);
        assertEquals("node_process", result);
    }

    @Test
    void testEvaluateNodeOutputCondition() {
        DefaultScopeContext context = new DefaultScopeContext();
        context.putNodeOutput("risk_check", "risk_level", "high");

        Map<String, String> mappings = Map.of(
                "low", "normal_process",
                "medium", "review_process",
                "high", "emergency_process");

        String result = evaluator.evaluate(EdgeDefinition.ConditionType.KV, "nodes.risk_check.risk_level", mappings,
                context);
        assertEquals("emergency_process", result);
    }

    @Test
    void testEvaluateGlobalCondition() {
        DefaultScopeContext context = new DefaultScopeContext(
                Map.of("env", "production"),
                Map.of());

        Map<String, String> mappings = Map.of(
                "development", "dev_node",
                "production", "prod_node");

        String result = evaluator.evaluate(EdgeDefinition.ConditionType.KV, "global.env", mappings, context);
        assertEquals("prod_node", result);
    }

    @Test
    void testEvaluateUnmatchedCondition() {
        DefaultScopeContext context = new DefaultScopeContext();
        context.putFlow("status", "unknown_status");

        Map<String, String> mappings = Map.of(
                "success", "node_a",
                "failure", "node_b");

        String result = evaluator.evaluate(EdgeDefinition.ConditionType.KV, "flow.status", mappings, context);
        assertNull(result, "未匹配的条件应返回 null");
    }

    @Test
    void testEvaluateNullValue() {
        DefaultScopeContext context = new DefaultScopeContext();
        // flow.missing_key 不存在

        Map<String, String> mappings = Map.of(
                "yes", "node_a",
                "no", "node_b");

        String result = evaluator.evaluate(EdgeDefinition.ConditionType.KV, "flow.missing_key", mappings, context);
        assertNull(result, "表达式值为 null 时应返回 null");
    }

    @Test
    void testEvaluateBooleanCondition() {
        DefaultScopeContext context = new DefaultScopeContext();
        context.putFlow("is_valid", true);

        Map<String, String> mappings = Map.of(
                "true", "valid_node",
                "false", "invalid_node");

        String result = evaluator.evaluate(EdgeDefinition.ConditionType.KV, "flow.is_valid", mappings, context);
        assertEquals("valid_node", result);
    }

    @Test
    void testEvaluateIntegerCondition() {
        DefaultScopeContext context = new DefaultScopeContext();
        context.putFlow("retry_count", 3);

        Map<String, String> mappings = Map.of(
                "0", "first_try",
                "1", "second_try",
                "2", "third_try",
                "3", "give_up");

        String result = evaluator.evaluate(EdgeDefinition.ConditionType.KV, "flow.retry_count", mappings, context);
        assertEquals("give_up", result);
    }
}
