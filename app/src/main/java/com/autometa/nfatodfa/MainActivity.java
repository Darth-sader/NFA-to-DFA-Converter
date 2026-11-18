package com.autometa.nfatodfa;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.autometa.nfatodfa.model.NFA;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Input screen: define an NFA (states, alphabet, start, accepting states and the
 * transition table) and hand it to {@link ResultActivity} for the animated
 * subset construction.
 */
public class MainActivity extends AppCompatActivity {

    private TextInputEditText etStates, etAlphabet, etAccept;
    private MaterialAutoCompleteTextView actStart;
    private TextInputLayout tilStart, tilAccept, tilStates, tilAlphabet;
    private CheckBox cbEpsilon, cbTrap;
    private TableLayout tableGrid;

    private final Map<String, EditText> cellEdits = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        View root = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        etStates = findViewById(R.id.etStates);
        etAlphabet = findViewById(R.id.etAlphabet);
        etAccept = findViewById(R.id.etAccept);
        actStart = findViewById(R.id.actStart);
        tilStart = findViewById(R.id.tilStart);
        tilAccept = findViewById(R.id.tilAccept);
        tilStates = findViewById(R.id.tilStates);
        tilAlphabet = findViewById(R.id.tilAlphabet);
        cbEpsilon = findViewById(R.id.cbEpsilon);
        cbTrap = findViewById(R.id.cbTrap);
        tableGrid = findViewById(R.id.tableGrid);

