# Secrets Management

Comprehensive secrets management for Pyjama agents with support for **file-based** and **Vault** storage.

## Why Secrets Management?

When building agents that interact with APIs (OpenAI, GitLab, Plane, etc.), you need a secure way to manage credentials. The Pyjama secrets library provides:

- **Multiple sources**: Files (plain/encrypted), Vault, environment variables
- **Priority-based lookup**: Override secrets per environment
- **Encryption**: AES-GCM encryption for local files
- **Environment-aware**: Automatic environment-specific configuration

## Quick Start

### Installation

```clojure
;; deps.edn
{:deps {hellonico/secrets {:git/url "https://github.com/hellonico/secrets"
                          :git/sha "d73f472"}}}
```

### Basic Usage

```clojure
(require '[secrets.core :as secrets])

;; Get a secret (searches all sources)
(secrets/get-secret :openai-api-key)
;; => "sk-..."

;; Get nested secret
(secrets/get-secret [:gitlab :token])
;; => "glpat-..."

;; Get all secrets
(secrets/all-secrets)
;; => {:openai-api-key "sk-..." :gitlab {:token "glpat-..."}}
```

## File-Based Secrets

### Plain EDN Files

Create `secrets.edn` in your project or home directory:

```clojure
{:openai-api-key "sk-..."
 :gitlab {:token "glpat-..."
          :url "https://gitlab.com"}
 :plane {:api-key "plane_..."
         :workspace-slug "my-workspace"}}
```

### Encrypted Files

For sensitive production secrets, use encrypted files:

```clojure
(require '[secrets.core :as secrets])

;; Set passphrase (or use SECRETS_PASSPHRASE env var)
(System/setenv "SECRETS_PASSPHRASE" "your-secure-passphrase")

;; Write encrypted secrets
(secrets/write-encrypted-secrets! 
  "~/secrets.edn.enc"
  {:openai-api-key "sk-..."
   :gitlab {:token "glpat-..."}})
```

The library automatically reads from `secrets.edn.enc` if it exists.

### Priority Order

Secrets are loaded from multiple sources (rightmost wins):

1. `~/secrets.edn` (home plain)
2. `~/secrets.edn.enc` (home encrypted)
3. `./secrets.edn` (project plain)
4. `./secrets.edn.enc` (project encrypted)
5. **Environment variables** (highest priority)

Example environment variable:
```bash
export SECRET__OPENAI__API_KEY="sk-..."
# Maps to [:openai :api-key]
```

## Vault Integration

For enterprise deployments, use HashiCorp Vault:

### Setup

```bash
# Set Vault configuration
export VAULT_ADDR="https://vault.example.com"
export VAULT_TOKEN="hvs.your-token"
```

### Usage

```clojure
(require '[secrets.plugins.vault :as vault])

;; Configure Vault (reads from env vars)
(def config (vault/vault-config))

;; Read secret from Vault
(vault/read-secret config "secret" "pyjama/openai")
;; => {:api-key "sk-..."}

;; Write secret to Vault
(vault/write-secret! config "secret" "pyjama/gitlab"
                     {:token "glpat-..." :url "https://gitlab.com"})

;; List secrets
(vault/list-secrets config "secret" "pyjama")
;; => ["openai" "gitlab" "plane"]

;; Load all Vault secrets into the library
(vault/vault->secrets-map config "secret" "pyjama")
;; => {:openai-api-key "sk-..." :gitlab-token "glpat-..." ...}
```

### Vault Namespaces (Enterprise)

```bash
export VAULT_NAMESPACE="production"
```

### Vault with Environments

When `ENV_NAME` is set, Vault paths are automatically prefixed:

```bash
export ENV_NAME=staging

# Without ENV_NAME:
# vault/read-secret reads from: secret/data/pyjama/database

# With ENV_NAME=staging:
# vault/read-secret reads from: secret/data/staging/pyjama/database
```

## Environment-Based Configuration

Manage secrets across **development**, **staging**, and **production** environments:

### Setup

```bash
export ENV_NAME=staging
```

### File Priority with Environments

When `ENV_NAME=staging`, files are loaded in this order:

1. `~/secrets.edn` (common)
2. `~/secrets.edn.enc` (common encrypted)
3. `~/secrets.staging.edn` (staging-specific)
4. `~/secrets.staging.edn.enc` (staging-specific encrypted)
5. `./secrets.edn` (project common)
6. `./secrets.edn.enc` (project common encrypted)
7. `./secrets.staging.edn` (project staging)
8. `./secrets.staging.edn.enc` (project staging encrypted)
9. `SECRET__*` (generic env vars)
10. `SECRET_STAGING__*` (staging env vars - highest priority)

### Example Setup

**Common secrets** (`secrets.edn`):
```clojure
{:openai-api-key "sk-dev-key"
 :database {:host "localhost" :port 5432}}
```

**Staging overrides** (`secrets.staging.edn`):
```clojure
{:openai-api-key "sk-staging-key"
 :database {:host "staging-db.example.com" :password "staging-pass"}}
```

**Production overrides** (environment variables):
```bash
export ENV_NAME=production
export SECRET_PRODUCTION__OPENAI__API_KEY="sk-prod-key"
export SECRET_PRODUCTION__DATABASE__PASSWORD="prod-pass"
```

## Using Secrets in Agents

### Basic Agent with Secrets

