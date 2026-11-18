package com.autometa.nfatodfa.model;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;

/**
 * Standard subset construction (Rabin–Scott). Converts an NFA (optionally with
 * ε-transitions) into an equivalent DFA, and records every intermediate action as
 * an ordered list of {@link Step}s so the UI can replay the construction as an
 * animation.
 */
public final class SubsetConstruction {

    private SubsetConstruction() {}

    /** One state of the resulting DFA. */
    public static class DfaState {
        public final String id;               // "A", "B", ... or "dead"
        public final Set<String> nfaStates;   // sorted set of NFA states it represents
        public final boolean isTrap;
        public boolean isAccept;

        DfaState(String id, Set<String> nfaStates, boolean isTrap) {
            this.id = id;
            this.nfaStates = Collections.unmodifiableSet(new TreeSet<>(nfaStates));
            this.isTrap = isTrap;
        }

        public boolean isStart() {
            return id.equals("A");
        }
    }

    /** One (merged) edge of the resulting DFA diagram. */
    public static class DfaEdge {
        public final String from;      // DfaState id
        public final String to;        // DfaState id
        public final List<String> symbols;

        DfaEdge(String from, String to, List<String> symbols) {
            this.from = from;
            this.to = to;
            this.symbols = symbols;
        }

        public String label() {
            return String.join(",", symbols);
        }
    }

    /** One replayable action of the construction animation. */
    public static class Step {
        public enum Type { CREATE_STATE, CREATE_EDGE, INFO }

        public final Type type;
        public final String title;        // short headline shown in the step card
        public final String detail;       // longer explanation shown under the title
        public final String stateId;      // for CREATE_STATE
        public final String edgeFrom;     // for CREATE_EDGE
        public final String edgeTo;
        public final String edgeSymbol;

        private Step(Type type, String title, String detail,
                     String stateId, String edgeFrom, String edgeTo, String edgeSymbol) {
            this.type = type;
            this.title = title;
            this.detail = detail;
            this.stateId = stateId;
            this.edgeFrom = edgeFrom;
            this.edgeTo = edgeTo;
            this.edgeSymbol = edgeSymbol;
        }

        static Step createState(String id, Set<String> contents, String reason) {
            String name = id.equals("dead") ? "dead (∅)" : id;
            return new Step(Type.CREATE_STATE,
                    "New DFA state " + name,
                    reason + " → this set of NFA states was never seen before, so it becomes DFA state "
                            + name + " = " + NFA.pretty(contents),
                    id, null, null, null);
        }

        static Step createEdge(String from, String to, String symbol, boolean isNewTarget) {
            String toName = to.equals("dead") ? "dead (∅)" : to;
            String extra = isNewTarget
                    ? "Following this arrow leads to state " + toName + "."
                    : "This set was already a DFA state (" + toName + "), so we just add an arrow to it.";
            return new Step(Type.CREATE_EDGE,
                    "Reading '" + symbol + "' from " + from,
                    "Where can " + from + " go when it reads '" + symbol + "'? " + extra,
                    null, from, to, symbol);
        }

        static Step info(String title, String detail) {
            return new Step(Type.INFO, title, detail, null, null, null, null);
        }

        static Step info(String title, String detail, String stateId) {
            return new Step(Type.INFO, title, detail, stateId, null, null, null);
        }
    }

    /** Complete output of the conversion. */
    public static class Result {
        public final List<DfaState> states;
        public final List<DfaEdge> edges;                    // merged, for the final diagram
        public final List<Step> steps;                       // replay script for the animation
        public final Map<String, Map<String, String>> table; // dfaId -> symbol -> dfaId ("" if omitted)
        public final boolean trapUsed;

        Result(List<DfaState> states, List<DfaEdge> edges, List<Step> steps,
               Map<String, Map<String, String>> table, boolean trapUsed) {
            this.states = states;
            this.edges = edges;
            this.steps = steps;
            this.table = table;
            this.trapUsed = trapUsed;
        }

        public DfaState stateById(String id) {
            for (DfaState s : states) if (s.id.equals(id)) return s;
            return null;
        }
    }

    private static final String[] LETTERS = {
            "A","B","C","D","E","F","G","H","I","J","K","L","M","N","O","P","Q","R","S","T","U","V","W","X","Y","Z"
    };

    private static String idFor(int index) {
        return index < LETTERS.length ? LETTERS[index] : "S" + index;
    }

