# Mermaid Diagram Generator Example

A simple Pyjama agent example demonstrating how to generate Mermaid diagrams using LLM.

## Overview

This example shows the basic pattern for:
- Using LLM to generate Mermaid diagram code
- Saving diagrams to markdown files
- Creating multiple diagram types in one workflow

## Usage

```bash
# Generate example diagrams
clj -M:pyjama run mermaid-diagram-generator \
  '{"project-dir":".", "output-dir":"./diagrams"}'
```

## Generated Diagrams

The agent creates three example diagrams:

1. **Component Architecture** (`component-diagram.md`)
   - Graph showing system components
   - Frontend, backend, database, external APIs
   - Color-coded with subgraphs

2. **User Authentication Flow** (`sequence-diagram.md`)
   - Sequence diagram of login process
   - Shows interaction between user, frontend, backend, database

3. **Deployment Pipeline** (`deployment-flow.md`)
   - Flowchart of CI/CD process
   - Includes decision points and error handling

## Pattern

The key pattern demonstrated:

```clojure
{:generate-diagram
 {:prompt "Generate a Mermaid 'graph TB' diagram showing...\n\nReturn ONLY the Mermaid code without markdown fences."
  :next :save-diagram}

 :save-diagram
 {:tool :write-file
  :args {:path "{{output-dir}}/diagram.md"
         :content "# Title\n\n```mermaid\n{{last-obs}}\n```\n"}
  :next :next-step}}
```

## For Production Use

For comprehensive codebase analysis and architecture diagram generation, see:
**[codebase-analyzer](https://github.com/hellonico/codebase-analyzer)** with the `mermaid-architecture-generator` agent.

That implementation includes:
- Project structure analysis
- Service/module discovery
- API endpoint detection
- Multiple diagram types
- Professional styling

## Viewing Diagrams

### GitHub
Mermaid renders natively - just push and view!

### VS Code
Install "Markdown Preview Mermaid Support" extension

### Online
Visit [mermaid.live](https://mermaid.live/) and paste diagram code

### CLI
```bash
npm install -g @mermaid-js/mermaid-cli
mmdc -i diagram.md -o diagram.png
```

## Customization

Edit the prompts in the agent to generate different diagram types:
- Entity-Relationship diagrams
- State diagrams
- Gantt charts
- Class diagrams
- Git graphs

See [Mermaid documentation](https://mermaid.js.org/) for all diagram types.

---

**Note**: This is a simple example for demonstration. For production-grade codebase analysis and diagram generation, use the codebase-analyzer project.
