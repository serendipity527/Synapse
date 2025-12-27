# Synapse 架构设计文档 V1.2 - 核心引擎

**版本**: 1.2 (Phase 1, 2 & 2.1)  
**作者**: Antigravity  
**日期**: 2025-12-27


---

## 1. 设计愿景

Synapse 旨在构建一个**企业级、配置驱动、强隔离**的 Java 流程编排引擎。与轻量级的 Agent 编排库（如 LangGraph4j）不同，Synapse 的核心设计哲学强调以下三点：

1.  **显式数据流 (Explicit Data Flow)**: 数据的来源（Source）和去向（Target）必须在配置层面明确声明，禁止节点隐式访问全局状态，确保数据流向清晰、可追踪。
2.  **强命名空间隔离 (Namespace Isolation)**: 采用分层状态管理，隔离全局配置、流程变量和节点产物，防止数据污染和命名冲突。
3.  **配置驱动 (Configuration-First)**: 业务逻辑（Java Code）与编排逻辑（YAML Config）完全解耦，支持无需重新编译代码即可调整流程结构。

---

## 2. 核心组件架构

### 2.1 状态管理 (State Management)

Synapse 摒弃了扁平的 `Map<String, Object>` 状态模型，采用了三级分层结构：

*   **Global Scope (全局域)**:
    *   存储系统级配置、环境变量或跨流程共享的只读数据。
    *   *特性*: 通常在流程启动时注入，运行期间不可变（或受限写入）。
*   **Flow Scope (流程域)**:
    *   存储贯穿整个流程生命周期的业务数据（如 RequestId, UserInfo）。
    *   *特性*: 所有节点可读写，类似于“黑板”模式。
*   **Node Scope (节点域)**:
    *   存储特定节点的输出结果，物理结构为 `Map<NodeId, Map<String, Object>>`。
    *   *特性*: **强隔离**。节点 A 默认只能写入 `nodes.A` 区域。节点 B 想要使用节点 A 的数据，必须显式引用 `nodes.A.key`。

**核心接口**: `com.synapse.core.state.ScopeContext`

### 2.2 节点模型 (Node Model)

为了实现“显式数据流”，Synapse 的节点设计极度精简且无状态。

*   **Node 接口**:
    ```java
    @FunctionalInterface
    public interface Node {
        Map<String, Object> execute(Map<String, Object> inputs) throws Exception;
    }
    ```
    *   *设计意图*: 节点是一个纯函数。它不知道“Context”的存在，也不知道自己处于图的哪个位置。它只负责：给什么原料（inputs），加工出什么产品（outputs）。

*   **NodeDefinition (元数据)**:
    *   定义节点的静态属性：`id`, `type`。
    *   定义 I/O 规则：
        *   `inputMappings`: 定义如何凑齐 inputs（例如：`param1 = flow.var1`, `param2 = nodes.step1.result`）。
        *   `outputMappings`: 定义 outputs 去哪儿（例如：`result -> flow.finalResult`）。

### 2.3 运行时架构 (Runtime Architecture)

运行时由两个核心类驱动：

#### 2.3.1 NodeRunner (搬运工)
`NodeRunner` 是连接 `ScopeContext` 和 `Node` 的桥梁，它实现了 Synapse 独特的“搬运机制”：

1.  **输入映射 (Pre-computation)**: 
    *   读取 `NodeDefinition` 的 `inputMappings`。
    *   从 `ScopeContext` 的不同作用域（Global/Flow/Nodes）抓取数据。
    *   组装成纯净的 `Map<String, Object> inputs`。
2.  **节点执行 (Execution)**: 
    *   调用 `node.execute(inputs)`。
    *   节点内部执行纯业务逻辑。
3.  **输出分发 (Post-computation)**: 
    *   获取节点返回的 `outputs`。
    *   根据 `outputMappings` 将数据分发回 `ScopeContext`（写入 Flow 或 Node 作用域）。

