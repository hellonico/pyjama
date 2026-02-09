#!/bin/bash
# Pyjama v0.4.0 Demo Script
# Showcases all major features of the Ollama CLI

set -e

# Path to pyjama binary (relative to this script)
PYJAMA="../.$PYJAMA"

echo "🎬 Pyjama v0.4.0 Demo - Native Binary Showcase"
echo "=============================================="
echo ""

# 1. Show help
echo "📖 1. Show help and available options"
echo "   Command: $PYJAMA --help"
echo ""
$PYJAMA --help
echo ""
read -p "Press Enter to continue..."
clear

# 2. Streaming text generation (default)
echo "🌊 2. Streaming Text Generation (real-time tokens)"
echo "   Command: $PYJAMA -m llama3.2 -p \"Write a haiku about coding\""
echo ""
$PYJAMA -m llama3.2 -p "Write a haiku about coding"
echo ""
echo ""
read -p "Press Enter to continue..."
clear

# 3. Non-streaming text generation
echo "⏸️  3. Non-Streaming Text Generation (wait for complete response)"
echo "   Command: $PYJAMA -m llama3.2 -s false -p \"What is 2+2?\""
echo ""
$PYJAMA -m llama3.2 -s false -p "What is 2+2?"
echo ""
read -p "Press Enter to continue..."
clear

# 4. Vision analysis with streaming
echo "👁️  4. Vision Analysis with LLaVA (image understanding)"
echo "   Command: $PYJAMA -m llava -i image.jpg -p \"Describe this image in detail\""
echo ""
$PYJAMA -m llava -i image.jpg -p "Describe this image in detail"
echo ""
echo ""
read -p "Press Enter to continue..."
clear

# 5. Image generation (small for demo)
echo "🎨 5. Image Generation with Progress Spinner"
echo "   Command: $PYJAMA -o demo_art.png -w 256 -g 256 -p \"A futuristic robot\""
echo ""
$PYJAMA -o demo_art.png -w 256 -g 256 -p "A futuristic robot"
echo ""
echo "   Opening generated image..."
open demo_art.png
echo ""
read -p "Press Enter to continue..."
clear

# 6. Save to markdown
echo "📝 6. Save Response to Markdown File"
echo "   Command: $PYJAMA -m llama3.2 -o demo_response.md -p \"Explain functional programming\""
echo ""
$PYJAMA -m llama3.2 -o demo_response.md -p "Explain functional programming in one paragraph"
echo ""
echo "   Opening markdown file..."
open demo_response.md
echo ""
read -p "Press Enter to continue..."
clear

# 7. ChatGPT comparison (if available)
echo "🤖 7. ChatGPT API (for comparison)"
echo "   Command: clj -M:llm -l chatgpt -p \"What is Clojure?\""
echo ""
echo "   Note: Requires OPENAI_API_KEY in ~/.secrets/secrets.edn"
echo "   Skipping if not configured..."
echo ""
if clj -M:llm -l chatgpt -s false -p "What is Clojure in one sentence?" 2>/dev/null; then
    echo ""
else
    echo "   (ChatGPT not configured - skipping)"
fi
echo ""
read -p "Press Enter to continue..."
clear

# Final summary
echo "✅ Demo Complete!"
echo "================"
echo ""
echo "Features demonstrated:"
echo "  ✓ Streaming text generation (real-time)"
echo "  ✓ Non-streaming text generation"
echo "  ✓ Vision analysis with LLaVA"
echo "  ✓ Image generation with progress tracking"
echo "  ✓ Markdown output"
echo "  ✓ Native binary (instant startup, no JVM)"
echo ""
echo "Binary size: $(ls -lh pyjama | awk '{print $5}')"
echo "Build time: ~2 minutes"
echo "Startup time: Instant (no JVM warmup)"
echo ""
echo "🎉 Thank you for watching!"
