# Pyjama Directory Loading Feature

## ✅ Feature Complete!

Successfully added support for loading all EDN agent files from directories via the `-Dagents.edn` system property.

## 🚀 Usage

### Load from a single directory
```bash
clj -J-Dagents.edn=test-resources/agentic -M:pyjama inspect
```

### Load from multiple directories (comma-separated)
```bash
clj -J-Dagents.edn=dir1,dir2,dir3 -M:pyjama inspect
```

### Mix files and directories
```bash
clj -J-Dagents.edn=agents.edn,test-resources/agentic,custom/agents.edn -M:pyjama inspect
```

## 📋 What Was Changed

### 1. **pyjama/cli/agent.clj** (Lines 16-76)
Added `load-agents-from-directory` function and enhanced `load-agents-config`:
- Supports single file paths
- Supports directory paths (loads all `.edn` files)
- Supports comma-separated lists of files/directories
- Provides helpful console output showing what's being loaded

### 2. **pyjama/core.clj** (Lines 249-295)
Enhanced `load-agents` function:
- Integrated directory loading into the core registry
- Maintains priority order (system property > cwd > classpath)
- Properly merges configurations from multiple sources

## 🎯 Benefits

1. **Modular Agent Development**: Organize agents in separate files
2. **Easy Testing**: Point to test agent directories without modifying code
3. **Multi-Project Support**: Load agents from multiple projects simultaneously
4. **Clean Separation**: Keep different agent sets in different directories

## 📊 Test Results

```
✓ Vault plugin auto-registered (VAULT_ADDR detected)
📂 Loading 10 agent file(s) from: test-resources/agentic
  ✓ Loaded: code.edn
  ✓ Loaded: first.edn
  ✓ Loaded: movie.edn
  ✓ Loaded: news.edn
  ✓ Loaded: party.edn
  ✓ Loaded: partypdf.edn
  ✓ Loaded: tool-loading-test.edn
  ✓ Loaded: web.edn
  ✓ Loaded: wiki-to-file.edn
  ✓ Loaded: wiki.edn

✓ Found 14 agents
```

## 💡 Examples

### Development Workflow
```bash
# Test new agents without affecting production
clj -J-Dagents.edn=dev/agents -M:pyjama run my-test-agent '{}'
```

### Multi-Project Analysis
```bash
# Load agents from multiple projects
clj -J-Dagents.edn=../project-a/agents,../project-b/agents -M:pyjama inspect
```

### Custom Agent Collections
```bash
# Load specific agent collections
clj -J-Dagents.edn=agents/core,agents/experimental,agents/custom -M:pyjama list
```

## 🔧 Implementation Details

### File Loading Order
1. **Classpath resources** (lowest priority)
   - `resources/agents.edn`
   - `resources/agents/` directory
2. **Current working directory**
   - `./agents.edn`
   - `./agents/` directory
3. **System property** (highest priority)
   - `-Dagents.edn=path` (file, directory, or comma-separated)

### Merging Strategy
- Uses `deep-merge` to combine configurations
- Later sources override earlier ones
- Agent definitions with same ID are merged

### Error Handling
- Silently skips non-existent paths
- Prints warnings for loading errors
- Continues loading remaining files on error

## 📝 Notes

- Files are loaded in alphabetical order within each directory
- Only `.edn` files are loaded from directories
- Whitespace in comma-separated paths is trimmed
- Empty or invalid paths are skipped

## 🎉 Status

**Feature Status**: ✅ Complete and Tested
**Pyjama Version**: v0.2.0+
**Compatibility**: Backward compatible with existing single-file loading

---

This feature enables more flexible agent organization and makes it easier to work with multiple agent collections simultaneously!
