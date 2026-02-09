# Pyjama v0.4.0 Video Demo Commands
# Copy and paste these commands for your video recording

# NOTE: Run these commands from the demos/cli-demo directory
# The pyjama binary is at ../../pyjama

# ============================================
# 1. SHOW HELP
# ============================================
../../pyjama --help


# ============================================
# 2. STREAMING TEXT (real-time tokens)
# ============================================
../../pyjama -m llama3.2 -p "Write a haiku about coding"


# ============================================
# 3. NON-STREAMING TEXT (complete response)
# ============================================
../../pyjama -m llama3.2 -s false -p "What is 2+2?"


# ============================================
# 4. VISION ANALYSIS (image understanding)
# ============================================
../../pyjama -m llava -i image.jpg -p "Describe this image in detail"


# ============================================
# 5. IMAGE GENERATION (with progress)
# ============================================
../../pyjama -o robot.png -w 256 -g 256 -p "A futuristic robot"
open robot.png


# ============================================
# 6. SAVE TO MARKDOWN
# ============================================
../../pyjama -m llama3.2 -o explanation.md -p "Explain functional programming in one paragraph"
open explanation.md


# ============================================
# 7. CHATGPT COMPARISON (optional)
# ============================================
clj -M:llm -l chatgpt -p "What is Clojure in one sentence?"


# ============================================
# BONUS: Show binary info
# ============================================
ls -lh pyjama
file pyjama
