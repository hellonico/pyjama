# Pyjama CLI Demo

This folder contains demo scripts and commands for showcasing Pyjama v0.4.0 features.

## Setup

1. **Build the native binary** (from project root):
   ```bash
   cd ../..
   clj -M:native-cli
   ```

2. **Navigate to this demo folder**:
   ```bash
   cd demos/cli-demo
   ```

## Running the Demo

### Option 1: Automated Script
```bash
./demo-script.sh
```
This interactive script walks through all features with prompts.

### Option 2: Manual Commands
Use the commands from `DEMO_COMMANDS.md` - copy and paste as you record.

## Features Demonstrated

1. **📖 Help & Options** - Show all available flags
2. **🌊 Streaming Text** - Real-time token generation
3. **⏸️ Non-Streaming Text** - Complete response at once
4. **👁️ Vision Analysis** - Image understanding with LLaVA
5. **🎨 Image Generation** - Create images with progress tracking
6. **📝 Markdown Output** - Save responses to formatted files
7. **🤖 Multi-Provider** - ChatGPT, Ollama, and more

## Requirements

- **Ollama** running locally (`http://localhost:11434`)
- **Models installed**:
  ```bash
  ollama pull llama3.2
  ollama pull llava
  ollama pull x/z-image-turbo
  ```
- **Sample image**: `image.jpg` (included)

## Binary Info

- **Size**: ~75MB
- **Build time**: ~2 minutes
- **Startup**: Instant (no JVM warmup)
- **Dependencies**: None (standalone executable)

## Recording Tips

- Clear terminal between commands: `clear`
- Use small image sizes (256x256) for faster generation
- Test each command before recording
- Highlight the instant startup time!

## Files

- `demo-script.sh` - Automated demo script
- `DEMO_COMMANDS.md` - Manual command list
- `image.jpg` - Sample image for vision demo
- `README.md` - This file
