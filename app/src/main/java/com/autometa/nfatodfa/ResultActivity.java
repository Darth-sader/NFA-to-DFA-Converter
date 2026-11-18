package com.autometa.nfatodfa;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ViewFlipper;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.autometa.nfatodfa.model.NFA;
import com.autometa.nfatodfa.model.SubsetConstruction;
import com.autometa.nfatodfa.view.AutomatonView;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shows the conversion result: a step-by-step animated replay of the subset
 * construction, the full NFA and DFA state diagrams, and both transition tables.
 */
public class ResultActivity extends AppCompatActivity {

    private NFA nfa;
    private SubsetConstruction.Result result;

    private AutomatonView dfaAnimView, nfaView, dfaView;
    private TextView tvStepTitle, tvStepDetail, tvStepProgress, tvSummary, btnPlay, btnSpeed;
    private ViewFlipper flipper;
    private LinearLayout tableContainer;

    private int stepIndex = -1;
    private boolean playing = false;
    private int speedIndex = 0;
    private static final long[] DELAYS = {1500, 850, 420};
    private static final String[] SPEED_LABELS = {"1×", "2×", "4×"};

    private String activeNodeId = null;
    private boolean nfaAnimatedIn = false, dfaAnimatedIn = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::advanceDuringPlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_result);
        View root = findViewById(R.id.resultRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        nfa = readNfa();
        result = SubsetConstruction.convert(nfa, getIntent().getBooleanExtra("trap", true));

        tvSummary = findViewById(R.id.tvSummary);
        flipper = findViewById(R.id.flipper);
        dfaAnimView = findViewById(R.id.dfaAnimView);
        nfaView = findViewById(R.id.nfaView);
        dfaView = findViewById(R.id.dfaView);
        tvStepTitle = findViewById(R.id.tvStepTitle);
        tvStepDetail = findViewById(R.id.tvStepDetail);
        tvStepProgress = findViewById(R.id.tvStepProgress);
        btnPlay = findViewById(R.id.btnPlay);
        btnSpeed = findViewById(R.id.btnSpeed);
        tableContainer = findViewById(R.id.tableContainer);

        tvSummary.setText("NFA states " + NFA.pretty(new LinkedHashSet<>(nfa.states))
                + "   ·   Σ = " + NFA.pretty(new LinkedHashSet<>(nfa.alphabet))
                + (nfa.hasEpsilon ? " (+ε)" : "")
                + "   ·   start " + nfa.startState);

        // ---- NFA diagram (static, revealed with a staggered animation) ----
        nfaView.setGraph(buildNfaNodes(), buildNfaEdges(nfa, false));
        // ---- final DFA diagram (static) ----
        dfaView.setGraph(buildDfaNodes(), buildDfaEdges(true));
        // ---- animation view (starts empty, grows step by step) ----
        dfaAnimView.setGraph(buildDfaNodes(), buildDfaEdges(false));

        // ---- tables ----
        buildTables();

        // ---- controls ----
        findViewById(R.id.btnRestart).setOnClickListener(v -> { pause(); setStepIndex(-1, false); });
        findViewById(R.id.btnPrev).setOnClickListener(v -> { pause(); setStepIndex(Math.max(stepIndex - 1, -1), false); });
        findViewById(R.id.btnNext).setOnClickListener(v -> { pause(); setStepIndex(Math.min(stepIndex + 1, result.steps.size() - 1), true); });
        btnPlay.setOnClickListener(v -> togglePlay());
        btnSpeed.setOnClickListener(v -> cycleSpeed());
        ((TextView) findViewById(R.id.tvSpeed)).setText(SPEED_LABELS[speedIndex]);

        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_animate) flipper.setDisplayedChild(0);
            else if (id == R.id.nav_nfa) {
                flipper.setDisplayedChild(1);
                if (!nfaAnimatedIn) { nfaView.animateEntrance(); nfaAnimatedIn = true; }
            } else if (id == R.id.nav_dfa) {
                flipper.setDisplayedChild(2);
                if (!dfaAnimatedIn) { dfaView.animateEntrance(); dfaAnimatedIn = true; }
            } else flipper.setDisplayedChild(3);
            return true;
        });

        updateCard();
    }

    // ------------------------------------------------------------- input extras

    private NFA readNfa() {
        String[] states = getIntent().getStringArrayExtra("states");
        String[] alphabet = getIntent().getStringArrayExtra("alphabet");
        String[] accept = getIntent().getStringArrayExtra("accept");
        ArrayList<String> trans = getIntent().getStringArrayListExtra("trans");
        String start = getIntent().getStringExtra("start");
        boolean eps = getIntent().getBooleanExtra("eps", false);

        if (states == null || alphabet == null || accept == null || trans == null || start == null) {
            // Defensive fallback (should never happen): tiny demo NFA.
            states = new String[]{"q0", "q1", "q2"};
            alphabet = new String[]{"a", "b"};
            accept = new String[]{"q2"};
            start = "q0";
            trans = new ArrayList<>(List.of("q0|a|q0,q1", "q0|b|q0", "q1|b|q2"));
        }

        Map<String, Map<String, Set<String>>> transitions = new HashMap<>();
        for (String row : trans) {
            String[] parts = row.split("\\|", -1);
            if (parts.length < 3) continue;
            Set<String> targets = new LinkedHashSet<>(List.of(parts[2].split(",")));
            transitions.computeIfAbsent(parts[0], k -> new HashMap<>()).put(parts[1], targets);
        }
        return new NFA(Arrays.asList(states), Arrays.asList(alphabet), start,
                new LinkedHashSet<>(Arrays.asList(accept)), eps, transitions);
    }

    // ------------------------------------------------------------- graph building

    private List<AutomatonView.GNode> buildNfaNodes() {
        List<AutomatonView.GNode> nodes = new ArrayList<>();
        for (String s : nfa.states) {
            nodes.add(new AutomatonView.GNode(s, s, null,
                    s.equals(nfa.startState), nfa.acceptStates.contains(s), false));
        }
        return nodes;
    }

    /** Merged (stateA,stateB) → "a,b" edges. */
    private List<AutomatonView.GEdge> buildNfaEdges(NFA nfa, boolean emptyLabels) {
        Map<String, List<String>> merged = new HashMap<>();
        for (String from : nfa.states) {
            Map<String, Set<String>> row = nfa.transitions.get(from);
            if (row == null) continue;
            for (Map.Entry<String, Set<String>> e : row.entrySet()) {
                for (String to : e.getValue()) {
                    merged.computeIfAbsent(from + "→" + to, k -> new ArrayList<>()).add(e.getKey());
                }
            }
        }
        List<AutomatonView.GEdge> edges = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : merged.entrySet()) {
            String[] ft = e.getKey().split("→");
            String label = emptyLabels ? "" : joinSymbols(e.getValue(), nfa.alphabet);
            edges.add(new AutomatonView.GEdge(ft[0], ft[1], label));
        }
        return edges;
    }

    private static String joinSymbols(List<String> symbols, List<String> alphabetOrder) {
        // stable order: alphabet order first, then anything else (e.g. ε) at the end
        List<String> ordered = new ArrayList<>();
        for (String s : alphabetOrder) if (symbols.contains(s)) ordered.add(s);
        for (String s : symbols) if (!ordered.contains(s)) ordered.add(s);
        return String.join(",", ordered);
    }

    private List<AutomatonView.GNode> buildDfaNodes() {
        List<AutomatonView.GNode> nodes = new ArrayList<>();
        for (SubsetConstruction.DfaState s : result.states) {
            String sub = s.isTrap ? "∅" : NFA.pretty(s.nfaStates);
            nodes.add(new AutomatonView.GNode(s.id, s.id, sub, s.isStart(), s.isAccept, s.isTrap));
        }
        return nodes;
    }

    /**
     * @param finalLabels true → edges carry their complete merged label ("a,b"),
     *                    false → empty labels that grow during the step animation.
     */
    private List<AutomatonView.GEdge> buildDfaEdges(boolean finalLabels) {
        List<AutomatonView.GEdge> edges = new ArrayList<>();
        for (SubsetConstruction.DfaEdge e : result.edges) {
            edges.add(new AutomatonView.GEdge(e.from, e.to, finalLabels ? e.label() : ""));
        }
        return edges;
    }

    // ------------------------------------------------------------- step animation

    private void togglePlay() {
        if (playing) { pause(); return; }
        if (stepIndex >= result.steps.size() - 1) setStepIndex(-1, false);
        playing = true;
        btnPlay.setText("II");
        handler.postDelayed(tick, stepIndex < 0 ? 200 : DELAYS[speedIndex]);
    }

    private void pause() {
        playing = false;
        btnPlay.setText("▶");
        handler.removeCallbacks(tick);
    }

    private void advanceDuringPlay() {
        if (!playing) return;
        if (stepIndex + 1 <= result.steps.size() - 1) {
            setStepIndex(stepIndex + 1, true);
            handler.postDelayed(tick, DELAYS[speedIndex]);
        } else {
            pause();
            if (activeNodeId != null) dfaAnimView.setActiveNode(activeNodeId, false);
            activeNodeId = null;
        }
    }

    private void cycleSpeed() {
        speedIndex = (speedIndex + 1) % DELAYS.length;
        btnSpeed.setText(SPEED_LABELS[speedIndex]);
        ((TextView) findViewById(R.id.tvSpeed)).setText(SPEED_LABELS[speedIndex]);
    }

    /** Move the animation to {@code target} (-1 = empty DFA). Steps are replayed
     *  without animation when jumping backwards. */
    private void setStepIndex(int target, boolean animateStep) {
        if (target < stepIndex) {
            dfaAnimView.setGraph(buildDfaNodes(), buildDfaEdges(false));
            if (activeNodeId != null) dfaAnimView.setActiveNode(activeNodeId, false);
            activeNodeId = null;
            stepIndex = -1;
        }
        while (stepIndex < target) {
            stepIndex++;
            applyStep(result.steps.get(stepIndex), animateStep && stepIndex == target);
        }
        updateCard();
    }

    private void applyStep(SubsetConstruction.Step s, boolean animate) {
        switch (s.type) {
            case CREATE_STATE:
                dfaAnimView.revealNode(s.stateId, animate);
                break;
            case CREATE_EDGE:
                dfaAnimView.revealEdge(s.edgeFrom, s.edgeTo, s.edgeSymbol, animate);
                break;
            case INFO:
                if (s.stateId != null) {
                    if (activeNodeId != null && !activeNodeId.equals(s.stateId)) {
                        dfaAnimView.setActiveNode(activeNodeId, false);
                    }
                    dfaAnimView.setActiveNode(s.stateId, animate);
                    activeNodeId = s.stateId;
                }
                break;
        }
    }

    private void updateCard() {
        int total = result.steps.size();
        if (stepIndex < 0) {
            tvStepProgress.setText("Step 0/" + total);
            tvStepTitle.setText(R.string.step_ready_title);
            tvStepDetail.setText(R.string.step_ready_detail);
        } else {
            SubsetConstruction.Step s = result.steps.get(stepIndex);
            tvStepProgress.setText("Step " + (stepIndex + 1) + "/" + total);
            tvStepTitle.setText(s.title);
            tvStepDetail.setText(s.detail);
        }
    }

    @Override protected void onDestroy() {
        pause();
        super.onDestroy();
    }

    // ------------------------------------------------------------- tables

    private void buildTables() {
        tableContainer.removeAllViews();

        tableContainer.addView(sectionTitle("NFA transition table δ"));
        tableContainer.addView(buildNfaTable());
        tableContainer.addView(note(nfa.hasEpsilon
                ? "ε is a zero-character move: it is never “read” from the input — the subset construction replaces it with ε-closures."
                : "This NFA has no ε-transitions, so ε-closure(x) = {x} everywhere."));

        tableContainer.addView(sectionTitle("DFA transition table (result)"));
        tableContainer.addView(buildDfaTable());
        StringBuilder legend = new StringBuilder();
        for (SubsetConstruction.DfaState s : result.states) {
            if (s.isTrap) continue;
            legend.append(s.isStart() ? "→ " : "").append(s.id)
                    .append(" = ").append(NFA.pretty(s.nfaStates))
                    .append(s.isAccept ? "  ✓accepting" : "").append("\n");
        }
        tableContainer.addView(note(legend.toString().trim()
                + (result.trapUsed ? "\n\ndead (∅) is a non-accepting sink: once the input dies, it can never accept."
                                   : "\n\nA DFA state accepts if its set contains any accepting NFA state.")));
    }

    private TextView sectionTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xFF6C63FF);
        tv.setTextSize(16);
        tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        int m = (int) (6 * getResources().getDisplayMetrics().density);
        tv.setPadding(0, m, 0, m);
        return tv;
    }

    private TextView note(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xFF9FB0D0);
        tv.setTextSize(12);
        float d = getResources().getDisplayMetrics().density;
        tv.setPadding(0, (int) (2 * d), 0, (int) (14 * d));
        return tv;
    }

    private TableLayout buildNfaTable() {
        float d = getResources().getDisplayMetrics().density;
        List<String> symbols = new ArrayList<>(nfa.alphabet);
        if (nfa.hasEpsilon) symbols.add(NFA.EPSILON);

        TableLayout table = new TableLayout(this);
        TableRow header = new TableRow(this);
        header.addView(cell("state", d, true, false, false));
        for (String sym : symbols) header.addView(cell(sym, d, true, false, false));
        table.addView(header);

        for (String state : nfa.states) {
            TableRow row = new TableRow(this);
            boolean acc = nfa.acceptStates.contains(state);
            String name = (state.equals(nfa.startState) ? "→ " : "") + state + (acc ? " *✓" : "");
            row.addView(cell(name, d, true, acc, false));
            Map<String, Set<String>> t = nfa.transitions.get(state);
            for (String sym : symbols) {
                Set<String> targets = t == null ? null : t.get(sym);
                String txt = targets == null || targets.isEmpty() ? "—" : String.join(",", targets);
                row.addView(cell(txt, d, false, acc, false));
            }
            table.addView(row);
        }
        return table;
    }

    private TableLayout buildDfaTable() {
        float d = getResources().getDisplayMetrics().density;
        TableLayout table = new TableLayout(this);
        TableRow header = new TableRow(this);
        header.addView(cell("state", d, true, false, false));
        for (String sym : nfa.alphabet) header.addView(cell(sym, d, true, false, false));
        table.addView(header);

        for (SubsetConstruction.DfaState s : result.states) {
            TableRow row = new TableRow(this);
            // first column: id + contents
            LinearLayout cellBox = new LinearLayout(this);
            cellBox.setOrientation(LinearLayout.VERTICAL);
            cellBox.setBackgroundResource(R.drawable.bg_input);
            TextView id = new TextView(this);
            id.setText((s.isStart() ? "→ " : "") + s.id + (s.isAccept ? " *✓" : ""));
            id.setTextColor(s.isTrap ? 0xFF6B7694 : 0xFFF2F5FF);
            id.setTextSize(13);
            id.setTypeface(id.getTypeface(), android.graphics.Typeface.BOLD);
            TextView contents = new TextView(this);
            contents.setText(s.isTrap ? "∅" : NFA.pretty(s.nfaStates));
            contents.setTextColor(0xFF9FB0D0);
            contents.setTextSize(9);
            cellBox.setPadding((int) (8 * d), (int) (6 * d), (int) (8 * d), (int) (6 * d));
            cellBox.addView(id);
            cellBox.addView(contents);
            TableRow.LayoutParams p = new TableRow.LayoutParams();
            p.setMargins((int) (3 * d), (int) (3 * d), (int) (3 * d), (int) (3 * d));
            row.addView(cellBox, p);

            Map<String, String> t = result.table.get(s.id);
            for (String sym : nfa.alphabet) {
                String target = t == null ? null : t.get(sym);
                String txt = target == null || target.isEmpty()
                        ? (result.trapUsed ? "—" : "∅") : target;
                if (txt.equals("dead")) txt = "dead";
                row.addView(cell(txt, d, false, s.isAccept, s.isTrap));
            }
            table.addView(row);
        }
        return table;
    }

    private TextView cell(String text, float d, boolean header, boolean acceptRow, boolean trapRow) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(header ? 13 : 12.5f);
        tv.setTypeface(tv.getTypeface(), header || acceptRow ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        if (header) tv.setTextColor(0xFF00E5FF);
        else if (trapRow) tv.setTextColor(0xFF6B7694);
        else tv.setTextColor(0xFFF2F5FF);
        tv.setBackgroundResource(R.drawable.bg_input);
        if (acceptRow) tv.setBackgroundColor(0x1400E676);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding((int) (10 * d), (int) (8 * d), (int) (10 * d), (int) (8 * d));
        TableRow.LayoutParams p = new TableRow.LayoutParams();
        p.setMargins((int) (3 * d), (int) (3 * d), (int) (3 * d), (int) (3 * d));
        tv.setLayoutParams(p);
        return tv;
    }
}
