# Autometa: Android NFA-to-DFA Converter

An interactive Android application built in Java that converts Non-Deterministic Finite Automata (NFA), including ε-NFAs, into equivalent Deterministic Finite Automata (DFA) using the **Rabin–Scott Subset Construction Algorithm** with animated, step-by-step visualizations.

---

## Key Features

- **NFA-to-DFA Conversion:** Converts NFA and ε-NFA into equivalent DFA.
- **ε-Closure Computation:** Handles epsilon transitions during state discovery.
- **Step-by-Step Visualization:** Displays individual stages of the conversion process.
- **Interactive Automata Graphs:** Visualizes input NFA and generated DFA.
- **Transition Tables:** Displays automata transitions in structured tables.
- **Animation Controls:** Navigate through conversion steps.
- **Dynamic Input Grid:** Configure states, alphabets, and transitions.
- **Input Validation:** Detects invalid and incomplete automata definitions.
- **Trap State Handling:** Supports optional empty-set trap state (∅).
- **Preset Examples:** Includes predefined automata for experimentation.

---

## Tech Stack

| Component | Technology |
| :--- | :--- |
| Language | Java |
| IDE | Android Studio |
| Platform | Android SDK |
| UI Framework | Material Components |
| Graph Rendering | Android Canvas |
| Algorithm | Rabin–Scott Subset Construction |
| Build System | Gradle |

---

## Project Structure

```text
app/
└── src/
    └── main/
        ├── java/
        │   └── .../
        │       ├── model/
        │       │   ├── NFA.java
        │       │   └── SubsetConstruction.java
        │       ├── view/
        │       │   └── AutomatonView.java
        │       ├── MainActivity.java
        │       └── ResultActivity.java
        ├── res/
        └── AndroidManifest.xml


## ** Getting Started**

### **1. Clone the Repository**
```bash
git clone https://github.com/Darth-sader/NFA-to-DFA-Converter.git
cd NFA-to-DFA-Converter

### **2. Build & Run**

1. Open the project in Android Studio.
2. Allow Gradle synchronization to complete.
3. Run the application on an Android emulator or physical device.

## ** Algorithm**

**Rabin–Scott Subset Construction**

1. Calculate the ε-closure of the initial NFA state.
2. Create the initial DFA state from the resulting set.
3. Calculate transitions for each input symbol.
4. Discover new reachable state subsets.
5. Repeat until all discovered states are processed.
6. Identify accepting DFA states based on the original NFA's final states.

The resulting DFA is equivalent to the input NFA.

## ** Concepts Covered**

- Finite Automata
- NFA and DFA
- ε-NFA
- Transition Functions
- ε-Closure
- Subset Construction
- Reachability and Accepting States
