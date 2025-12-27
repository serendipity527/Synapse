package com.synapse.core.runtime;

import com.synapse.core.graph.EdgeDefinition;
import com.synapse.core.state.DefaultScopeContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 高级条件评估测试 (SpEL & Groovy)。
 */
class AdvancedConditionTest {

    private final ConditionEvaluator evaluator = new ConditionEvaluator();

    @Test
    void testSpelCondition_Math() {
        DefaultScopeContext context = new DefaultScopeContext();
        context.putFlow("score", 85);

        Map<String, String> mappings = Map.of(
                "true", "pass_node",
                "false", "fail_node");

        // SpEL: flow['score'] > 60
        // 注意：SpEL 的 #this 根对象是 EvaluationRoot，所以可以直接访问 flow
        String result = evaluator.evaluate(
                EdgeDefinition.ConditionType.SPEL,
                "flow['score'] > 60",
                mappings,
                context);

        assertEquals("pass_node", result);
    }

    @Test
    void testSpelCondition_StringLogic() {
        DefaultScopeContext context = new DefaultScopeContext();
        context.putFlow("status", "ERROR_CODE_500");

        Map<String, String> mappings = Map.of(
                "true", "error_handler",
                "false", "normal_flow");

        // SpEL: flow['status'].contains('ERROR')
        String result = evaluator.evaluate(
                EdgeDefinition.ConditionType.SPEL,
                "flow['status'].contains('ERROR')",
                mappings,
                context);

        assertEquals("error_handler", result);
    }

    @Test
    void testGroovyCondition_Math() {
        DefaultScopeContext context = new DefaultScopeContext();
        context.putFlow("retry_count", 2);

        Map<String, String> mappings = Map.of(
                "retry", "retry_node",
                "stop", "end_node");

        // Groovy: retry_count < 3 ? 'retry' : 'stop'
        // 注意：EvaluationRoot 将 flow 暴露为顶层变量绑定
        String result = evaluator.evaluate(
                EdgeDefinition.ConditionType.GROOVY,
                "flow.retry_count < 3 ? 'retry' : 'stop'",
                mappings,
                context);

        assertEquals("retry_node", result);
    }

    @Test
    void testGroovyCondition_ComplexLogic() {
        DefaultScopeContext context = new DefaultScopeContext();
        context.putFlow("user_level", "VIP");
        context.putFlow("amount", 1000);

        Map<String, String> mappings = Map.of(
                "approve", "approve_node",
                "reject", "reject_node");

        // Groovy: (user_level == 'VIP' || amount < 500) ? 'approve' : 'reject'
        String result = evaluator.evaluate(
                EdgeDefinition.ConditionType.GROOVY,
                "(flow.user_level == 'VIP' || flow.amount < 500) ? 'approve' : 'reject'",
                mappings,
                context);

        assertEquals("approve_node", result);
    }

    @Test
    void testNodeOutputAccess() {
        DefaultScopeContext context = new DefaultScopeContext();
        context.putNodeOutput("step1", "result", "success");

        Map<String, String> mappings = Map.of(
                "success", "step2",
                "failed", "step3");

        // SpEL: nodes['step1']['result'] == 'success'
        // 由于 SpEL 返回的是 boolean，需要转字符串 "true"
        // 更新：这里我们直接返回 boolean，转 string 后是 "true"
        // 映射表需要配 "true"
        Map<String, String> boolMappings = Map.of("true", "step2", "false", "step3");

        String result = evaluator.evaluate(
                EdgeDefinition.ConditionType.SPEL,
                "nodes['step1']['result'] == 'success'",
                boolMappings,
                context);

        assertEquals("step2", result);
    }
}
