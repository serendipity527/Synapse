package com.synapse.core;

import com.synapse.core.graph.Graph;
import com.synapse.core.graph.NodeDefinition;
import com.synapse.core.node.Node;
import com.synapse.core.runtime.SynapseEngine;
import com.synapse.core.state.ScopeContext;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

public class ParallelExecutionTest {

    static class SimpleNode implements Node {
        private final String outputKey;
        private final String outputVal;

        public SimpleNode(String outputKey, String outputVal) {
            this.outputKey = outputKey;
            this.outputVal = outputVal;
        }

        @Override
        public Map<String, Object> execute(Map<String, Object> inputs) {
            try {
                // Add a small delay to increase chance of overlap
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return Map.of(outputKey, outputVal);
        }
    }

    @Test
    void testForkExecution() throws Exception {
        Graph graph = new Graph("parallel_test");

        NodeDefinition startNode = new NodeDefinition();
        startNode.setId("start_node");
        startNode.setType("SimpleNode");
        graph.addNode(startNode);

        NodeDefinition branchA = new NodeDefinition();
        branchA.setId("branchA");
        branchA.setType("SimpleNodeA");
        graph.addNode(branchA);

        NodeDefinition branchB = new NodeDefinition();
        branchB.setId("branchB");
        branchB.setType("SimpleNodeB");
        graph.addNode(branchB);

        // Edges: START -> start_node -> [branchA, branchB]
        graph.addEdge(Graph.START, "start_node");
        // Parallel fork
        graph.addEdge("start_node", "branchA");
        graph.addEdge("start_node", "branchB");

        // Terminate branches
        graph.addEdge("branchA", Graph.END);
        graph.addEdge("branchB", Graph.END);

        Function<String, Node> factory = type -> {
            if ("SimpleNode".equals(type))
                return new SimpleNode("init", "done");
            if ("SimpleNodeA".equals(type))
                return new SimpleNode("resultA", "valA");
            if ("SimpleNodeB".equals(type))
                return new SimpleNode("resultB", "valB");
            return null;
        };

        SynapseEngine engine = new SynapseEngine(graph, factory);
        ScopeContext context = engine.execute(Collections.emptyMap());

        // Verify both branches executed
        assertEquals("valA", context.getNodeOutput("branchA", "resultA").orElse(null));
        assertEquals("valB", context.getNodeOutput("branchB", "resultB").orElse(null));
    }
}
