# Secrets Management

Pyjama integrates with the **[Secrets Library](https://github.com/hellonico/secrets)** - a comprehensive, **100% open-source** secrets management solution for Clojure.

## Features

- **📁 File-Based**: Plain and encrypted EDN files (`secrets.edn`, `secrets.edn.enc`)
- **🔐 Vault Integration**: Full HashiCorp Vault support (KV v2 engine)
- **🌍 Environment-Aware**: Automatic dev/staging/production separation
- **🔄 Priority Merging**: Override secrets from multiple sources
- **🔒 AES-GCM Encryption**: Secure local file encryption
- **🆓 100% Open Source**: MIT/EPL licensed

## Quick Start

```clojure
;; deps.edn
{:deps {hellonico/secrets {:git/url "https://github.com/hellonico/secrets"
                          :git/sha "d73f472"}}}

;; Get secrets in your agents
(require '[secrets.core :as secrets])

(secrets/get-secret [:openai :api-key])     ; => "sk-..."
(secrets/get-secret [:gitlab :token])        ; => "glpat-..."
```

## File-Based Secrets

Create `secrets.edn`:

```clojure
{:openai {:api-key "sk-..."}
 :gitlab {:token "glpat-..." :url "https://gitlab.com"}
 :plane {:api-key "plane_..." :workspace-slug "mycompany"}}
```

## Vault Integration (Open Source)

```clojure
(require '[secrets.plugins.vault :as vault])

(def config (vault/vault-config))  ; From VAULT_ADDR/VAULT_TOKEN

(vault/read-secret config "secret" "pyjama/openai")
;; => {:api-key "sk-..."}
```

## Environment-Based Configuration

```bash
export ENV_NAME=staging

# Automatically uses:
# - File: secrets.staging.edn
# - Vault: secret/data/staging/pyjama/*
# - Env vars: SECRET_STAGING__*
```

## Using in Agents

```clojure
;; agents.edn
{:my-agent
 {:start :call-api
  :steps
  {:call-api
   {:prompt "Use this API key: {{secrets.openai.api-key}}"
    :next :done}}}}
```

Template syntax `{{secrets.*}}` automatically retrieves secrets.

## Complete Documentation

**Full documentation, examples, and source code:**

👉 **[https://github.com/hellonico/secrets](https://github.com/hellonico/secrets)**

**Includes:**
- Complete API reference
- Vault plugin documentation
- Environment-based secrets guide
- Docker Vault setup
- Plugin architecture
- Security best practices

**100% Open Source** - MIT/EPL Licensed
