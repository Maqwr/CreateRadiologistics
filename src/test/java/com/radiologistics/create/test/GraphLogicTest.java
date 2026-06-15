package com.radiologistics.create.test;

import com.radiologistics.create.node.NodeGraph;
import com.radiologistics.create.node.nodes.*;
import com.radiologistics.create.radio.RadioNetworkManager;
import com.radiologistics.create.block.MainComputerBlockEntity;
import com.radiologistics.create.node.EvaluationContext;
import net.minecraft.core.BlockPos;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class GraphLogicTest {

    @BeforeAll
    public static void initBootstrap() {
        try {
            Class<?> bClass = Class.forName("net.minecraft.server.Bootstrap");
            for (java.lang.reflect.Field f : bClass.getDeclaredFields()) {
                if (f.getType() == boolean.class && java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    f.setAccessible(true);
                    f.set(null, true);
                    System.out.println("Set static boolean field " + f.getName() + " in Bootstrap to true!");
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @BeforeEach
    public void setup() {
        // Clear network manager signals before each test
        RadioNetworkManager.clearChannels();
    }

    @Test
    public void testBasicGraphEvaluation() {
        NodeGraph graph = new NodeGraph();

        // 1. Create a TextNode containing "hello"
        TextNode textNode1 = new TextNode("text1", 0, 0);
        textNode1.setText("hello");
        graph.addNode(textNode1);

        // 2. Create another TextNode containing "hello"
        TextNode textNode2 = new TextNode("text2", 0, 100);
        textNode2.setText("hello");
        graph.addNode(textNode2);

        // 3. Create an EqualNode to compare them
        EqualNode equalNode = new EqualNode("equal1", 150, 50);
        graph.addNode(equalNode);

        // Link them: text1:value -> equal1:a, text2:value -> equal1:b
        graph.addLink("text1", "value", "equal1", "a");
        graph.addLink("text2", "value", "equal1", "b");

        // 4. Create a RedstoneOutputNode
        RedstoneOutputNode redstoneNode = new RedstoneOutputNode("redstone1", 300, 50);
        redstoneNode.setSide("north");
        graph.addNode(redstoneNode);

        // Link equal1:result -> redstone1:power
        graph.addLink("equal1", "result", "redstone1", "power");

        // Evaluate the graph
        graph.evaluate(new com.radiologistics.create.node.EvaluationContext(null, null, null, graph));

        // The values are equal ("hello" == "hello"), so EqualNode should output true,
        // which RedstoneOutputNode parses as power level 15.
        assertEquals(15, redstoneNode.getPowerLevel());

        // Now modify textNode2 to "world"
        textNode2.setText("world");
        graph.evaluate(new com.radiologistics.create.node.EvaluationContext(null, null, null, graph));

        // "hello" != "world", so EqualNode outputs false -> power level 0
        assertEquals(0, redstoneNode.getPowerLevel());
    }

    @Test
    public void testSignalNodeAndIfNode() {
        NodeGraph graph = new NodeGraph();

        // 1. SignalNode listening on channel "42"
        SignalNode signalNode = new SignalNode("sig1", 0, 0);
        graph.addNode(signalNode);
        TextNode channelText = new TextNode("chan", 0, -50);
        channelText.setText("42");
        graph.addNode(channelText);
        graph.addLink("chan", "value", "sig1", "channel");

        // 2. TextNode containing compare string "docked"
        TextNode targetText = new TextNode("target", 0, 80);
        targetText.setText("docked");
        graph.addNode(targetText);

        // 3. EqualNode comparing signal value with "docked"
        EqualNode equalNode = new EqualNode("equal", 150, 40);
        graph.addNode(equalNode);
        graph.addLink("sig1", "value", "equal", "a");
        graph.addLink("target", "value", "equal", "b");

        // 4. IfNode routing true_val="15" (as TextNode) or false_val="2" (as TextNode)
        TextNode trueText = new TextNode("t_val", 100, 160);
        trueText.setText("15");
        graph.addNode(trueText);

        TextNode falseText = new TextNode("f_val", 100, 220);
        falseText.setText("2");
        graph.addNode(falseText);

        IfNode ifNode = new IfNode("if_node", 280, 100);
        graph.addNode(ifNode);
        graph.addLink("equal", "result", "if_node", "condition");
        graph.addLink("t_val", "value", "if_node", "true_val");
        graph.addLink("f_val", "value", "if_node", "false_val");

        // 5. RedstoneOutputNode
        RedstoneOutputNode redstoneNode = new RedstoneOutputNode("redstone", 400, 100);
        graph.addNode(redstoneNode);
        graph.addLink("if_node", "result", "redstone", "power");

        // Broadcast "docked" to the channel
        RadioNetworkManager.setSignalForTesting("42", "docked");
        graph.evaluate(new com.radiologistics.create.node.EvaluationContext(null, null, null, graph));
        // Since signal equals "docked", equalNode is true -> ifNode chooses trueText ("15") -> redstone power 15
        assertEquals(15, redstoneNode.getPowerLevel());

        // Broadcast "flying" to the channel
        RadioNetworkManager.setSignalForTesting("42", "flying");
        graph.evaluate(new com.radiologistics.create.node.EvaluationContext(null, null, null, graph));
        // Since signal is "flying" != "docked", equalNode is false -> ifNode chooses falseText ("2") -> redstone power 2
        assertEquals(2, redstoneNode.getPowerLevel());
    }

    @Test
    public void testLoopProtection() {
        NodeGraph graph = new NodeGraph();

        // Create an IfNode and an EqualNode and wire them in a loop:
        // ifNode:result -> equalNode:a
        // equalNode:result -> ifNode:condition (this forms a cycle)
        
        IfNode ifNode = new IfNode("if1", 0, 0);
        EqualNode equalNode = new EqualNode("equal1", 150, 0);
        graph.addNode(ifNode);
        graph.addNode(equalNode);

        graph.addLink("if1", "result", "equal1", "a");
        graph.addLink("equal1", "result", "if1", "condition");

        RedstoneOutputNode redstone = new RedstoneOutputNode("redstone1", 300, 0);
        graph.addNode(redstone);
        graph.addLink("if1", "result", "redstone1", "power");

        // Evaluate graph. It should evaluate without throwing StackOverflowError or running into an infinite loop.
        assertDoesNotThrow(() -> {
            graph.evaluate(new com.radiologistics.create.node.EvaluationContext(null, null, null, graph));
        });
    }

    @Test
    public void testVariablesGetAndSet() {
        NodeGraph graph = new NodeGraph();

        // Put initial variable value
        graph.getVariables().put("speed", "120");

        // Create VariableNode reading "speed"
        VariableNode speedNode = new VariableNode("var1", 0, 0);
        speedNode.setVariableName("speed");
        graph.addNode(speedNode);

        // Create SetVariableNode writing to "new_speed"
        SetVariableNode setNode = new SetVariableNode("set1", 150, 0);
        setNode.setVariableName("new_speed");
        graph.addNode(setNode);

        // Link them
        graph.addLink("var1", "value", "set1", "value");

        // Evaluate
        graph.evaluate(new com.radiologistics.create.node.EvaluationContext(null, null, null, graph));

        // Check if new_speed has the correct value
        assertEquals("120", graph.getVariables().get("new_speed"));
    }

    @Test
    public void testVariablesGetAndSetWithEvent() {
        NodeGraph graph = new NodeGraph();

        graph.getVariables().put("speed", "120");

        VariableNode speedNode = new VariableNode("var1", 0, 0);
        speedNode.setVariableName("speed");
        graph.addNode(speedNode);

        SetVariableNode setNode = new SetVariableNode("set1", 150, 0);
        setNode.setVariableName("new_speed");
        graph.addNode(setNode);

        BoolNode eventNode = new BoolNode("event1", 0, 100);
        eventNode.setValue(false);
        graph.addNode(eventNode);

        // Link value and event ports
        graph.addLink("var1", "value", "set1", "value");
        graph.addLink("event1", "value", "set1", "event");

        // 1. Evaluate with event=false
        graph.evaluate(new com.radiologistics.create.node.EvaluationContext(null, null, null, graph));
        assertNull(graph.getVariables().get("new_speed")); // Should not write!

        // 2. Evaluate with event=true
        eventNode.setValue(true);
        graph.evaluate(new com.radiologistics.create.node.EvaluationContext(null, null, null, graph));
        assertEquals("120", graph.getVariables().get("new_speed")); // Should write now!
    }

    @Test
    public void testAntennaOutputNodeEvent() {
        AntennaOutputNode antennaNode = new AntennaOutputNode("ant1", 0, 0);
        assertTrue(antennaNode.getInputPorts().contains("event"));
        assertTrue(antennaNode.getInputPorts().contains("channel"));
        assertTrue(antennaNode.getInputPorts().contains("message"));

        // Evaluation context is empty/null, evaluate should return null and not crash
        Object result = antennaNode.evaluate("result", java.util.Map.of("event", "true"),
                new com.radiologistics.create.node.EvaluationContext(null, null, null, null));
        assertNull(result);
    }

    @Test
    public void dumpRedstoneLinkReflection() {
        try {
            Class<?> clazz = Class.forName("com.simibubi.create.content.redstone.link.IRedstoneLinkable");
            System.out.println("=== IRedstoneLinkable Methods ===");
            for (java.lang.reflect.Method m : clazz.getDeclaredMethods()) {
                System.out.print("  " + m.getReturnType().getSimpleName() + " " + m.getName() + "(");
                Class<?>[] params = m.getParameterTypes();
                for (int i = 0; i < params.length; i++) {
                    System.out.print(params[i].getSimpleName());
                    if (i < params.length - 1) System.out.print(", ");
                }
                System.out.println(")");
            }
            
            Class<?> handlerClass = Class.forName("com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler");
            System.out.println("=== RedstoneLinkNetworkHandler Methods ===");
            for (java.lang.reflect.Method m : handlerClass.getDeclaredMethods()) {
                System.out.print("  " + m.getReturnType().getSimpleName() + " " + m.getName() + "(");
                Class<?>[] params = m.getParameterTypes();
                for (int i = 0; i < params.length; i++) {
                    System.out.print(params[i].getSimpleName());
                    if (i < params.length - 1) System.out.print(", ");
                }
                System.out.println(")");
            }
            System.out.println("=== RedstoneLinkNetworkHandler Fields ===");
            for (java.lang.reflect.Field f : handlerClass.getDeclaredFields()) {
                System.out.println("  " + f.getType().getSimpleName() + " " + f.getName());
            }

            Class<?> behaviourClass = Class.forName("com.simibubi.create.content.redstone.link.LinkBehaviour");
            System.out.println("=== LinkBehaviour Methods ===");
            for (java.lang.reflect.Method m : behaviourClass.getDeclaredMethods()) {
                System.out.print("  " + m.getReturnType().getSimpleName() + " " + m.getName() + "(");
                Class<?>[] params = m.getParameterTypes();
                for (int i = 0; i < params.length; i++) {
                    System.out.print(params[i].getSimpleName());
                    if (i < params.length - 1) System.out.print(", ");
                }
                System.out.println(")");
            }

            try {
                Class<?> videoSourceClass = Class.forName("net.mehvahdjukaar.vista.client.video_source.IVideoSource");
                System.out.println("=== IVideoSource Methods ===");
                for (java.lang.reflect.Method m : videoSourceClass.getDeclaredMethods()) {
                    System.out.println("  " + m.getReturnType().getSimpleName() + " " + m.getName());
                }
            } catch (Throwable ignored) {}

            try {
                Class<?> vfBEClass = Class.forName("net.mehvahdjukaar.vista.common.view_finder.ViewFinderBlockEntity");
                System.out.println("=== ViewFinderBlockEntity Methods ===");
                for (java.lang.reflect.Method m : vfBEClass.getDeclaredMethods()) {
                    System.out.println("  " + m.getReturnType().getSimpleName() + " " + m.getName());
                }
                System.out.println("=== ViewFinderBlockEntity Fields ===");
                for (java.lang.reflect.Field f : vfBEClass.getDeclaredFields()) {
                    System.out.println("  " + f.getType().getSimpleName() + " " + f.getName());
                }
            } catch (Throwable ignored) {}

            try {
                String[] potentialClasses = {
                    "rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity",
                    "rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlock",
                    "rbasamoyai.createbigcannons.cannon_mount.CannonMountBlockEntity",
                    "rbasamoyai.createbigcannons.cannon_mount.CannonMountBlock",
                    "rbasamoyai.createbigcannons.cannons.cannon_mount.CannonMountBlockEntity",
                    "rbasamoyai.createbigcannons.cannons.cannon_mount.CannonMountBlock"
                };
                System.out.println("=== CBC Class Search ===");
                for (String clsName : potentialClasses) {
                    try {
                        Class<?> c = Class.forName(clsName);
                        System.out.println("  FOUND: " + clsName);
                        System.out.println("  Methods of " + c.getSimpleName() + ":");
                        for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                            System.out.println("    " + m.getReturnType().getSimpleName() + " " + m.getName());
                        }
                    } catch (ClassNotFoundException ignored) {}
                }
            } catch (Throwable ignored) {}

            // Inspect generic return type of networksIn
            for (java.lang.reflect.Method m : handlerClass.getDeclaredMethods()) {
                if (m.getName().equals("networksIn")) {
                    System.out.println("networksIn generic return type: " + m.getGenericReturnType());
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testViewerNodes() {
        NodeGraph graph = new NodeGraph();

        // 1. BoolViewerNode
        BoolViewerNode boolViewer = new BoolViewerNode("bv", 0, 0);
        graph.addNode(boolViewer);
        BoolNode boolNode = new BoolNode("bn", -100, 0);
        boolNode.setValue(true);
        graph.addNode(boolNode);
        graph.addLink("bn", "value", "bv", "value");

        // 2. NumberViewerNode
        NumberViewerNode numViewer = new NumberViewerNode("nv", 0, 100);
        graph.addNode(numViewer);
        NumberNode numNode = new NumberNode("nn", -100, 100);
        numNode.setValue(3.1415);
        graph.addNode(numNode);
        graph.addLink("nn", "value", "nv", "value");

        // 3. TextViewerNode
        TextViewerNode textViewer = new TextViewerNode("tv", 0, 200);
        graph.addNode(textViewer);
        TextNode textNode = new TextNode("tn", -100, 200);
        textNode.setText("hello viewer");
        graph.addNode(textNode);
        graph.addLink("tn", "value", "tv", "value");

        // Prior to evaluate, they should not have value
        assertFalse(boolViewer.hasValue());
        assertFalse(numViewer.hasValue());
        assertFalse(textViewer.hasValue());

        // Evaluate
        graph.evaluate(new com.radiologistics.create.node.EvaluationContext(null, null, null, graph));

        // After evaluate, they should store the values
        assertTrue(boolViewer.hasValue());
        assertTrue(boolViewer.getLastValue());

        assertTrue(numViewer.hasValue());
        assertEquals(3.1415, numViewer.getLastValue(), 0.0001);

        assertTrue(textViewer.hasValue());
        assertEquals("hello viewer", textViewer.getLastValue());
    }

    @Test
    public void testMathNodes() {
        FloorNode floor = new FloorNode("floor1", 0, 0);
        SqrtNode sqrt = new SqrtNode("sqrt1", 0, 100);
        SquareNode square = new SquareNode("square1", 0, 200);
        Atan2Node atan2 = new Atan2Node("atan2_1", 0, 300);
        OrNode orNode = new OrNode("or1", 0, 400);

        InvertNode invert = new InvertNode("inv1", 0, 500);
        AbsNode abs = new AbsNode("abs1", 0, 600);
        SinNode sin = new SinNode("sin1", 0, 700);
        CosNode cos = new CosNode("cos1", 0, 800);
        TanNode tan = new TanNode("tan1", 0, 900);
        CtgNode ctg = new CtgNode("ctg1", 0, 1000);

        assertEquals(5.0, floor.evaluate("result", java.util.Map.of("number", 5.73), null));
        assertEquals(4.0, sqrt.evaluate("result", java.util.Map.of("number", 16.0), null));
        assertEquals(9.0, square.evaluate("result", java.util.Map.of("number", 3.0), null));
        assertEquals(45L, atan2.evaluate("result", java.util.Map.of("y", 1.0, "x", 1.0), null));
        assertEquals(true, orNode.evaluate("result", java.util.Map.of("a", true, "b", false), null));
        assertEquals(false, orNode.evaluate("result", java.util.Map.of("a", false, "b", false), null));
        assertEquals(245L, invert.evaluate("result", java.util.Map.of("number", -245.0), null));
        assertEquals(-34L, invert.evaluate("result", java.util.Map.of("number", 34.0), null));
        assertEquals(245L, abs.evaluate("result", java.util.Map.of("number", -245.0), null));
        assertEquals(34L, abs.evaluate("result", java.util.Map.of("number", 34.0), null));
        assertEquals(0.5, (Double) sin.evaluate("result", java.util.Map.of("number", 30.0), null), 0.0001);
        assertEquals(0.5, (Double) cos.evaluate("result", java.util.Map.of("number", 60.0), null), 0.0001);
        assertEquals(1.0, (Double) tan.evaluate("result", java.util.Map.of("number", 45.0), null), 0.0001);
        assertEquals(1.0, (Double) ctg.evaluate("result", java.util.Map.of("number", 45.0), null), 0.0001);
    }

    @Test
    public void testSignalDistanceFallback() {
        double dist = RadioNetworkManager.getSignalDistance(null, new net.minecraft.core.BlockPos(0, 0, 0), "42");
        assertEquals(-1.0, dist);
    }

    @Test
    public void testActiveTargetFallback() {
        ActiveTargetNode node = new ActiveTargetNode("at1", 0, 0);
        com.radiologistics.create.node.EvaluationContext context = new com.radiologistics.create.node.EvaluationContext(null, null, null, null);
        
        assertEquals(0.0, node.evaluate("x", java.util.Map.of(), context));
        assertEquals(0.0, node.evaluate("y", java.util.Map.of(), context));
        assertEquals(0.0, node.evaluate("z", java.util.Map.of(), context));
        assertEquals("", node.evaluate("name", java.util.Map.of(), context));
    }

    @Test
    public void testGyroscopePositionFallback() {
        GyroscopePositionNode node = new GyroscopePositionNode("gp1", 0, 0);
        com.radiologistics.create.node.EvaluationContext context = new com.radiologistics.create.node.EvaluationContext(null, null, null, null);
        
        assertEquals(0.0, node.evaluate("x", java.util.Map.of(), context));
        assertEquals(0.0, node.evaluate("y", java.util.Map.of(), context));
        assertEquals(0.0, node.evaluate("z", java.util.Map.of(), context));
    }

    @Test
    public void testCommentNode() {
        CommentNode commentNode = new CommentNode("comment_1", 10.0, 20.0);
        assertEquals("comment", commentNode.getType());
        assertEquals("comment_1", commentNode.getId());
        assertEquals(10.0, commentNode.getX(), 0.001);
        assertEquals(20.0, commentNode.getY(), 0.001);
        assertEquals(0, commentNode.getInputPorts().size());
        assertEquals(0, commentNode.getOutputPorts().size());

        commentNode.setComment("Hello, this is a multi-line comment!\nSecond line.");
        assertEquals("Hello, this is a multi-line comment!\nSecond line.", commentNode.getComment());

        net.minecraft.nbt.CompoundTag properties = commentNode.saveProperties();
        assertEquals("Hello, this is a multi-line comment!\nSecond line.", properties.getString("comment"));

        CommentNode loadedNode = new CommentNode("comment_2", 0, 0);
        loadedNode.loadProperties(properties);
        assertEquals("Hello, this is a multi-line comment!\nSecond line.", loadedNode.getComment());
    }

    public static class MockComputer extends MainComputerBlockEntity {
        private List<BlockPos> jammersList;
        private List<int[]> jammedRangesList;

        public MockComputer() {
            super(null, null);
        }

        public void init(List<BlockPos> jammers, List<int[]> jammedRanges) {
            this.jammersList = jammers;
            this.jammedRangesList = jammedRanges;
        }

        @Override
        public List<BlockPos> getJammers() {
            return jammersList;
        }

        @Override
        public void addActiveJammedRange(int start, int end) {
            jammedRangesList.add(new int[]{start, end});
        }

        @Override
        public BlockPos getModulePos(String type) {
            return null;
        }
    }

    @Test
    public void testJammerNodeClamping() throws Exception {
        JammerNode jammerNode = new JammerNode("jam1", 0, 0);
        assertEquals("jammer", jammerNode.getType());
        assertEquals("jam1", jammerNode.getId());
        assertTrue(jammerNode.getInputPorts().contains("start"));
        assertTrue(jammerNode.getInputPorts().contains("end"));
        assertEquals(0, jammerNode.getOutputPorts().size());

        // Mock Level and make sure isClientSide() returns false
        net.minecraft.world.level.Level mockLevel = org.mockito.Mockito.mock(net.minecraft.world.level.Level.class);
        org.mockito.Mockito.when(mockLevel.isClientSide()).thenReturn(false);

        // Instantiate MockComputer using ReflectionFactory to bypass neoforge registries
        java.lang.reflect.Constructor<Object> objectConstructor = Object.class.getDeclaredConstructor();
        sun.reflect.ReflectionFactory reflectionFactory = sun.reflect.ReflectionFactory.getReflectionFactory();
        java.lang.reflect.Constructor<?> constructor = reflectionFactory.newConstructorForSerialization(
                MockComputer.class, objectConstructor
        );
        MockComputer computer = (MockComputer) constructor.newInstance();
        
        List<BlockPos> jammers = new ArrayList<>();
        List<int[]> ranges = new ArrayList<>();
        computer.init(jammers, ranges);

        EvaluationContext context = new EvaluationContext(mockLevel, BlockPos.ZERO, computer, null);

        // Case 1: 1 Jammer (max range size = 25)
        jammers.add(BlockPos.ZERO);

        // Subcase 1a: Range [10, 34] (size 25) -> should preserve exactly
        jammerNode.evaluate("", Map.of("start", 10, "end", 34), context);
        assertEquals(1, ranges.size());
        assertEquals(10, ranges.get(0)[0]);
        assertEquals(34, ranges.get(0)[1]);
        ranges.clear();

        // Subcase 1a_event: Range [10, 34] with event = false -> should NOT jam
        jammerNode.evaluate("", Map.of("start", 10, "end", 34, "event", false), context);
        assertEquals(0, ranges.size());

        // Subcase 1b: Range [10, 50] (size 41 > 25) -> should clamp to size 25, i.e., [10, 34]
        jammerNode.evaluate("", Map.of("start", 10, "end", 50), context);
        assertEquals(1, ranges.size());
        assertEquals(10, ranges.get(0)[0]);
        assertEquals(34, ranges.get(0)[1]);
        ranges.clear();

        // Subcase 1c: Range [250, 260] (size 11 <= 25) with overflow -> should clamp to [245, 255]
        jammerNode.evaluate("", Map.of("start", 250, "end", 260), context);
        assertEquals(1, ranges.size());
        assertEquals(245, ranges.get(0)[0]);
        assertEquals(255, ranges.get(0)[1]);
        ranges.clear();

        // Case 2: 3 Jammers (max range size = 75)
        jammers.clear();
        jammers.add(BlockPos.ZERO);
        jammers.add(BlockPos.ZERO);
        jammers.add(BlockPos.ZERO);

        // Subcase 2a: Range [100, 200] (size 101 > 75) -> should clamp to size 75, i.e., [100, 174]
        jammerNode.evaluate("", Map.of("start", 100, "end", 200), context);
        assertEquals(1, ranges.size());
        assertEquals(100, ranges.get(0)[0]);
        assertEquals(174, ranges.get(0)[1]);
        ranges.clear();
    }

    @Test
    public void testGyroscopeFacingRotations() throws Exception {
        // Mock Level
        net.minecraft.world.level.Level mockLevel = org.mockito.Mockito.mock(net.minecraft.world.level.Level.class);
        org.mockito.Mockito.when(mockLevel.isClientSide()).thenReturn(false);

        // Instantiate GyroscopeSensorBlockEntity using ReflectionFactory
        java.lang.reflect.Constructor<Object> objectConstructor = Object.class.getDeclaredConstructor();
        sun.reflect.ReflectionFactory reflectionFactory = sun.reflect.ReflectionFactory.getReflectionFactory();
        java.lang.reflect.Constructor<?> constructor = reflectionFactory.newConstructorForSerialization(
                com.radiologistics.create.block.GyroscopeSensorBlockEntity.class, objectConstructor
        );
        com.radiologistics.create.block.GyroscopeSensorBlockEntity gyro = 
                (com.radiologistics.create.block.GyroscopeSensorBlockEntity) constructor.newInstance();

        // Inject fields via reflection
        java.lang.reflect.Field levelField = net.minecraft.world.level.block.entity.BlockEntity.class.getDeclaredField("level");
        levelField.setAccessible(true);
        levelField.set(gyro, mockLevel);

        java.lang.reflect.Field posField = net.minecraft.world.level.block.entity.BlockEntity.class.getDeclaredField("worldPosition");
        posField.setAccessible(true);
        posField.set(gyro, BlockPos.ZERO);

        // Setup Sable reflection:
        // Set mock subLevelAccess to return a pose with a quaternion (rotated 90 degrees around Y axis)
        // A rotation of 90 degrees around Y: w = 0.70710678, y = 0.70710678, x = 0, z = 0
        dev.ryanhcode.sable.companion.DummyQuaternion shipQuat = new dev.ryanhcode.sable.companion.DummyQuaternion(0.0, 0.70710678, 0.0, 0.70710678);
        dev.ryanhcode.sable.companion.DummyPose dummyPose = new dev.ryanhcode.sable.companion.DummyPose(shipQuat);
        
        dev.ryanhcode.sable.companion.SubLevelAccess mockAccess = org.mockito.Mockito.mock(dev.ryanhcode.sable.companion.SubLevelAccess.class);
        org.mockito.Mockito.when(mockAccess.getLogicalPose()).thenReturn(dummyPose);
        
        dev.ryanhcode.sable.companion.SableCompanion.mockSubLevelAccess = mockAccess;

        // Mock BlockState using mockito-inline
        net.minecraft.world.level.block.state.BlockState mockState = org.mockito.Mockito.mock(net.minecraft.world.level.block.state.BlockState.class);
        org.mockito.Mockito.when(mockState.hasProperty(com.radiologistics.create.block.GyroscopeSensorBlock.FACING)).thenReturn(true);

        java.lang.reflect.Field stateField = net.minecraft.world.level.block.entity.BlockEntity.class.getDeclaredField("blockState");
        stateField.setAccessible(true);
        stateField.set(gyro, mockState);

        // Test 1: Facing NORTH
        org.mockito.Mockito.when(mockState.getValue(com.radiologistics.create.block.GyroscopeSensorBlock.FACING)).thenReturn(net.minecraft.core.Direction.NORTH);
        float[] rotNorth = gyro.getContraptionRotation();
        // Since ship is rotated 90 degrees around Y, the Yaw (index 2) should be 90 degrees.
        // Pitch X (index 0) and Pitch Z (index 1) should be 0.
        assertEquals(90.0, rotNorth[2], 0.01);
        assertEquals(0.0, rotNorth[0], 0.01);
        assertEquals(0.0, rotNorth[1], 0.01);

        // Test 2: Facing EAST
        org.mockito.Mockito.when(mockState.getValue(com.radiologistics.create.block.GyroscopeSensorBlock.FACING)).thenReturn(net.minecraft.core.Direction.EAST);
        float[] rotEast = gyro.getContraptionRotation();
        // Facing East is rotated -90 degrees relative to North.
        // So combined rotation is +90 (ship) - 90 (facing East) = 0.
        assertEquals(0.0, rotEast[2], 0.01);

        // Test 3: Facing SOUTH
        org.mockito.Mockito.when(mockState.getValue(com.radiologistics.create.block.GyroscopeSensorBlock.FACING)).thenReturn(net.minecraft.core.Direction.SOUTH);
        float[] rotSouth = gyro.getContraptionRotation();
        // Facing South is rotated 180 degrees.
        // Combined: +90 + 180 = 270 (which is -90 in degrees).
        assertEquals(-90.0, rotSouth[2], 0.01);

        // Test 4: Facing WEST
        org.mockito.Mockito.when(mockState.getValue(com.radiologistics.create.block.GyroscopeSensorBlock.FACING)).thenReturn(net.minecraft.core.Direction.WEST);
        float[] rotWest = gyro.getContraptionRotation();
        // Facing West is rotated +90 degrees.
        // Combined: +90 + 90 = 180.
        assertEquals(180.0, Math.abs(rotWest[2]), 0.01);
    }

    @Test
    public void testTextSplitAndJoinNodes() {
        TextSplitNode splitNode = new TextSplitNode("split1", 0, 0);
        
        // Case 1: First part of "hello world" -> "hello"
        Object res1 = splitNode.evaluate("value", java.util.Map.of("text", "hello world", "part_number", 1), null);
        assertEquals("hello", res1);
        
        // Case 2: Second part of "hello world" -> "world"
        Object res2 = splitNode.evaluate("value", java.util.Map.of("text", "hello world", "part_number", 2), null);
        assertEquals("world", res2);
        
        // Case 3: Out of bounds -> ""
        Object res3 = splitNode.evaluate("value", java.util.Map.of("text", "hello world", "part_number", 3), null);
        assertEquals("", res3);

        // Case 4: Part number as a String double value -> "world"
        Object res4 = splitNode.evaluate("value", java.util.Map.of("text", "hello world", "part_number", "2.0"), null);
        assertEquals("world", res4);

        TextJoinNode joinNode = new TextJoinNode("join1", 0, 0);
        // Concatenating "hello" and "world" -> "helloworld"
        Object resJoin = joinNode.evaluate("value", java.util.Map.of("text1", "hello", "text2", "world"), null);
        assertEquals("helloworld", resJoin);
    }

    @Test
    public void testPosToRotAndRotToPosNodes() {
        PosToRotNode posToRot = new PosToRotNode("p2r", 0, 0);
        RotToPosNode rotToPos = new RotToPosNode("r2p", 0, 0);

        // Test 1: Pos to Rot
        // Let's compute yaw/pitch from (0,0,0) to (0,0,10)
        // Yaw should be 0 (dx=0, dz=10 -> Math.atan2(0, 10) = 0)
        // Pitch should be 0 (dy=0)
        Object yaw = posToRot.evaluate("yaw", java.util.Map.of("x", 0.0, "y", 0.0, "z", 0.0, "x_end", 0.0, "y_end", 0.0, "z_end", 10.0), null);
        Object pitch = posToRot.evaluate("pitch", java.util.Map.of("x", 0.0, "y", 0.0, "z", 0.0, "x_end", 0.0, "y_end", 0.0, "z_end", 10.0), null);
        assertEquals(0L, yaw);
        assertEquals(0L, pitch);

        // From (0,0,0) to (10,0,0)
        // Yaw should be -90 (dx=10, dz=0 -> Math.atan2(-10, 0) = -PI/2 -> -90 degrees)
        Object yaw2 = posToRot.evaluate("yaw", java.util.Map.of("x", 0.0, "y", 0.0, "z", 0.0, "x_end", 10.0, "y_end", 0.0, "z_end", 0.0), null);
        assertEquals(-90L, yaw2);

        // From (0,0,0) to (0,10,0)
        // Pitch should be -90 (dy=10, horizontalDist=0 -> Math.atan2(-10, 0) = -PI/2 -> -90 degrees)
        Object pitch2 = posToRot.evaluate("pitch", java.util.Map.of("x", 0.0, "y", 0.0, "z", 0.0, "x_end", 0.0, "y_end", 10.0, "z_end", 0.0), null);
        assertEquals(-90L, pitch2);

        // Test 2: Rot to Pos
        // Yaw = 0, Pitch = 0, distance = 10, start = (0,0,0) -> dx = 0, dz = 10, dy = 0 -> end = (0, 0, 10)
        Object rx = rotToPos.evaluate("x_out", java.util.Map.of("yaw", 0.0, "pitch", 0.0, "distance", 10.0, "x", 0.0, "y", 0.0, "z", 0.0), null);
        Object ry = rotToPos.evaluate("y_out", java.util.Map.of("yaw", 0.0, "pitch", 0.0, "distance", 10.0, "x", 0.0, "y", 0.0, "z", 0.0), null);
        Object rz = rotToPos.evaluate("z_out", java.util.Map.of("yaw", 0.0, "pitch", 0.0, "distance", 10.0, "x", 0.0, "y", 0.0, "z", 0.0), null);
        assertEquals(0L, rx);
        assertEquals(0L, ry);
        assertEquals(10L, rz);
    }

    @Test
    public void testHelmetRotationNodeNormalization() {
        assertEquals(-180.0, net.minecraft.util.Mth.wrapDegrees(180.0), 0.001);
        assertEquals(-90.0, net.minecraft.util.Mth.wrapDegrees(270.0), 0.001);
        assertEquals(170.0, net.minecraft.util.Mth.wrapDegrees(-190.0), 0.001);
        assertEquals(45.0, net.minecraft.util.Mth.wrapDegrees(45.0), 0.001);
    }

    @Test
    public void testAudioStreamFormatting() throws Exception {
        SoundPlayNode soundPlay = new SoundPlayNode("sound_play1", 0, 0);
        
        net.minecraft.world.level.Level mockLevel = org.mockito.Mockito.mock(net.minecraft.world.level.Level.class);
        org.mockito.Mockito.when(mockLevel.isClientSide()).thenReturn(false);
        org.mockito.Mockito.when(mockLevel.getGameTime()).thenReturn(100L);

        java.lang.reflect.Constructor<Object> objectConstructor = Object.class.getDeclaredConstructor();
        sun.reflect.ReflectionFactory reflectionFactory = sun.reflect.ReflectionFactory.getReflectionFactory();
        java.lang.reflect.Constructor<?> constructor = reflectionFactory.newConstructorForSerialization(
                MockComputer.class, objectConstructor
        );
        MockComputer computer = (MockComputer) constructor.newInstance();

        EvaluationContext context = new EvaluationContext(mockLevel, BlockPos.ZERO, computer, null);
        
        Object streamVal = soundPlay.evaluate("stream", Map.of(), context);
        assertEquals("", streamVal);

        Map<String, Object> inputs = Map.of("event", true, "link", "http://example.com/audio.mp3", "volume", 0.8, "pitch", 1.2);
        soundPlay.evaluate("event", inputs, context);
        Object playingStreamVal = soundPlay.evaluate("stream", inputs, context);
        
        assertTrue(playingStreamVal instanceof String);
        String streamJson = (String) playingStreamVal;
        assertTrue(streamJson.contains("\"audio_type\": \"sound\""));
        assertTrue(streamJson.contains("\"url\": \"http://example.com/audio.mp3\""));
        assertTrue(streamJson.contains("\"volume\": 0.80"));
        assertTrue(streamJson.contains("\"pitch\": 1.20"));
    }

    @Test
    public void testNewMathAndLogicNodes() {
        RandomNode rand = new RandomNode("rand1", 0, 0);
        DegreeVectorNode degVec = new DegreeVectorNode("dv1", 0, 0);

        for (int i = 0; i < 100; i++) {
            Object res = rand.evaluate("result", Map.of("min", 10, "max", 20), null);
            assertTrue(res instanceof Number);
            double val = ((Number) res).doubleValue();
            assertTrue(val >= 10.0 && val <= 20.0);
        }

        assertEquals(90L, degVec.evaluate("result", Map.of("a", 90, "b", 180), null));
        assertEquals(20L, degVec.evaluate("result", Map.of("a", 170, "b", -170), null));
        assertEquals(-20L, degVec.evaluate("result", Map.of("a", -170, "b", 170), null));
        double diff = ((Number) degVec.evaluate("result", Map.of("a", 0, "b", 180), null)).doubleValue();
        assertTrue(Math.abs(diff) == 180.0);
    }
}
