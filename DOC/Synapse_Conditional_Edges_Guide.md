# Synapse 条件边 (Conditional Edges) 使用指南 V1.0 - 配置示例

本文档通过三个典型的使用场景，展示 Synapse 引擎中条件边的配置与使用方法。

Synapse 支持三种数据来源作为路由条件：
1.  **Flow Scope**: 基于当前流程中的变量（如用户意图、状态码）。
2.  **Node Output**: 基于上一个节点的直接输出（如工具执行结果）。
3.  **Global Scope**: 基于全局环境变量（如开发/生产环境切换）。

---

## 场景 1：智能助手路由 (基于 Flow 变量)

**场景描述**：根据用户输入的意图（Intent），将请求分发给不同的处理节点（工具调用、闲聊、紧急处理）。

### Java 代码逻辑 (模拟)
```java
// 意图识别节点
String intent = classifier.classify(userInput); // 返回 "use_tool", "chat", 或 "emergency"
return Map.of("intent", intent);

// 输出映射配置
// result.intent -> flow.next_action
```

### YAML 配置
```yaml
name: smart_assistant_router
nodes:
  - id: intent_classifier
    type: com.ai.IntentClassifierNode
    inputs:
      query: flow.user_query
    outputs:
      - sourceKey: intent
        targetScope: flow
        targetKey: next_action  # 将意图写入流程变量

  - id: tool_agent
    type: com.ai.ToolAgentNode
  
  - id: chat_agent
    type: com.ai.ChatAgentNode

  - id: human_escalation
    type: com.ai.HumanEscalationNode

edges:
  - source: __start__
    target: intent_classifier

  # === 条件边配置 ===
  - source: intent_classifier
    condition: flow.next_action  # 读取流程变量
    mappings:
      use_tool: tool_agent      # 如果 next_action == "use_tool"，跳转到 tool_agent
      chat: chat_agent          # 如果 next_action == "chat"，跳转到 chat_agent
      emergency: human_escalation # 如果 next_action == "emergency"，跳转到人工
  # =================

  - source: tool_agent
    target: __end__
  - source: chat_agent
    target: __end__
  - source: human_escalation
    target: __end__
```

---

## 场景 2：审批流程 (基于节点直接输出)

**场景描述**：如果前一个节点（如风险评估）的直接输出结果为 "SAFE"，则自动通过；否则进入人工复审。

### YAML 配置
```yaml
name: risk_approval_process
nodes:
  - id: risk_engine
    type: com.fintech.RiskEngineNode
    # 假设节点返回: { "risk_level": "LOW" } 或 { "risk_level": "HIGH" }
    # 注意：这里不需要显式把 risk_level 写入 flow，条件边可以直接读取节点的输出

  - id: auto_approve
    type: com.fintech.AutoApproveNode

  - id: manual_review
    type: com.fintech.ManualReviewNode

edges:
  - source: __start__
    target: risk_engine

  # === 条件边配置 ===
  - source: risk_engine
    condition: nodes.risk_engine.risk_level  # 直接读取 risk_engine 节点的输出
    mappings:
      LOW: auto_approve    # 风险低 -> 自动通过
      MEDIUM: manual_review # 风险中 -> 人工复审
      HIGH: __end__         # 风险高 -> 直接结束（拒绝）
  # =================

  - source: auto_approve
    target: __end__
  - source: manual_review
    target: __end__
```

---

## 场景 3：环境自适应 (基于 Global 变量)

**场景描述**：根据当前的运行环境（全局变量 `env`），决定调用真实的支付接口还是模拟的 Mock 接口。

### Context 初始化
```java
Map<String, Object> globalConfig = Map.of("env", "DEV"); // 或者 "PROD"
engine.execute(globalConfig, flowData);
```

### YAML 配置
```yaml
name: payment_flow
nodes:
  - id: prepare_payment
    type: com.pay.PrepareNode

  - id: mock_payment_provider
    type: com.pay.MockProviderNode

  - id: real_payment_provider
    type: com.pay.RealProviderNode

edges:
  - source: __start__
    target: prepare_payment

  # === 条件边配置 ===
  - source: prepare_payment
    condition: global.env  # 读取全局配置
    mappings:
      DEV: mock_payment_provider   # 开发环境 -> 走 Mock
      TEST: mock_payment_provider
      PROD: real_payment_provider  # 生产环境 -> 走真实接口
  # =================

  - source: mock_payment_provider
    target: __end__
  - source: real_payment_provider
    target: __end__
```

---

## 最佳实践 Tips

1.  **显式优于隐式**：虽然可以直接读取 `nodes.xxx.output`，但推荐先将关键决策变量（如 `status`、`action`）写入 `flow` 作用域。这样在 YAML 中阅读 `condition: flow.status` 会比 `condition: nodes.step1.data.status` 更清晰，也更容易重构。
2.  **枚举值管理**：建议节点的返回值使用明确的字符串（如 `SUCCESS`, `FAIL`, `RETRY`），并在文档中定义好，确保 YAML 映射表没有拼写错误。
3.  **默认路径**：Synapse 目前要求条件值必须在 `mappings` 中找到完全匹配的键，否则会抛出异常。这是一个设计保护，防止意外的未处理状态。确保你的节点覆盖了所有可能的返回值，或者在 YAML 中列出所有可能的分支。
