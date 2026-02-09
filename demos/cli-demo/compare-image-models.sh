#!/bin/bash
# Compare three image generation models
# Models: jmorgan/z-image-turbo, x/z-image-turbo, x/flux2-klein

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PYJAMA="$SCRIPT_DIR/../../pyjama"

# Check if OLLAMA_HOST is set, otherwise use default
if [ -z "$OLLAMA_HOST" ]; then
    echo "💡 OLLAMA_HOST not set, using default: http://localhost:11434"
    echo "   Set OLLAMA_HOST to use a different server:"
    echo "   export OLLAMA_HOST=http://your-server:11434"
    echo ""
fi

PROMPT="A futuristic robot in a cyberpunk city"
SIZE="512x512"

echo "🎨 Image Model Comparison"
echo "=========================="
echo "Prompt: $PROMPT"
echo "Size: $SIZE"
echo ""

# Function to test a model
test_model() {
    local model=$1
    local output=$2
    local number=$3
    
    echo "${number}  Testing $model"
    echo "-----------------------------------"
    
    # Try to generate image with auto-pull
    if $PYJAMA --pull -m "$model" -o "$output" -w 512 -g 512 -p "$PROMPT"; then
        echo "✅ Success: $output"
    else
        echo "❌ Failed to generate with $model"
        echo "   Model may not be available on this server"
        return 1
    fi
    echo ""
}

# Model 1: jmorgan/z-image-turbo
test_model "jmorgan/z-image-turbo" "jmorgan_z_image_turbo.png" "1️⃣" || true

# Model 2: x/z-image-turbo
test_model "x/z-image-turbo" "x_z_image_turbo.png" "2️⃣" || true

# Model 3: x/flux2-klein
test_model "x/flux2-klein" "x_flux2_klein.png" "3️⃣" || true

echo "✅ Comparison Complete!"
echo "======================="
echo "Generated images:"

# List successfully generated images
for img in jmorgan_z_image_turbo.png x_z_image_turbo.png x_flux2_klein.png; do
    if [ -f "$img" ]; then
        echo "  ✓ $img"
    else
        echo "  ✗ $img (failed)"
    fi
done

echo ""

# Open successfully generated images
IMAGES=""
for img in jmorgan_z_image_turbo.png x_z_image_turbo.png x_flux2_klein.png; do
    if [ -f "$img" ]; then
        IMAGES="$IMAGES $img"
    fi
done

if [ -n "$IMAGES" ]; then
    echo "Opening images..."
    open $IMAGES
else
    echo "⚠️  No images were successfully generated"
fi
