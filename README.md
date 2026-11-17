# NFA to DFA Converter - Android Educational Simulator

## Overview
An Android-based educational simulator for converting Non-Deterministic Finite Automata (NFAs) into equivalent Deterministic Finite Automata (DFAs) via subset construction, featuring interactive graph visualization and step-by-step construction replay.

## Supported Features
* NFA specification input (states, alphabet, start state, final states, transitions).
* Optional $\epsilon$-transition support with automated $\epsilon$-closure computation.
* Subset-construction algorithm engine with canonical duplicate subset detection.
* Interactive graph rendering, transition table generation, and construction step replay.
* Optional trap/dead state configuration for incomplete DFAs.

## Technology Stack & Dependencies
* **Language:** Java (Android SDK)
* **Minimum SDK:** API 24 (Android 7.0)
* **Target SDK:** API 34+
* **Build System:** Gradle

## Installation & Setup
1. Clone the repository: `git clone <repository-url>`
2. Open the project folder in **Android Studio**.
3. Sync Gradle dependencies (`File > Sync Project with Gradle Files`).

## How to Run
* Connect an Android device with USB Debugging enabled or start an Android Emulator (Pixel API 34 recommended).
* Click the **Run 'app'** (green play button) in Android Studio.

## Example Input & Output
* **Alphabet:** `0,1`
* **States:** `q0,q1,q2`
* **Start State:** `q0`
* **Accepting States:** `q2`
* **Transitions:** `q0 -> 0: {q0,q1}, 1: {q0} \| q1 -> 1: {q2}`
* **Output:** Generates DFA states $A, B, C, D$ with transition table and acceptance flags.

## Project Structure
* `MainActivity.java` — Input collection and form validation.
* `NFA.java` — Automaton data model, `move()`, and $\epsilon$-closure logic.
* `SubsetConstruction.java` — Powerset engine, queue processing, and step recording.
* `ResultActivity.java` — Result coordination and playback controller.
* `AutomatonView.java` — Custom Canvas graph renderer with zoom/pan.

## Testing
* Run unit tests via Gradle: `./gradlew test` inside the terminal.

## Known Limitations & Troubleshooting
* Very large NFAs ($n > 5$ states) may experience exponential subset growth ($2^n$). Graph layouts automatically scale but may become dense; use pinch-to-zoom for navigation.
