package com.radiologistics.create.node.nodes;

import java.util.*;

public class JavaLikeScriptInterpreter {

    public interface Stmt {
        void execute(Map<String, Double> vars);
    }

    public static class BlockStmt implements Stmt {
        public final List<Stmt> statements = new ArrayList<>();
        @Override
        public void execute(Map<String, Double> vars) {
            for (Stmt stmt : statements) {
                stmt.execute(vars);
            }
        }
    }

    public static class IfStmt implements Stmt {
        public final String conditionExpr;
        public final Stmt thenBranch;
        public final Stmt elseBranch;

        public IfStmt(String conditionExpr, Stmt thenBranch, Stmt elseBranch) {
            this.conditionExpr = conditionExpr;
            this.thenBranch = thenBranch;
            this.elseBranch = elseBranch;
        }

        @Override
        public void execute(Map<String, Double> vars) {
            double cond = ExpressionEvaluator.evaluate(conditionExpr, vars);
            if (cond != 0.0) {
                thenBranch.execute(vars);
            } else if (elseBranch != null) {
                elseBranch.execute(vars);
            }
        }
    }

    public static class AssignStmt implements Stmt {
        public final String varName;
        public final String expr;

        public AssignStmt(String varName, String expr) {
            this.varName = varName;
            this.expr = expr;
        }

        @Override
        public void execute(Map<String, Double> vars) {
            double val = ExpressionEvaluator.evaluate(expr, vars);
            vars.put(varName, val);
        }
    }

    public static Stmt parse(String code) {
        List<String> tokens = tokenize(code);
        return parseProgram(tokens);
    }

    private static List<String> tokenize(String code) {
        List<String> tokens = new ArrayList<>();
        int i = 0;
        int len = code.length();
        while (i < len) {
            char c = code.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c == '/' && i + 1 < len && code.charAt(i + 1) == '/') {
                i += 2;
                while (i < len && code.charAt(i) != '\n' && code.charAt(i) != '\r') {
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < len && code.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < len && !(code.charAt(i) == '*' && code.charAt(i + 1) == '/')) {
                    i++;
                }
                i += 2;
                continue;
            }
            if (i + 1 < len) {
                String op2 = code.substring(i, i + 2);
                if (op2.equals("==") || op2.equals("!=") || op2.equals("<=") || op2.equals(">=") || op2.equals("&&") || op2.equals("||")) {
                    tokens.add(op2);
                    i += 2;
                    continue;
                }
            }
            if (c == '{' || c == '}' || c == '(' || c == ')' || c == ';' || c == '=' || c == '<' || c == '>' || c == '+' || c == '-' || c == '*' || c == '/' || c == '%' || c == ',' || c == '!') {
                tokens.add(String.valueOf(c));
                i++;
                continue;
            }
            if (Character.isJavaIdentifierStart(c)) {
                int start = i;
                while (i < len && (Character.isJavaIdentifierPart(code.charAt(i)) || code.charAt(i) == '.')) {
                    i++;
                }
                tokens.add(code.substring(start, i));
                continue;
            }
            if (Character.isDigit(c) || c == '.') {
                int start = i;
                while (i < len && (Character.isDigit(code.charAt(i)) || code.charAt(i) == '.')) {
                    i++;
                }
                tokens.add(code.substring(start, i));
                continue;
            }
            tokens.add(String.valueOf(c));
            i++;
        }
        return tokens;
    }

    private static Stmt parseProgram(List<String> tokens) {
        BlockStmt program = new BlockStmt();
        int[] index = new int[]{0};
        while (index[0] < tokens.size()) {
            Stmt stmt = parseStatement(tokens, index);
            if (stmt != null) {
                program.statements.add(stmt);
            }
        }
        return program;
    }

    private static Stmt parseStatement(List<String> tokens, int[] index) {
        if (index[0] >= tokens.size()) return null;
        String tok = tokens.get(index[0]);
        if (tok.equals("{")) {
            index[0]++;
            BlockStmt block = new BlockStmt();
            while (index[0] < tokens.size() && !tokens.get(index[0]).equals("}")) {
                Stmt stmt = parseStatement(tokens, index);
                if (stmt != null) {
                    block.statements.add(stmt);
                }
            }
            if (index[0] < tokens.size()) {
                index[0]++;
            }
            return block;
        }
        if (tok.equals("if")) {
            index[0]++;
            if (index[0] >= tokens.size() || !tokens.get(index[0]).equals("(")) return null;
            index[0]++;

            int parenDepth = 1;
            List<String> condToks = new ArrayList<>();
            while (index[0] < tokens.size()) {
                String t = tokens.get(index[0]);
                if (t.equals("(")) parenDepth++;
                else if (t.equals(")")) {
                    parenDepth--;
                    if (parenDepth == 0) {
                        index[0]++;
                        break;
                    }
                }
                condToks.add(t);
                index[0]++;
            }
            String condExpr = String.join(" ", condToks);
            Stmt thenBranch = parseStatement(tokens, index);
            Stmt elseBranch = null;
            if (index[0] < tokens.size() && tokens.get(index[0]).equals("else")) {
                index[0]++;
                elseBranch = parseStatement(tokens, index);
            }
            return new IfStmt(condExpr, thenBranch, elseBranch);
        }

        if (tok.equals("double") || tok.equals("int") || tok.equals("var") || tok.equals("float")) {
            index[0]++;
            if (index[0] >= tokens.size()) return null;
            tok = tokens.get(index[0]);
        }

        String varName = tok;
        index[0]++;
        if (index[0] >= tokens.size() || !tokens.get(index[0]).equals("=")) {
            if (index[0] < tokens.size() && tokens.get(index[0]).equals(";")) {
                index[0]++;
            }
            return null;
        }
        index[0]++;

        List<String> exprToks = new ArrayList<>();
        while (index[0] < tokens.size() && !tokens.get(index[0]).equals(";")) {
            exprToks.add(tokens.get(index[0]));
            index[0]++;
        }
        if (index[0] < tokens.size()) {
            index[0]++;
        }
        String exprStr = String.join(" ", exprToks);
        return new AssignStmt(varName, exprStr);
    }
}
