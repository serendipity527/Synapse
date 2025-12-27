# Synapse

<p align="center">
  <strong>🧠 轻量级、配置优先的 Java 图执行引擎</strong>
</p>

<p align="center">
  <em>专为 LLM 应用与复杂业务流程编排而生</em>
</p>

---

## ✨ 特性

| 功能 | 说明 |
| :--- | :--- |
| **📝 配置优先** | 使用 YAML 定义工作流，无需硬编码业务逻辑 |
| **⚡ 异步执行** | 基于 `CompletableFuture` 的非阻塞引擎，适合 I/O 密集型任务 |
| **🔀 条件路由** | 支持 KV 匹配、SpEL 表达式、Groovy 脚本三种条件边 |
| **🚀 并行执行** | 多条出边自动触发并行分叉，提升吞吐量 |
| **📡 流式输出** | 原生支持 LLM Token 流式回调 |
| **🔒 线程安全** | `ConcurrentHashMap` 支撑的并发状态管理 |

---

## 🚀 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.synapse</groupId>
    <artifactId>synapse-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. 定义工作流 (YAML)

```yaml
name: simple_chat_flow

nodes:
  - id: input_parser
    type: InputParserNode
    inputs:
      request: flow.user_input
    outputs:
      - source: intent
        target: flow.next_action

  - id: chat_node
    type: ChatNode
    inputs:
      prompt: flow.user_input

edges:
  - source: __start__
    target: input_parser

  - source: input_parser
    condition: flow.next_action
    conditionType: KV
    mappings:
      chat: chat_node
      tool: tool_node

  - source: chat_node
    target: __end__
```

### 3. 执行工作流

```java
// 加载 YAML 配置
Graph graph = YamlGraphBuilder.build("simple_chat_flow.yaml");

// 注册节点实现
Function<String, Node> nodeFactory = type -> switch (type) {
    case "InputParserNode" -> new InputParserNode();
    case "ChatNode" -> new ChatNode();
    default -> throw new IllegalArgumentException("Unknown node: " + type);
};

// 创建引擎并执行
SynapseEngine engine = new SynapseEngine(graph, nodeFactory);

Map<String, Object> flowData = Map.of("user_input", "你好，请帮我查询天气");
ScopeContext result = engine.execute(flowData);

// 获取结果
String response = result.getNodeOutput("chat_node", "response").orElse("");
```

---

## 🏗️ 架构概览

```
┌─────────────────────────────────────────────────────────────┐
│                    YAML Configuration                        │
│  (nodes, edges, conditions, input/output mappings)          │
└───────────────────────────┬─────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                    YamlGraphBuilder                          │
│          解析 YAML → Graph (NodeDefinition + EdgeDefinition) │
└───────────────────────────┬─────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                     SynapseEngine                            │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │ NodeRunner  │  │ Condition   │  │ ScopeContext        │  │
│  │ (执行节点)  │  │ Evaluator   │  │ (global/flow/node)  │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

---

## 🔌 节点类型

### 同步节点 (Node)

```java
public class MyNode implements Node {
    @Override
    public Map<String, Object> execute(Map<String, Object> inputs) {
        String input = (String) inputs.get("data");
        return Map.of("result", process(input));
    }
}
```

### 异步节点 (AsyncNode)

```java
public class MyAsyncNode implements AsyncNode {
    @Override
    public CompletableFuture<Map<String, Object>> executeAsync(Map<String, Object> inputs) {
        return CompletableFuture.supplyAsync(() -> {
            // 异步执行（如 HTTP 请求）
            return Map.of("result", callExternalApi(inputs));
        });
    }
}
```

### 流式节点 (StreamingNode)

```java
public class LlmStreamingNode implements StreamingNode {
    @Override
    public CompletableFuture<Map<String, Object>> executeStream(
            Map<String, Object> inputs, DataStreamer streamer) {
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder response = new StringBuilder();
            for (String token : llm.streamTokens(inputs.get("prompt"))) {
                streamer.stream(token);  // 实时推送
                response.append(token);
            }
            return Map.of("response", response.toString());
        });
    }
}
```

---

## 🔀 条件边类型

| 类型 | 表达式示例 | 适用场景 |
| :--- | :--- | :--- |
| **KV** | `flow.action` | 简单键值匹配 |
| **SpEL** | `flow.score > 60` | 复杂逻辑判断 |
| **Groovy** | `nodes.llm.confidence >= 0.8` | 动态脚本 |

---

## 📊 与 LangGraph4j 对比

| 维度 | Synapse | LangGraph4j |
| :--- | :--- | :--- |
| **配置方式** | YAML 优先 | Java 代码优先 |
| **复杂度** | ~500 行核心代码 | ~3000+ 行 |
| **状态模型** | 可变 (Mutable) | 不可变快照 |
| **流式支持** | 轻量回调 | Generator 模式 |
| **学习曲线** | 低 | 中高 |

---

## 📁 项目结构

```
Synapse/
├── synapse-core/                # 核心引擎模块
│   ├── src/main/java/
│   │   └── com/synapse/core/
│   │       ├── config/          # YAML 解析
│   │       ├── graph/           # 图定义 (Node, Edge)
│   │       ├── node/            # 节点接口
│   │       ├── runtime/         # 执行引擎
│   │       └── state/           # 状态管理
│   └── src/test/                # 测试用例
├── DOC/                         # 设计文档
│   ├── Synapse_Architecture_V1.md
│   └── Synapse_Conditional_Edges_Guide.md
└── README.md
```

---

## 📜 开源协议

MIT License

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

---

<p align="center">
  <strong>Synapse</strong> - 让复杂流程变得简单 🚀
</p>