```clojure
;; agents.edn
{:gitlab-agent
 {:start :create-issue
  :tools {:create-issue {:fn my.tools/create-gitlab-issue}}
  :steps
  {:create-issue
   {:tool :create-issue
    :args {:token "{{secrets.gitlab.token}}"
           :url "{{secrets.gitlab.url}}"
           :title "{{ctx.title}}"}
    :next :done}}}}
```

The `{{secrets.*}}` template syntax automatically retrieves secrets.

### Tool Implementation

```clojure
(ns my.tools
  (:require [secrets.core :as secrets]))

(defn create-gitlab-issue [ctx args]
  (let [token (or (:token args)
                  (secrets/get-secret [:gitlab :token]))
        url (or (:url args)
                (secrets/get-secret [:gitlab :url]))]
    ;; Use token and url to create GitLab issue
    ...))
```

## Security Best Practices

### 1. Never Commit Secrets

Add to `.gitignore`:
```gitignore
secrets.edn
secrets.*.edn
secrets.edn.enc
secrets.*.edn.enc
.env
```

### 2. Use Encrypted Files

```bash
# Development: plain files (gitignored)
./secrets.edn

# Staging/Production: encrypted files
./secrets.staging.edn.enc
./secrets.production.edn.enc
```

### 3. Environment Variables for CI/CD

```yaml
# GitHub Actions
env:
  SECRET__OPENAI__API_KEY: ${{ secrets.OPENAI_API_KEY }}
  SECRET__GITLAB__TOKEN: ${{ secrets.GITLAB_TOKEN }}
```

### 4. Vault for Production

```bash
# Production setup
export VAULT_ADDR="https://vault.prod.example.com"
export VAULT_TOKEN="hvs.prod-token"
export ENV_NAME=production
```

### 5. Rotate Secrets Regularly

Use Vault's secret rotation features or update encrypted files periodically.

## Examples

### Jetlag Agent (Email → Plane)

```clojure
;; secrets.edn
{:email {:username "support@example.com"
         :password "email-pass"}
 :plane {:api-key "plane_..."
         :workspace-slug "mycompany"}}

;; jetlag-agent.edn
{:jetlag-agent
 {:start :watch-emails
  :tools {:watch-emails {:fn jetlag/watch-emails}
          :create-issue {:fn jetlag/create-plane-issue}}
  :steps
  {:watch-emails
   {:tool :watch-emails
    :args {:username "{{secrets.email.username}}"
           :password "{{secrets.email.password}}"}
    :next :create-issues}
   
   :create-issues
   {:loop-over [:obs :emails]
    :loop-body :create-one
    :next :done}
   
   :create-one
   {:tool :create-issue
    :args {:api-key "{{secrets.plane.api-key}}"
           :workspace "{{secrets.plane.workspace-slug}}"
           :email "{{loop-item}}"}
    :next :done}}}}
```

### Multi-Environment GitLab Agent

```bash
# Development
export ENV_NAME=dev
# Uses secrets.dev.edn with personal GitLab token

# Staging
export ENV_NAME=staging  
# Uses secrets.staging.edn with staging GitLab token

# Production
export ENV_NAME=production
export VAULT_ADDR="https://vault.prod.example.com"
export VAULT_TOKEN="hvs.prod-token"
# Uses Vault at: secret/data/production/pyjama/gitlab
```

## API Reference

### Core Functions

```clojure
(require '[secrets.core :as secrets])

;; Retrieve secrets
(secrets/get-secret :api-key)              ; Flat key
(secrets/get-secret [:gitlab :token])      ; Nested path

;; Get all secrets
(secrets/all-secrets)

;; Reload from all sources
(secrets/reload-secrets!)

;; Write encrypted file
(secrets/write-encrypted-secrets! path secret-map)
```

### Vault Functions

```clojure
(require '[secrets.plugins.vault :as vault])

;; Configuration
(vault/vault-config)                       ; From env vars
(vault/vault-config addr token)            ; Explicit

;; Read/Write
(vault/read-secret config mount path)
(vault/write-secret! config mount path data)

;; List and convert
(vault/list-secrets config mount path)
(vault/vault->secrets-map config mount path)
```

## Troubleshooting

### Secret Not Found

```clojure
(secrets/get-secret :missing-key)
;; => nil

;; Check all loaded secrets
(secrets/all-secrets)
;; => {...}

;; Reload from sources
(secrets/reload-secrets!)
```

### Encryption Errors

```bash
# Ensure passphrase is set
export SECRETS_PASSPHRASE="your-passphrase"

# Or pass explicitly
(secrets/write-encrypted-secrets! 
  "secrets.edn.enc" 
  data 
  "your-passphrase")
```

### Vault Connection Issues

```bash
# Test connection
curl -H "X-Vault-Token: $VAULT_TOKEN" \
  $VAULT_ADDR/v1/sys/health

# Check configuration
(vault/vault-config)
;; => {:vault-addr "..." :vault-token "..."}
```

## Further Reading

- [Secrets Library Repository](https://github.com/hellonico/secrets)
- [Vault Plugin Documentation](https://github.com/hellonico/secrets/blob/main/VAULT_PLUGIN.md)
- [Environment-Based Secrets](https://github.com/hellonico/secrets/blob/main/ENV_BASED_SECRETS.md)
- [Plugin Architecture](https://github.com/hellonico/secrets/blob/main/PLUGIN_ARCHITECTURE.md)

## See Also

- [Agent Framework](LOOP_SUPPORT.md) - Build agents that use secrets
- [Examples](../examples/) - Agent examples with secret integration
- [Jetlag Agent](https://github.com/hellonico/pyjama-commercial/tree/main/jetlag) - Production agent using secrets
