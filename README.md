# AutoClickDetector – Intelligent CPS & Pattern Detection Tool  

AutoClickDetector introduces a **smart and adaptive anti-autoclick system** designed for **Paper/Spigot** servers.  
It continuously monitors player click patterns, compares human-like variability, and flags or clears players based on consistent, machine-like behaviour.  

This plugin was built as an experimental and educational tool to explore **statistical pattern detection**, **in-game fairness verification**, and **real-time server analytics**.

---

## Overview  
Upon running the `/autoclick test <player>` command, the system begins analysing the player’s click sequence over a short interval.  
It records each click’s timestamp, calculates click-per-second (CPS), and computes **variance and duplication ratios** to determine whether a player’s input pattern is statistically suspicious.  

The detector applies a custom scoring algorithm that distinguishes legitimate jitter clicking from automated macro or autoclicker input.

---

## Core Features  

### 🔍 Real-Time Pattern Analysis  
- Records exact click intervals for the selected player.  
- Calculates **CPS (clicks per second)**, **coefficient of variation**, and **duplicate ratio**.  
- Identifies robotic or highly repetitive patterns (e.g., identical timing across 30+ seconds).  

### 🧮 Statistical Scoring System  
- Uses a weighted detection rule to evaluate:  
  - Low variance with constant intervals → suspicious.  
  - Perfectly identical duplicate ratios → flagged as autoclicker.  
  - Irregular yet human-like intervals → marked as clear.  
- Logs live results in the console for transparency and debugging.  

### ⚙️ Configurable Parameters  
- Interval duration, minimum click threshold, and tolerance levels can be adjusted in the configuration file.  
- Designed for iterative testing and threshold fine-tuning during server development.  

### 📊 Developer Debug Mode  
- Outputs full raw statistics for each analysed session:  
  - `n` (sample size)  
  - `cps` (clicks per second)  
  - `cv` (coefficient of variation)  
  - `dup` (duplicate ratio)  
- Displays boolean flags for each detection rule (`periodicStrong`, `ruleSuspicious`, etc.)  

---

## Commands  
| Command | Description |  
|----------|--------------|  
| `/autoclick test <player>` | Begins a click-pattern test for the specified player. |  
| `/autoclick reload` | Reloads configuration values without restarting the server. |  

---

## Technical  
- **Minecraft:** Spigot/Paper 1.21.1  
- **Language:** Java 21  
- **Build Tool:** Maven  
- **Dependencies:** None (pure Java implementation)  

---

## Installation  
1. Build the plugin with Maven (`mvn clean package`) or use the provided JAR file.  
2. Place the compiled JAR inside your server’s `plugins/` folder.  
3. Restart or reload the server.  
4. Use `/autoclick test <player>` to begin detection.  
5. Review live console output or adjust thresholds in the configuration file for tuning.  

---

## Author  
This plugin was developed by **Penta** as a prototype for pattern-recognition and anti-automation systems.  
All rights reserved.  
