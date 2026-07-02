package com.radiologistics.create.node.nodes;

import java.util.*;

public class ExpressionEvaluator {
    public static double evaluate(String expression, Map<String, Double> variables) {
        if (expression == null || expression.trim().isEmpty()) {
            return 0.0;
        }
        try {
            return new Parser(expression, variables).parse();
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static class Parser {
        private final String str;
        private final Map<String, Double> variables;
        private int pos = -1;
        private int ch;

        public Parser(String str, Map<String, Double> variables) {
            this.str = str;
            this.variables = variables;
        }

        private void nextChar() {
            ch = (++pos < str.length()) ? str.charAt(pos) : -1;
        }

        private boolean eat(int charToEat) {
            while (ch == ' ') nextChar();
            if (ch == charToEat) {
                nextChar();
                return true;
            }
            return false;
        }

        private boolean eatStr(String s) {
            while (ch == ' ') nextChar();
            if (pos < 0 || pos >= str.length()) return false;
            int len = s.length();
            if (pos + len <= str.length() && str.substring(pos, pos + len).equals(s)) {
                for (int i = 0; i < len; i++) nextChar();
                return true;
            }
            return false;
        }

        public double parse() {
            nextChar();
            double x = parseLogicalOr();
            while (ch == ' ') nextChar();
            if (pos < str.length()) throw new RuntimeException("Unexpected character: " + (char)ch);
            return x;
        }

        private double parseLogicalOr() {
            double x = parseLogicalAnd();
            for (;;) {
                if (eatStr("||")) {
                    double right = parseLogicalAnd();
                    x = (x != 0.0 || right != 0.0) ? 1.0 : 0.0;
                } else return x;
            }
        }

        private double parseLogicalAnd() {
            double x = parseEquality();
            for (;;) {
                if (eatStr("&&")) {
                    double right = parseEquality();
                    x = (x != 0.0 && right != 0.0) ? 1.0 : 0.0;
                } else return x;
            }
        }

        private double parseEquality() {
            double x = parseRelational();
            for (;;) {
                if (eatStr("==")) {
                    x = (x == parseRelational()) ? 1.0 : 0.0;
                } else if (eatStr("!=")) {
                    x = (x != parseRelational()) ? 1.0 : 0.0;
                } else return x;
            }
        }

        private double parseRelational() {
            double x = parseExpression();
            for (;;) {
                if (eatStr("<=")) {
                    x = (x <= parseExpression()) ? 1.0 : 0.0;
                } else if (eatStr(">=")) {
                    x = (x >= parseExpression()) ? 1.0 : 0.0;
                } else if (eatStr("<")) {
                    x = (x < parseExpression()) ? 1.0 : 0.0;
                } else if (eatStr(">")) {
                    x = (x > parseExpression()) ? 1.0 : 0.0;
                } else return x;
            }
        }

        private double parseExpression() {
            double x = parseTerm();
            for (;;) {
                if      (eat('+')) x += parseTerm();
                else if (eat('-')) x -= parseTerm();
                else return x;
            }
        }

        private double parseTerm() {
            double x = parseFactor();
            for (;;) {
                if      (eat('*')) x *= parseFactor();
                else if (eat('/')) {
                    double div = parseFactor();
                    x = (div == 0.0) ? 0.0 : x / div;
                }
                else if (eat('%')) {
                    double mod = parseFactor();
                    x = (mod == 0.0) ? 0.0 : x % mod;
                }
                else return x;
            }
        }

        private double parseFactor() {
            if (eat('+')) return parseFactor();
            if (eat('-')) return -parseFactor();
            if (eat('!')) return (parseFactor() == 0.0) ? 1.0 : 0.0;

            double x;
            int startPos = this.pos;
            if (eat('(')) {
                x = parseLogicalOr();
                eat(')');
            } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                try {
                    x = Double.parseDouble(str.substring(startPos, this.pos));
                } catch (NumberFormatException e) {
                    x = 0.0;
                }
            } else if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || ch == '_') {
                while ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch == '_') nextChar();
                String name = str.substring(startPos, this.pos);
                if (eat('(')) {
                    List<Double> args = new ArrayList<>();
                    if (!eat(')')) {
                        args.add(parseLogicalOr());
                        while (eat(',')) {
                            args.add(parseLogicalOr());
                        }
                        eat(')');
                    }
                    x = evalFunc(name, args);
                } else {
                    x = variables.getOrDefault(name, 0.0);
                }
            } else {
                throw new RuntimeException("Unexpected: " + (char)ch);
            }

            if (eat('^')) x = Math.pow(x, parseFactor());

            return x;
        }

        private double evalFunc(String name, List<Double> args) {
            double first = args.isEmpty() ? 0.0 : args.get(0);
            return switch (name.toLowerCase()) {
                case "sin" -> Math.sin(first);
                case "cos" -> Math.cos(first);
                case "tan" -> Math.tan(first);
                case "sqrt" -> Math.sqrt(first);
                case "abs" -> Math.abs(first);
                case "rad" -> Math.toRadians(first);
                case "deg" -> Math.toDegrees(first);
                case "pow" -> Math.pow(first, args.size() > 1 ? args.get(1) : 0.0);
                case "min" -> Math.min(first, args.size() > 1 ? args.get(1) : 0.0);
                case "max" -> Math.max(first, args.size() > 1 ? args.get(1) : 0.0);
                default -> 0.0;
            };
        }
    }
}
