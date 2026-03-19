package dev.ko.processor.validator;

import dev.ko.processor.model.ServiceDependencyModel;

import java.util.*;

public final class CircularDependencyValidator {

    private CircularDependencyValidator() {}

    public static List<List<String>> findCycles(List<ServiceDependencyModel> dependencies) {
        // Build adjacency list
        Map<String, List<String>> graph = new HashMap<>();
        for (ServiceDependencyModel dep : dependencies) {
            graph.computeIfAbsent(dep.from(), k -> new ArrayList<>()).add(dep.to());
            graph.putIfAbsent(dep.to(), new ArrayList<>());
        }

        List<List<String>> cycles = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> inStack = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>();

        for (String node : graph.keySet()) {
            if (!visited.contains(node)) {
                dfs(node, graph, visited, inStack, stack, cycles);
            }
        }

        return cycles;
    }

    private static void dfs(String node, Map<String, List<String>> graph,
                            Set<String> visited, Set<String> inStack,
                            Deque<String> stack, List<List<String>> cycles) {
        visited.add(node);
        inStack.add(node);
        stack.push(node);

        for (String neighbor : graph.getOrDefault(node, List.of())) {
            if (!visited.contains(neighbor)) {
                dfs(neighbor, graph, visited, inStack, stack, cycles);
            } else if (inStack.contains(neighbor)) {
                // Found a cycle — extract it
                List<String> cycle = new ArrayList<>();
                for (String s : stack) {
                    cycle.add(s);
                    if (s.equals(neighbor)) {
                        break;
                    }
                }
                Collections.reverse(cycle);
                cycles.add(cycle);
            }
        }

        stack.pop();
        inStack.remove(node);
    }
}