#### 2.3.2 SynapseEngine (指挥官)
负责图的生命周期管理：
*   初始化 `ScopeContext`。
*   从 `__start__` 节点开始遍历。
*   根据 YAML 定义的边（Edges）寻找下一个节点。
*   调用 `NodeRunner` 执行节点。
*   检测 `__end__` 信号或死循环（Max Iteration）。

---

## 3. 配置系统 (Configuration)

Synapse 使用 YAML 定义图结构，强调可读性和声明式。

```yaml
name: explicit_flow_demo
nodes:
  - id: input_parser
    type: com.example.Parser
    inputs:
      raw: flow.requestData  # 从 Flow 作用域取值
    outputs:
      - sourceKey: parsed    # 节点返回的 key
        targetScope: flow    # 写入 Flow 作用域
        targetKey: cleanData # 重命名为 cleanData

  - id: risk_check
    type: com.example.RiskEngine
    inputs:
      user: flow.cleanData   # 从上一步的 Flow 变量取值
      config: global.rules   # 从 Global 配置取值

edges:
  - source: __start__
    target: input_parser
  - source: input_parser
    target: risk_check
  - source: risk_check
    target: __end__
```

---

## 4. 架构对比：Synapse vs LangGraph4j

为什么 Synapse 不直接复用 LangGraph4j 的底层代码？

| 特性 | **Synapse (本项目)** | **LangGraph4j** | **差异原因** |
| :--- | :--- | :--- | :--- |
| **状态模型** | **三级强隔离 (Hierarchical)**<br>Global / Flow / Nodes | **扁平化共享 (Flat)**<br>AgentState (Single Map) | Synapse 针对复杂业务流，需防止键名冲突，提供清晰的变量生命周期管理。 |
| **节点感知** | **无知 (Ignorant)**<br>节点只接收纯 Map，不依赖框架类。 | **全知 (Omniscient)**<br>节点接收整个 AgentState，可访问所有数据。 | Synapse 强制“最小权限原则”，节点只能访问被显式映射给它的数据。 |
| **I/O 机制** | **显式映射 (Explicit Mapping)**<br>在 YAML 配置中声明数据流向。 | **隐式访问 (Implicit Access)**<br>在 Java 代码中直接操作 State。 | Synapse 追求“数据世系 (Data Lineage)”可见，方便在配置层面审计数据流。 |
| **执行逻辑** | **双层执行 (NodeRunner + Node)**<br>框架负责搬运数据，节点负责计算。 | **单层执行 (NodeAction)**<br>节点自己负责从 State 取值和计算。 | 解耦业务逻辑与数据获取逻辑，提高节点的可测试性和复用性。 |
| **定义方式** | **配置优先 (Configuration-First)**<br>YAML 是第一公民。 | **代码优先 (Code-First)**<br>Fluent API 是第一公民。 | Synapse 目标用户包含非核心开发的配置人员。 |

### 总结
*   **LangGraph4j** 适合快速构建灵活的 LLM Agent，代码即逻辑。
*   **Synapse** 适合构建结构化、可管理、多人协作的企业级业务流程，配置即逻辑。

---

## 5. 条件边 (Phase 2)

Phase 2 引入了**条件边 (Conditional Edges)**，实现了基于运行时数据的动态路由能力。

### 5.1 设计理念

与 LangGraph4j 不同（在 Java 代码中编写条件函数），Synapse 坚持 **"配置优先"** 原则：
*   条件表达式在 YAML 中声明（而非 Java 代码）。
*   条件所依赖的数据来源必须显式指定（遵循"显式数据流"）。

### 5.2 YAML 配置语法

```yaml
edges:
  # 简单边
  - source: nodeA
    target: nodeB

  # 条件边
  - source: decision_node
    condition: flow.next_action  # 条件表达式（数据来源）
    mappings:                     # 值到目标节点的映射
      continue: process_node
      stop: __end__
      retry: decision_node

  # 条件边 (高级表达式 Phase 2.1)
  - source: risk_check
    conditionType: GROOVY        # 指定计算引擎 (可选: KV, SPEL, GROOVY)
    condition: "flow.score > 60 ? 'pass' : 'reject'"
    mappings:
      pass: auto_approve
      reject: manual_review
```

### 5.3 核心组件

