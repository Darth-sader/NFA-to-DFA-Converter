package com.autometa.nfatodfa;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.autometa.nfatodfa.model.NFA;
import com.autometa.nfatodfa.model.SubsetConstruction;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Unit tests for the subset construction engine (pure JVM, no Android). */
public class SubsetConstructionTest {

    /** L = { w ∈ {a,b}* : w ends with "ab" } — the classic 3-state result. */
    private static NFA endsWithAb() {
        Map<String, Map<String, Set<String>>> t = new LinkedHashMap<>();
        t.put("q0", mapOf("a", set("q0", "q1"), "b", set("q0")));
        t.put("q1", mapOf("b", set("q2")));
        t.put("q2", new HashMap<>());
        return new NFA(list("q0", "q1", "q2"), list("a", "b"), "q0",
                set("q2"), false, t);
    }

    /** ε-NFA for (ab)* : a→ q0-q1, ε: q1→q2, b: q2→q3, ε: q3→q0, start & accept q0. */
    private static NFA epsAbStar() {
        Map<String, Map<String, Set<String>>> t = new LinkedHashMap<>();
        t.put("q0", mapOf("a", set("q1")));
        t.put("q1", mapOf(NFA.EPSILON, set("q2")));
        t.put("q2", mapOf("b", set("q3")));
        t.put("q3", mapOf(NFA.EPSILON, set("q0")));
        return new NFA(list("q0", "q1", "q2", "q3"), list("a", "b"), "q0",
                set("q0"), true, t);
    }

    @Test
    public void testEndsWithAb_conversion() {
        SubsetConstruction.Result r = SubsetConstruction.convert(endsWithAb(), true);
        assertEquals(3, r.states.size());
        assertFalse(r.trapUsed);                       // every move is defined, no dead state needed

        SubsetConstruction.DfaState a = r.stateById("A");
        SubsetConstruction.DfaState c = r.stateById("C");
        assertEquals("[q0]", a.nfaStates.toString());
        assertTrue(a.isStart());
        assertFalse(a.isAccept);
        assertEquals("[q0, q2]", c.nfaStates.toString());
        assertTrue(c.isAccept);
        assertEquals("B", r.table.get("A").get("a"));  // A -a-> B
        assertEquals("A", r.table.get("A").get("b"));  // A -b-> A
        assertEquals("C", r.table.get("B").get("b"));  // B -b-> C
        assertEquals("B", r.table.get("C").get("a"));  // C -a-> B
        assertEquals("A", r.table.get("C").get("b"));  // C -b-> A

        // merged edges: 6 arrows with single-symbol labels
        assertEquals(6, r.edges.size());
    }

    @Test
    public void testEpsilonClosure_andSteps() {
        NFA nfa = epsAbStar();
        Set<String> closure = nfa.epsilonClosure(set("q0"));
        assertEquals("[q0]", closure.toString());          // q0 has no outgoing ε
        assertEquals("[q1, q2]", nfa.epsilonClosure(set("q1")).toString());

        SubsetConstruction.Result r = SubsetConstruction.convert(nfa, true);
        assertEquals(4, r.states.size());                  // A, B, C + dead
        assertTrue(r.trapUsed);
        assertEquals("[q0]", r.stateById("A").nfaStates.toString());
        assertTrue(r.stateById("A").isAccept);             // ε-closure keeps q0 accepting
        assertEquals("[q1, q2]", r.stateById("B").nfaStates.toString());
        assertEquals("[q0, q3]", r.stateById("C").nfaStates.toString());
        assertTrue(r.stateById("C").isAccept);
        assertEquals("B", r.table.get("A").get("a"));      // A -a-> B
        assertEquals("dead", r.table.get("A").get("b"));   // A -b-> dead
        assertEquals("C", r.table.get("B").get("b"));      // B -b-> C
        assertEquals("B", r.table.get("C").get("a"));      // C -a-> B

        // step script: every DFA state and every edge appears in order
        int created = 0, edges = 0;
        for (SubsetConstruction.Step s : r.steps) {
            if (s.type == SubsetConstruction.Step.Type.CREATE_STATE) created++;
            if (s.type == SubsetConstruction.Step.Type.CREATE_EDGE) edges++;
        }
        assertEquals(4, created);
        assertEquals(6, edges);
        assertTrue(r.steps.get(r.steps.size() - 1).title.contains("complete"));
    }

    @Test
    public void testNoTrapMode_omitsDeadState() {
        NFA nfa = epsAbStar();
        SubsetConstruction.Result r = SubsetConstruction.convert(nfa, false);
        assertEquals(3, r.states.size());
        assertFalse(r.trapUsed);
        for (SubsetConstruction.DfaEdge e : r.edges) {
            assertFalse("dead".equals(e.to));
        }
        // the INFO steps explain the dropped transitions instead
        boolean explained = false;
        for (SubsetConstruction.Step s : r.steps) {
            if (s.type == SubsetConstruction.Step.Type.INFO && s.title.contains("No move")) explained = true;
        }
        assertTrue(explained);
    }

    @Test
    public void testMove_collectsAllTargets() {
        NFA nfa = endsWithAb();
        assertEquals("[q0, q1]", nfa.move(set("q0"), "a").toString());
        assertEquals("[q0, q2]", nfa.move(set("q0", "q1"), "b").toString());
        assertEquals("[]", nfa.move(set("q2"), "a").toString());
    }

    // ------------------------------------------------------------ tiny helpers

    private static Set<String> set(String... items) {
        return new LinkedHashSet<>(Arrays.asList(items));
    }

    private static List<String> list(String... items) {
        return Arrays.asList(items);
    }

    private static Map<String, Set<String>> mapOf(Object... kv) {
        Map<String, Set<String>> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], (Set<String>) kv[i + 1]);
        return m;
    }
}
