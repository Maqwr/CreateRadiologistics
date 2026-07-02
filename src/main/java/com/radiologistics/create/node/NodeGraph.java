package com.radiologistics.create.node;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.*;

public class NodeGraph {
    private final Map<String, AlgoNode> nodes = new HashMap<>();
    private final List<NodeLink> links = new ArrayList<>();

    public record NodeLink(String fromNode, String fromPort, String toNode, String toPort) {
        public CompoundTag toNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putString("fromNode", fromNode);
            tag.putString("fromPort", fromPort);
            tag.putString("toNode", toNode);
            tag.putString("toPort", toPort);
            return tag;
        }

        public static NodeLink fromNBT(CompoundTag tag) {
            return new NodeLink(
                tag.getString("fromNode"),
                tag.getString("fromPort"),
                tag.getString("toNode"),
                tag.getString("toPort")
            );
        }
    }

    public void addNode(AlgoNode node) {
        nodes.put(node.getId(), node);
    }

    public void removeNode(String id) {
        nodes.remove(id);
        links.removeIf(link -> link.fromNode().equals(id) || link.toNode().equals(id));
    }

    public Map<String, AlgoNode> getNodes() {
        return nodes;
    }

    public List<NodeLink> getLinks() {
        return links;
    }

    public void addLink(String fromNode, String fromPort, String toNode, String toPort) {

        links.removeIf(link -> link.toNode().equals(toNode) && link.toPort().equals(toPort));
        links.add(new NodeLink(fromNode, fromPort, toNode, toPort));
    }

    public void removeLink(String fromNode, String fromPort, String toNode, String toPort) {
        links.removeIf(link -> link.fromNode().equals(fromNode) && link.fromPort().equals(fromPort) &&
                               link.toNode().equals(toNode) && link.toPort().equals(toPort));
    }

    public void clear() {
        nodes.clear();
        links.clear();
        variables.clear();
        variableTypes.clear();
    }

    private final Map<String, Map<String, Object>> evalCache = new HashMap<>();
    private final Set<String> visiting = new HashSet<>();
    private final Map<String, Object> variables = new HashMap<>();
    private final Map<String, String> variableTypes = new HashMap<>();

    public Map<String, Object> getVariables() {
        return variables;
    }

    public Map<String, String> getVariableTypes() {
        return variableTypes;
    }

    public void evaluate(EvaluationContext context) {
        evalCache.clear();
        visiting.clear();

        for (AlgoNode node : nodes.values()) {
            if (node.getType().equals("redstone_output") || node.getType().equals("set_variable")
                    || node.getType().equals("antenna_output") || node.getType().equals("link_output")
                    || node.getType().equals("bool_viewer") || node.getType().equals("number_viewer")
                    || node.getType().equals("text_viewer") || node.getType().equals("jammer")
                    || node.getType().equals("helmet_screen") || node.getType().equals("screen")
                    || node.getType().equals("set_list") || node.getType().equals("camera_screen")
                    || node.getType().equals("servo_control")
                    || node.getType().equals("display_board") || node.getType().equals("camera")
                    || node.getType().equals("display_link") || node.getType().equals("gizmos_view")
                    || node.getType().equals("audio_play")) {
                visiting.add(node.getId());

                Map<String, Object> inputValues = new HashMap<>();
                for (String inputPort : node.getInputPorts()) {
                    inputValues.put(inputPort, evaluateInput(node.getId(), inputPort, context));
                }

                visiting.remove(node.getId());

                node.evaluate("", inputValues, context);
            }
        }
    }

    private Object parseStaticValue(String val) {
        if (val == null) return "";
        String trimmed = val.trim();
        if (trimmed.equalsIgnoreCase("true")) {
            return true;
        }
        if (trimmed.equalsIgnoreCase("false")) {
            return false;
        }
        try {
            if (trimmed.indexOf('.') >= 0) {
                return Double.parseDouble(trimmed);
            } else {
                return Long.parseLong(trimmed);
            }
        } catch (NumberFormatException e) {
            return val;
        }
    }

    public Object evaluateInput(String targetNodeId, String inputPort, EvaluationContext context) {
        NodeLink connection = null;
        for (NodeLink link : links) {
            if (link.toNode().equals(targetNodeId) && link.toPort().equals(inputPort)) {
                connection = link;
                break;
            }
        }

        if (connection == null) {
            AlgoNode node = nodes.get(targetNodeId);
            if (node != null && node.getDefaultPortValues().containsKey(inputPort)) {
                String val = node.getDefaultPortValues().get(inputPort);
                if (val != null) {
                    return parseStaticValue(val);
                }
            }
            return "";
        }

        String sourceNodeId = connection.fromNode();
        String sourcePort = connection.fromPort();

        if (visiting.contains(sourceNodeId)) {
            return "";
        }

        if (evalCache.containsKey(sourceNodeId) && evalCache.get(sourceNodeId).containsKey(sourcePort)) {
            return evalCache.get(sourceNodeId).get(sourcePort);
        }

        AlgoNode sourceNode = nodes.get(sourceNodeId);
        if (sourceNode == null) {
            return "";
        }

        visiting.add(sourceNodeId);

        Map<String, Object> sourceInputs = new HashMap<>();
        for (String inPort : sourceNode.getInputPorts()) {
            sourceInputs.put(inPort, evaluateInput(sourceNodeId, inPort, context));
        }

        Object result = sourceNode.evaluate(sourcePort, sourceInputs, context);

        visiting.remove(sourceNodeId);

        evalCache.computeIfAbsent(sourceNodeId, k -> new HashMap<>()).put(sourcePort, result);

        return result;
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();

        ListTag nodesList = new ListTag();
        for (AlgoNode node : nodes.values()) {
            nodesList.add(node.toNBT());
        }
        tag.put("nodes", nodesList);

        ListTag linksList = new ListTag();
        for (NodeLink link : links) {
            linksList.add(link.toNBT());
        }
        tag.put("links", linksList);

        ListTag varsList = new ListTag();
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            CompoundTag varTag = new CompoundTag();
            varTag.putString("name", entry.getKey());
            varTag.putString("value", entry.getValue() != null ? entry.getValue().toString() : "");
            varTag.putString("type", variableTypes.getOrDefault(entry.getKey(), "text"));
            varsList.add(varTag);
        }
        tag.put("variables", varsList);

        return tag;
    }

    public void loadNBT(CompoundTag tag) {
        clear();

        ListTag nodesList = tag.getList("nodes", 10);
        for (int i = 0; i < nodesList.size(); i++) {
            AlgoNode node = AlgoNode.fromNBT(nodesList.getCompound(i));
            if (node != null) {
                addNode(node);
            }
        }

        ListTag linksList = tag.getList("links", 10);
        for (int i = 0; i < linksList.size(); i++) {
            NodeLink link = NodeLink.fromNBT(linksList.getCompound(i));
            links.add(link);
        }

        if (tag.contains("variables")) {
            ListTag varsList = tag.getList("variables", 10);
            for (int i = 0; i < varsList.size(); i++) {
                CompoundTag varTag = varsList.getCompound(i);
                String name = varTag.getString("name");
                variables.put(name, varTag.getString("value"));
                String type = varTag.contains("type") ? varTag.getString("type") : "text";
                variableTypes.put(name, type);
            }
        }
    }

    public void mergeNBT(CompoundTag tag) {
        String suffix = "_" + System.currentTimeMillis();
        ListTag nodesList = tag.getList("nodes", 10);
        Map<String, String> idMap = new HashMap<>();

        for (int i = 0; i < nodesList.size(); i++) {
            CompoundTag nodeTag = nodesList.getCompound(i);
            String oldId = nodeTag.getString("id");
            String newId = oldId + suffix;
            idMap.put(oldId, newId);

            CompoundTag clonedTag = nodeTag.copy();
            clonedTag.putString("id", newId);

            AlgoNode node = AlgoNode.fromNBT(clonedTag);
            if (node != null) {
                addNode(node);
            }
        }

        ListTag linksList = tag.getList("links", 10);
        for (int i = 0; i < linksList.size(); i++) {
            CompoundTag linkTag = linksList.getCompound(i);
            String oldFrom = linkTag.getString("fromNode");
            String oldTo = linkTag.getString("toNode");
            String fromPort = linkTag.getString("fromPort");
            String toPort = linkTag.getString("toPort");

            String newFrom = idMap.getOrDefault(oldFrom, oldFrom);
            String newTo = idMap.getOrDefault(oldTo, oldTo);

            links.add(new NodeLink(newFrom, fromPort, newTo, toPort));
        }

        if (tag.contains("variables")) {
            ListTag varsList = tag.getList("variables", 10);
            for (int i = 0; i < varsList.size(); i++) {
                CompoundTag varTag = varsList.getCompound(i);
                String name = varTag.getString("name");
                if (!variables.containsKey(name)) {
                    variables.put(name, varTag.getString("value"));
                    String type = varTag.contains("type") ? varTag.getString("type") : "text";
                    variableTypes.put(name, type);
                }
            }
        }
    }
}