    public static Result convert(NFA nfa, boolean includeTrapState) {
        LinkedHashMap<String, DfaState> dfaStates = new LinkedHashMap<>();
        Map<String, DfaState> byContents = new HashMap<>();   // joined contents -> DfaState
        Map<String, Map<String, String>> table = new LinkedHashMap<>();
        List<Step> steps = new ArrayList<>();
        Queue<DfaState> queue = new ArrayDeque<>();

        DfaState trap = null;

        // --- Step 0: start state = ε-closure({q0}) ------------------------------
        Set<String> startSet = nfa.epsilonClosure(
                Collections.singleton(nfa.startState));
        DfaState start = new DfaState(idFor(0), startSet, false);
        start.isAccept = !intersectionEmpty(startSet, nfa.acceptStates);
        dfaStates.put(start.id, start);
        byContents.put(key(startSet), start);
        queue.add(start);
        steps.add(Step.createState(start.id, startSet,
                (nfa.hasEpsilon ? "ε-closure(" + NFA.pretty(Collections.singleton(nfa.startState)) + ")"
                                : "The start state " + nfa.startState)
                        + " = " + NFA.pretty(startSet)));
        steps.add(Step.info("Ready to explore",
                "We now repeatedly read each input symbol from every DFA state we discover. "
                        + "Each new set of NFA states becomes a new DFA state."));

        int nextId = 1;

        // --- Breadth-first subset construction ---------------------------------
        while (!queue.isEmpty()) {
            DfaState cur = queue.poll();
            steps.add(Step.info("Processing state " + cur.id,
                    "DFA state " + cur.id + " = " + NFA.pretty(cur.nfaStates)
                            + (cur.isAccept ? " (accepting, because it contains an accepting NFA state)" : "")
                            + " — try every input symbol from here.", cur.id));
            for (String symbol : nfa.alphabet) {
                Set<String> moved = nfa.move(cur.nfaStates, symbol);
                Set<String> target = moved.isEmpty()
                        ? Collections.emptySet()
                        : nfa.epsilonClosure(moved);

                if (target.isEmpty()) {
                    if (includeTrapState) {
                        if (trap == null) {
                            trap = new DfaState("dead", Collections.emptySet(), true);
                            dfaStates.put(trap.id, trap);
                            byContents.put(key(trap.nfaStates), trap);
                            steps.add(Step.createState(trap.id, trap.nfaStates,
                                    "No NFA state can be reached, so we add an explicit dead state"));
                        }
                        setTransition(table, cur.id, symbol, trap.id);
                        steps.add(Step.createEdge(cur.id, trap.id, symbol, false));
                    } else {
                        steps.add(Step.info("No move on '" + symbol + "' from " + cur.id,
                                "Reading '" + symbol + "' in " + NFA.pretty(cur.nfaStates)
                                        + " leads to ∅ (the empty set), so this path simply dies — no arrow is drawn."));
                    }
                    continue;
                }

                String targetKey = key(target);
                DfaState t = byContents.get(targetKey);
                boolean isNew = (t == null);
                if (isNew) {
                    t = new DfaState(idFor(nextId++), target, false);
                    t.isAccept = !intersectionEmpty(target, nfa.acceptStates);
                    dfaStates.put(t.id, t);
                    byContents.put(targetKey, t);
                    queue.add(t);
                    steps.add(Step.createState(t.id, target,
                            "Reading '" + symbol + "' from " + cur.id + " gives " + NFA.pretty(target)));
                }
                setTransition(table, cur.id, symbol, t.id);
                steps.add(Step.createEdge(cur.id, t.id, symbol, !isNew));
            }
        }

        // Accepting states of the DFA = every set that contains an accepting NFA state.
        List<String> accepts = new ArrayList<>();
        for (DfaState s : dfaStates.values()) {
            if (!s.isTrap && !intersectionEmpty(s.nfaStates, nfa.acceptStates)) s.isAccept = true;
            if (s.isAccept) accepts.add(s.id);
        }
        steps.add(Step.info("Marking accepting states",
                accepts.isEmpty()
                        ? "No DFA state contains an accepting NFA state."
                        : "A DFA state accepts if its set contains any accepting NFA state. Accepting: "
                          + String.join(", ", accepts) + "."));

        steps.add(Step.info("Conversion complete 🎉",
                "The DFA with " + dfaStates.size() + " state"
                        + (dfaStates.size() == 1 ? "" : "s") + " recognises exactly the same language as the NFA."));

        // --- Merge parallel edges (same from & to) into one labelled edge -------
        Map<String, DfaEdge> merged = new LinkedHashMap<>();
        for (DfaState from : dfaStates.values()) {
            Map<String, String> row = table.get(from.id);
            if (row == null) continue;
            for (Map.Entry<String, String> e : row.entrySet()) {
                if (e.getValue().isEmpty()) continue;
                String k = from.id + "→" + e.getValue();
                DfaEdge edge = merged.get(k);
                if (edge == null) {
                    List<String> syms = new ArrayList<>();
                    syms.add(e.getKey());
                    merged.put(k, new DfaEdge(from.id, e.getValue(), syms));
                } else if (!edge.symbols.contains(e.getKey())) {
                    edge.symbols.add(e.getKey());
                }
            }
        }

        return new Result(new ArrayList<>(dfaStates.values()),
                new ArrayList<>(merged.values()), steps, table, trap != null);
    }

    // ------------------------------------------------------------------ helpers

    private static String key(Set<String> contents) {
        return String.join("\u0001", new TreeSet<>(contents));
    }

    private static boolean intersectionEmpty(Set<String> a, Set<String> b) {
        for (String s : a) if (b.contains(s)) return false;
        return true;
    }

    private static void setTransition(Map<String, Map<String, String>> table,
                                      String from, String symbol, String to) {
        table.computeIfAbsent(from, k -> new LinkedHashMap<>()).put(symbol, to);
    }
}