        TextWatcher rebuild = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { rebuildTable(); }
        };
        etStates.addTextChangedListener(rebuild);
        etAlphabet.addTextChangedListener(rebuild);
        cbEpsilon.setOnCheckedChangeListener((b, v) -> rebuildTable());

        findViewById(R.id.btnExample).setOnClickListener(this::showExamples);
        findViewById(R.id.btnClear).setOnClickListener(v -> clearAll());
        findViewById(R.id.btnConvert).setOnClickListener(v -> convert());

        rebuildTable();
    }

    // ------------------------------------------------------------- input parsing

    private static List<String> tokens(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        for (String t : s.trim().split("[,;\\s]+")) {
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    private List<String> stateList() { return tokens(text(etStates)); }
    private List<String> alphabetList() { return tokens(text(etAlphabet)); }

    private static String text(EditText e) {
        return e == null || e.getText() == null ? "" : e.getText().toString();
    }

    // ------------------------------------------------------------- table building

    /** Rebuilds the δ grid from the current states/alphabet/ε flag, keeping typed values. */
    private void rebuildTable() {
        Map<String, String> previous = new HashMap<>();
        for (Map.Entry<String, EditText> e : cellEdits.entrySet()) {
            previous.put(e.getKey(), text(e.getValue()));
        }
        cellEdits.clear();
        tableGrid.removeAllViews();

        List<String> states = stateList();
        List<String> alphabet = alphabetList();
        boolean eps = cbEpsilon.isChecked();

        float d = getResources().getDisplayMetrics().density;
        int m = (int) (3 * d);

        List<String> syms = new ArrayList<>(alphabet);
        if (eps) syms.add(NFA.EPSILON);

        // header row: δ | a | b | (ε)
        TableRow header = new TableRow(this);
        header.addView(headerCell("δ", d, true));
        for (String sym : syms) header.addView(headerCell(sym, d, true));
        tableGrid.addView(header);

        for (String state : states) {
            TableRow row = new TableRow(this);

            TextView label = new TextView(this);
            label.setText(state);
            label.setTextColor(0xFFF2F5FF);
            label.setTextSize(14);
            label.setTypeface(label.getTypeface(), android.graphics.Typeface.BOLD);
            label.setGravity(Gravity.CENTER);
            label.setBackgroundResource(R.drawable.bg_input);
            label.setPadding((int) (10 * d), (int) (12 * d), (int) (10 * d), (int) (12 * d));
            TableRow.LayoutParams lp = new TableRow.LayoutParams();
            lp.setMargins(m, m, m, m);
            label.setLayoutParams(lp);
            row.addView(label);

            for (String sym : syms) {
                EditText cell = new EditText(this);
                cell.setHint("—");
                cell.setTextColor(0xFFF2F5FF);
                cell.setHintTextColor(0xFF5A6480);
                cell.setTextSize(13);
                cell.setSingleLine();
                cell.setGravity(Gravity.CENTER);
                cell.setMinWidth((int) (74 * d));
                cell.setBackgroundResource(R.drawable.bg_input);
                cell.setPadding((int) (8 * d), (int) (10 * d), (int) (8 * d), (int) (10 * d));
                TableRow.LayoutParams lp2 = new TableRow.LayoutParams();
                lp2.setMargins(m, m, m, m);
                cell.setLayoutParams(lp2);
                row.addView(cell);
                String key = state + "\u0001" + sym;
                String old = previous.get(key);
                if (old != null && !old.isEmpty()) cell.setText(old);
                cellEdits.put(key, cell);
            }
            tableGrid.addView(row);
        }

        // keep the start-state dropdown in sync with the current state list
        if (!states.isEmpty()) {
            actStart.setSimpleItems(states.toArray(new String[0]));
            String current = actStart.getText() == null ? "" : actStart.getText().toString().trim();
            if (current.isEmpty() || !states.contains(current)) {
                actStart.setText(states.get(0), false);
            }
        }
        tableGrid.requestLayout();
    }

    private TextView headerCell(String symbol, float d, boolean accent) {
        TextView tv = new TextView(this);
        tv.setText(symbol);
        tv.setTextColor(accent ? 0xFF00E5FF : 0xFF9FB0D0);
        tv.setTextSize(14);
        tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setMinWidth((int) (44 * d));
        tv.setPadding((int) (6 * d), (int) (4 * d), (int) (6 * d), (int) (4 * d));
        return tv;
    }

    // ------------------------------------------------------------- examples

    private void showExamples(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Ends with \"ab\"");
        menu.getMenu().add("Ends with \"abb\"");
        menu.getMenu().add("ε-NFA for (ab)*");
        menu.setOnMenuItemClickListener(item -> {
            String name = item.getTitle().toString();
            switch (name) {
                case "Ends with \"ab\"":
                    loadExample("q0, q1, q2", "a, b", "q0", "q2", false, new String[][]{
                            {"q0", "q0,q1", "q0"},
                            {"q1", "", "q2"},
                            {"q2", "", ""}});
                    break;
                case "Ends with \"abb\"":
                    loadExample("q0, q1, q2, q3", "a, b", "q0", "q3", false, new String[][]{
                            {"q0", "q0,q1", "q0"},
                            {"q1", "", "q2"},
                            {"q2", "", "q3"},
                            {"q3", "", ""}});
                    break;
                case "ε-NFA for (ab)*":
                    loadExample("q0, q1, q2, q3", "a, b", "q0", "q0", true, new String[][]{
                            {"q0", "q1", ""},
                            {"q1", "", ""},
                            {"q2", "", "q3"},
                            {"q3", "", ""}});
                    break;
            }
            // the ε-NFA example also needs its ε column filled in
            if (name.equals("ε-NFA for (ab)*")) {
                setCell("q1", NFA.EPSILON, "q2");
                setCell("q3", NFA.EPSILON, "q0");
            }
            return true;
        });
        menu.show();
    }

    private void loadExample(String states, String alphabet, String start,
                             String accept, boolean eps, String[][] rows) {
        clearAll();
        etStates.setText(states);
        etAlphabet.setText(alphabet);
        actStart.setText(start, false);
        etAccept.setText(accept);
        cbEpsilon.setChecked(eps);
        rebuildTable();
        for (String[] row : rows) {
            setCell(row[0], "a", row[1]);
            setCell(row[0], "b", row[2]);
        }
        if (eps) {
            // epsilon cells were added by rebuildTable above
        }
    }

    private void setCell(String state, String symbol, String value) {
        EditText cell = cellEdits.get(state + "\u0001" + symbol);
        if (cell != null && value != null && !value.isEmpty()) cell.setText(value);
    }

    private void clearAll() {
        etStates.setText("");
        etAlphabet.setText("");
        etAccept.setText("");
        actStart.setText("", false);
        cbEpsilon.setChecked(false);
        cbTrap.setChecked(true);
        rebuildTable();
    }

    // ------------------------------------------------------------- convert

    private void convert() {
        tilStates.setError(null);
        tilAlphabet.setError(null);
        tilStart.setError(null);
        tilAccept.setError(null);

        List<String> states = stateList();
        List<String> alphabet = alphabetList();

        if (states.isEmpty()) { tilStates.setError("Enter at least one state"); return; }
        Set<String> stateSet = new LinkedHashSet<>(states);
        if (stateSet.size() != states.size()) { tilStates.setError("Duplicate state names"); return; }

        if (alphabet.isEmpty()) { tilAlphabet.setError("Enter at least one input symbol"); return; }
        Set<String> alphabetSet = new LinkedHashSet<>(alphabet);
        if (alphabetSet.size() != alphabet.size()) { tilAlphabet.setError("Duplicate symbols"); return; }
        if (alphabetSet.contains(NFA.EPSILON)) { tilAlphabet.setError("Do not name a symbol ε — use the checkbox instead"); return; }

        String start = actStart.getText() == null ? "" : actStart.getText().toString().trim();
        if (start.isEmpty()) { tilStart.setError("Choose a start state"); return; }
        if (!stateSet.contains(start)) { tilStart.setError("Start must be one of the states"); return; }

        List<String> accepts = tokens(text(etAccept));
        if (accepts.isEmpty()) { tilAccept.setError("Enter at least one accepting state"); return; }
        for (String a : accepts) {
            if (!stateSet.contains(a)) { tilAccept.setError("Unknown accepting state: " + a); return; }
        }

        // read + validate the transition table
        boolean eps = cbEpsilon.isChecked();
        List<String> symbols = new ArrayList<>(alphabetSet);
        if (eps) symbols.add(NFA.EPSILON);
        Map<String, Map<String, Set<String>>> transitions = new LinkedHashMap<>();
        for (String state : stateSet) {
            Map<String, Set<String>> row = new LinkedHashMap<>();
            for (String sym : symbols) {
                EditText cell = cellEdits.get(state + "\u0001" + sym);
                List<String> targets = tokens(text(cell));
                for (String t : targets) {
                    if (!stateSet.contains(t)) {
                        if (cell != null) {
                            cell.setError("Unknown state \"" + t + "\"");
                            cell.requestFocus();
                        }
                        Toast.makeText(this, "Transition " + state + " --" + sym + "--> \"" + t + "\" is not a known state",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                }
                if (!targets.isEmpty()) row.put(sym, new LinkedHashSet<>(targets));
            }
            transitions.put(state, row);
        }

        NFA nfa = new NFA(new ArrayList<>(stateSet), new ArrayList<>(alphabetSet), start,
                new LinkedHashSet<>(accepts), eps, transitions);

        Intent intent = new Intent(this, ResultActivity.class);
        intent.putExtra("states", states.toArray(new String[0]));
        intent.putExtra("alphabet", alphabet.toArray(new String[0]));
        intent.putExtra("start", start);
        intent.putExtra("accept", accepts.toArray(new String[0]));
        intent.putExtra("eps", eps);
        intent.putExtra("trap", cbTrap.isChecked());
        ArrayList<String> rows = new ArrayList<>();
        for (Map.Entry<String, Map<String, Set<String>>> e : transitions.entrySet()) {
            for (Map.Entry<String, Set<String>> t : e.getValue().entrySet()) {
                rows.add(e.getKey() + "|" + t.getKey() + "|" + String.join(",", t.getValue()));
            }
        }
        intent.putStringArrayListExtra("trans", rows);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }
}
