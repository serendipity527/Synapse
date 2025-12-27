package com.synapse.core.runtime;

import com.synapse.core.graph.EdgeDefinition;
import com.synapse.core.state.ScopeContext;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * ConditionEvaluator 负责评估条件表达式并确定下一个目标节点。
 * <p>
 * 支持 KV (默认), SpEL, Groovy 三种策略。
 */
public class ConditionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(ConditionEvaluator.class);

    private final Map<EdgeDefinition.ConditionType, ConditionStrategy> strategies = new HashMap<>();

    public ConditionEvaluator() {
        strategies.put(EdgeDefinition.ConditionType.KV, new KvStrategy());
        strategies.put(EdgeDefinition.ConditionType.SPEL, new SpelStrategy());
        strategies.put(EdgeDefinition.ConditionType.GROOVY, new GroovyStrategy());
    }

    /**
     * 评估条件并返回目标节点 ID。
     *
     * @param edgeConditionType 条件类型
     * @param expression        条件表达式
     * @param mappings          映射表
     * @param context           上下文
     * @return 目标节点 ID
     */
    public String evaluate(EdgeDefinition.ConditionType edgeConditionType, String expression,
            Map<String, String> mappings, ScopeContext context) {
        Objects.requireNonNull(expression, "条件表达式不能为空");
        Objects.requireNonNull(mappings, "条件映射不能为空");
        Objects.requireNonNull(context, "ScopeContext 不能为空");

        ConditionStrategy strategy = strategies.get(edgeConditionType);
        if (strategy == null) {
            throw new IllegalArgumentException("不支持的条件类型: " + edgeConditionType);
        }

        Object value = strategy.evaluate(expression, context);

        if (value == null) {
            log.warn("条件表达式 '{}' [{}] 的值为 null。", expression, edgeConditionType);
            return null;
        }

        String conditionKey = String.valueOf(value);
        String targetNodeId = mappings.get(conditionKey);

        if (targetNodeId == null) {
            log.warn("条件值 '{}' 在映射表中未找到匹配项。可用选项: {}", conditionKey, mappings.keySet());
            return null;
        }

        log.debug("条件评估结果 [{}]: {} = '{}' -> 目标节点 '{}'", edgeConditionType, expression, conditionKey, targetNodeId);
        return targetNodeId;
    }

    // ================== Strategies ==================

    /**
     * 简单的 Key-Value 匹配策略 (Phase 2 原生实现)
     */
    private static class KvStrategy implements ConditionStrategy {
        @Override
        public Object evaluate(String expression, ScopeContext context) {
            if (expression == null || expression.isBlank()) {
                return null;
            }

            String[] parts = expression.split("\\.", 3);
            if (parts.length < 2) {
                log.warn("KV 表达式格式无效: {}。预期格式为 'scope.key'", expression);
                return null;
            }

            String scope = parts[0];
            try {
                switch (scope) {
                    case "global":
                        return context.getGlobal(parts[1]).orElse(null);
                    case "flow":
                        return context.getFlow(parts[1]).orElse(null);
                    case "nodes":
                        if (parts.length < 3)
                            return null;
                        return context.getNodeOutput(parts[1], parts[2]).orElse(null);
                    default:
                        return null;
                }
            } catch (Exception e) {
                log.warn("KV 解析失败: {}", expression, e);
                return null;
            }
        }
    }

    /**
     * Spring Expression Language (SpEL) 策略
     */
    private static class SpelStrategy implements ConditionStrategy {
        private final ExpressionParser parser = new SpelExpressionParser();

        @Override
        public Object evaluate(String expression, ScopeContext context) {
            // EvaluationRoot 提供类似 {flow: {...}, global: {...}} 的结构
            EvaluationRoot root = new EvaluationRoot(context);

            StandardEvaluationContext evalContext = new StandardEvaluationContext(root);
            // 也可以把 context 暴露为 #context
            evalContext.setVariable("context", context);

            try {
                Expression exp = parser.parseExpression(expression);
                return exp.getValue(evalContext);
            } catch (Exception e) {
                log.error("SpEL 解析错误: {}", expression, e);
                throw e;
            }
        }
    }

    /**
     * Groovy Script 策略
     */
    private static class GroovyStrategy implements ConditionStrategy {
        @Override
        public Object evaluate(String expression, ScopeContext context) {
            EvaluationRoot root = new EvaluationRoot(context);

            Binding binding = new Binding();
            // 直接将 global, flow, nodes 作为顶层变量注入
            binding.setVariable("global", root.getGlobal());
            binding.setVariable("flow", root.getFlow());
            binding.setVariable("nodes", root.getNodes());

            GroovyShell shell = new GroovyShell(binding);
            try {
                return shell.evaluate(expression);
            } catch (Exception e) {
                log.error("Groovy 执行错误: {}", expression, e);
                throw e;
            }
        }
    }

    /**
     * 辅助类，用于为 SpEL/Groovy 提供方便的数据访问根对象。
     */
    public static class EvaluationRoot {
        private final ScopeContext context;

        public EvaluationRoot(ScopeContext context) {
            this.context = context;
        }

        public Map<String, Object> getGlobal() {
            return context.getGlobalData();
        }

        public Map<String, Object> getFlow() {
            return context.getFlowData();
        }

        public Map<String, Map<String, Object>> getNodes() {
            return context.getAllNodeModels();
        }
    }
}