#### ConditionEvaluator
支持多种策略来解析条件：

1.  **KV (默认)**: 简单的键值对匹配。
    *   表达式: `flow.status`
    *   优点: 零依赖，性能极高。
2.  **SpEL (Spring Expression Language)**:
    *   依赖: `spring-expression`
    *   表达式: `#flow['score'] > 60`
    *   优点: 标准且强大，支持复杂的 Java 对象操作。
3.  **Groovy**:
    *   依赖: `groovy`
    *   表达式: `flow.score > 60`
    *   优点: 语法简洁（类 Lambda），动态脚本能力强。

支持的表达式格式：
*   `global.key` - 从全局作用域获取
*   `flow.key` - 从流程作用域获取
*   `nodes.nodeId.key` - 从特定节点的输出获取

### 5.4 工作流程示例

```
┌─────────────┐
│   START     │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ input_parser│  outputs: { intent: "use_tool" } → flow.next_action
└──────┬──────┘
       │
       │ condition: flow.next_action
       │ mappings:
       │   use_tool → tool_executor
       │   chat → response_generator
       │   emergency → emergency_handler
       │
       ▼
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│tool_executor│     │ response_gen│     │ emergency   │
└──────┬──────┘     └──────┬──────┘     └──────┬──────┘
       │                   │                   │
       └───────────────────┼───────────────────┘
                           ▼
                     ┌─────────────┐
                     │    END      │
                     └─────────────┘
```

### 5.5 与 LangGraph4j 条件边对比

| 特性 | **Synapse** | **LangGraph4j** |
| :--- | :--- | :--- |
| 条件定义位置 | **YAML 配置** | Java 代码（Lambda 函数） |
| 条件表达式 | 声明式（如 `flow.status`） | 命令式（如 `state.getValue("status")`） |
| 数据访问 | 必须显式声明来源 | 可访问整个 State 任意字段 |
| 可序列化 | ✅ 纯配置，可存储/传输 | ❌ Lambda 无法序列化 |
| 灵活性 | 受限于表达式语法 | 完全灵活（任意 Java 代码） |

## 6. 异步执行模型 (Phase 3)

为了支持高性能的 I/O 密集型任务（如 LLM 调用、数据库访问），Synapse 核心引擎在 Phase 3 进行了全异步化重构。

### 6.1 AsyncNode 接口

所有节点都可以选择实现 `AsyncNode` 接口，以非阻塞方式执行：

```java
public interface AsyncNode extends Node {
    CompletableFuture<Map<String, Object>> executeAsync(Map<String, Object> inputs);
}
```

*   **向后兼容**：传统的同步 `Node` 会被引擎自动包装在 `CompletableFuture` 中。
*   **非阻塞调度**：`SynapseEngine` 使用递归的 `CompletableFuture` 链式调用，而非阻塞线程。

### 6.2 与 LangGraph4j 异步模型的对比

虽然 Synapse 的异步设计受到了 LangGraph4j 的启发，但两者在设计哲学和实现细节上有显著差异：

| 维度 | Synapse (当前) | LangGraph4j |
| :--- | :--- | :--- |
| **基础原语** | `CompletableFuture` (Java 标准库) | `CompletableFuture` + `AsyncGenerator` (自定义) |
| **调度机制** | **递归链 (Recursive Chain)**<br>利用 `thenCompose` 串联节点，逻辑简单直观，堆栈开销小。 | **循环状态机 (Loop State Machine)**<br>基于生成器模式，通过循环驱动状态流转，控制逻辑极其复杂。 |
| **状态管理** | **可变上下文 (Mutable Context)**<br>极致性能优先。节点直接修改共享的 `ScopeContext`。 | **不可变快照 (Immutable Snapshots)**<br>功能优先。每一步都创建状态副本，支持时间旅行和回溯。 |
| **设计目标** | **轻量级、高性能 API 服务**<br>专注于高吞吐量及简单的集成体验。 | **全功能 Agent 框架**<br>专注于交互式 Chat、人机回环 (Human-in-the-loop) 及复杂状态恢复。 |
| **流式支持** | **轻量级回调 (规划中)**<br>计划通过 Observer 模式支持流式，不改变引擎核心。 | **原生生成器 (Native)**<br>引擎本身即是生成器，天生支持流式输出中间状态。 |

