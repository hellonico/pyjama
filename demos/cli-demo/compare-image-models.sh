#!/bin/bash
# Compare three image generation models
# Models: jmorgan/z-image-turbo, x/z-image-turbo, x/flux2-klein

set -e

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PYJAMA="$SCRIPT_DIR/../../pyjama"

PROMPT="A futuristic robot in a cyberpunk city"
SIZE="512x512"

echo "🎨 Image Model Comparison"
echo "=========================="
echo "Prompt: $PROMPT"
echo "Size: $SIZE"
echo ""

# Model 1: jmorgan/z-image-turbo
echo "1️⃣  Testing jmorgan/z-image-turbo"
echo "-----------------------------------"
$PYJAMA --pull -m jmorgan/z-image-turbo -o jmorgan_z_image_turbo.png -w 512 -g 512 -p "$PROMPT"
echo ""

# Model 2: x/z-image-turbo
echo "2️⃣  Testing x/z-image-turbo"
echo "-----------------------------------"
$PYJAMA --pull -m x/z-image-turbo -o x_z_image_turbo.png -w 512 -g 512 -p "$PROMPT"
echo ""

# Model 3: x/flux2-klein
echo "3️⃣  Testing x/flux2-klein"
echo "-----------------------------------"
$PYJAMA --pull -m x/flux2-klein -o x_flux2_klein.png -w 512 -g 512 -p "$PROMPT"
echo ""

echo "✅ Comparison Complete!"
echo "======================="
echo "Generated images:"
echo "  - jmorgan_z_image_turbo.png"
echo "  - x_z_image_turbo.png"
echo "  - x_flux2_klein.png"
echo ""
echo "Opening images..."
open jmorgan_z_image_turbo.png x_z_image_turbo.png x_flux2_klein.png
