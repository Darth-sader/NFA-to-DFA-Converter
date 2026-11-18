package com.autometa.nfatodfa.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * A nondeterministic finite automaton, optionally with epsilon (ε) transitions.
 * Transitions map: state -> symbol -> set of destination states.
 */
public class NFA {

    public static final String EPSILON = "ε";

    public final List<String> states;
    public final List<String> alphabet;   // does not include ε
    public final String startState;
    public final Set<String> acceptStates;
    public final boolean hasEpsilon;
    public final Map<String, Map<String, Set<String>>> transitions;

    public NFA(List<String> states, List<String> alphabet, String startState,
               Set<String> acceptStates, boolean hasEpsilon,
               Map<String, Map<String, Set<String>>> transitions) {
        this.states = Collections.unmodifiableList(new ArrayList<>(states));
        this.alphabet = Collections.unmodifiableList(alphabet);
        this.startState = startState;
        this.acceptStates = Collections.unmodifiableSet(new TreeSet<>(acceptStates));
        this.hasEpsilon = hasEpsilon;
        this.transitions = transitions;
    }

    /** All states reachable from any of {@code sources} by one edge labelled {@code symbol}. */
    public Set<String> move(Set<String> sources, String symbol) {
        Set<String> out = new TreeSet<>();
        for (String s : sources) {
            Set<String> d = destinations(s, symbol);
            if (d != null) out.addAll(d);
        }
        return out;
    }

    /** ε-closure: all states reachable from {@code sources} using only ε edges (plus the sources). */
    public Set<String> epsilonClosure(Set<String> sources) {
        Set<String> closure = new TreeSet<>(sources);
        ArrayDeque<String> queue = new ArrayDeque<>(sources);
        while (!queue.isEmpty()) {
            String s = queue.poll();
            Set<String> d = destinations(s, EPSILON);
            if (d != null) {
                for (String t : d) {
                    if (closure.add(t)) queue.add(t);
                }
            }
        }
        return closure;
    }

    public Set<String> destinations(String state, String symbol) {
        Map<String, Set<String>> row = transitions.get(state);
        return row == null ? null : row.get(symbol);
    }

    /** Sorted, comma-joined contents of a state set, e.g. "{q0, q1}". */
    public static String pretty(Set<String> states) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (String s : states) {
            if (!first) sb.append(", ");
            sb.append(s);
            first = false;
        }
        return sb.append('}').toString();
    }
}