**总结**: 
Synapse 选择了更为**轻量和纯粹**的异步路线。我们避免了引入复杂的 Generator 状态机，而是利用 Java 强大的 Future 组合能力来实现高效调度。这使得 Synapse 的核心引擎代码量仅为同类框架的 1/10，却能提供同等的并发处理能力。

## 7. 并行执行 (Phase 4)

得益于 Phase 3 建立的异步和线程安全基础，Synapse 现在支持**隐式并行分叉 (Implicit Forking)**。

### 7.1 工作原理

并行执行无需特殊的节点类型，只需在 YAML 中定义从一个节点到多个目标节点的出边即可。

**示例配置：**

```yaml
edges:
  - source: start_node
    target: branch_a  # 边 1
  - source: start_node
    target: branch_b  # 边 2
```

当 `start_node` 执行完毕后，`SynapseEngine` 会检测到多条有效出边/条件，并立即**并行启动** `branch_a` 和 `branch_b` 的执行链。

### 7.2 线程安全

为了支持并行分支同时写入上下文，底层 `ScopeContext` 已升级为使用 `ConcurrentHashMap`。这意味着：
*   **不同分支写不同 Key**: 完全安全，无需额外操作。
*   **不同分支写相同 Key**: 存在竞争条件（Race Condition），后写入者胜出（Last-Write-Wins）。建议在并行设计时避免键名冲突。

### 7.3 分支汇聚 (Join)

目前的实现支持自然汇聚到 END 或无状态汇聚。
*   如果两个分支最终都指向 END，它们会各自完成。
*   如果需要显式等待所有分支完成后再执行某操作，可以通过设计一个聚合节点（等待所有输入就绪）来实现（高级模式，暂未内置 Barrier）。

## 8. 流式输出支持 (Phase 5)

对于 LLM 等需要实时返回 Token 的场景，Synapse 提供了轻量级的流式输出机制。

### 8.1 核心接口

```java
// 用于发送数据块的回调
@FunctionalInterface
public interface DataStreamer {
    void stream(Object content);
}

// 支持流式输出的节点
public interface StreamingNode extends AsyncNode {
    CompletableFuture<Map<String, Object>> executeStream(
        Map<String, Object> inputs, 
        DataStreamer streamer
    );
}
```

### 8.2 使用方式

1.  **实现 StreamingNode**：在节点逻辑中调用 `streamer.stream(chunk)` 发送数据块。
2.  **引擎调用**：`NodeRunner` 会自动检测 `StreamingNode` 并传入 `DataStreamer`。
3.  **外部集成**：调用者可以在 `executeAsync` 的外层包装中捕获流式数据（例如通过 WebSocket 推送）。

### 8.3 设计哲学

与 LangGraph4j 的 Generator 模式不同，Synapse 使用简单的**回调模式**：
*   **零依赖**：不引入 Reactive Streams 或复杂的背压机制。
*   **灵活性**：`DataStreamer` 可以是任何 Lambda，例如 `webSocket::send`。
*   **非侵入性**：不改变引擎核心逻辑，流式只是节点的可选能力。

---

## 9. 总结与路线图

| Phase | 功能 | 状态 |
| :--- | :--- | :--- |
| **Phase 1** | 基础引擎 (Node, Edge, Context) | ✅ 完成 |
| **Phase 2** | 条件边路由 (KV 匹配) | ✅ 完成 |
| **Phase 2.1** | 高级表达式引擎 (SpEL, Groovy) | ✅ 完成 |
| **Phase 3** | 异步执行 (CompletableFuture) | ✅ 完成 |
| **Phase 4** | 并行执行 (Fork) | ✅ 完成 |
| **Phase 5** | 流式输出 (Streaming) | ✅ 完成 |
| **Phase 6** | 持久化 & 断点续传 | 🔜 规划中 |
| **Phase 7** | 子图嵌套 (SubGraph) | 🔜 规划中 |




