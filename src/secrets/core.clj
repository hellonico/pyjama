(ns secrets.core
 (:require [clojure.edn :as edn]
           [clojure.java.io :as io]
           [clojure.string :as str])
 (:import [java.io PushbackReader ByteArrayOutputStream]
          [java.security SecureRandom]
          [javax.crypto Cipher SecretKeyFactory]
          [javax.crypto.spec PBEKeySpec SecretKeySpec GCMParameterSpec]))

;; ---------- utils

(defn- load-edn-file [path]
 (let [f (io/file path)]
  (when (.exists f)
   (with-open [r (PushbackReader. (io/reader f))]
    (edn/read r)))))

(defn- env-name [k]
 (-> (if (keyword? k) (name k) (str/join "-" (map name k)))
     (str/upper-case)
     (str/replace "-" "_")))

(defn- deep-merge
 "Rightmost wins; maps merge recursively."
 [& ms]
 (letfn [(m2 [a b]
          (merge-with
           (fn [x y]
            (if (and (map? x) (map? y))
             (m2 x y)
             y))
           a b))]
  (reduce m2 {} ms)))

;; ---------- encryption (AES-GCM + PBKDF2)

(def ^:private ^:const salt-bytes 16)
(def ^:private ^:const iv-bytes 12)
(def ^:private ^:const tag-bits 128)
(def ^:private ^:const iters 100000)
(def ^:private ^:const key-bits 256)

(defn- pbkdf2-key [pass salt]
 (let [skf (SecretKeyFactory/getInstance "PBKDF2WithHmacSHA256")
       spec (PBEKeySpec. (.toCharArray pass) salt iters key-bits)
       bytes (.getEncoded (.generateSecret skf spec))]
  (SecretKeySpec. bytes "AES")))

(defn- encrypt-bytes [pass plaintext-bytes]
 (let [rng (SecureRandom.)
       salt (byte-array salt-bytes)
       iv (byte-array iv-bytes)
       _ (.nextBytes rng salt)
       _ (.nextBytes rng iv)
       key (pbkdf2-key pass salt)
       c (doto (Cipher/getInstance "AES/GCM/NoPadding")
          (.init Cipher/ENCRYPT_MODE key (GCMParameterSpec. tag-bits iv)))
       ct (.doFinal c plaintext-bytes)]
  ;; file = SALT || IV || CIPHERTEXT
  (-> (doto (ByteArrayOutputStream.)
       (.write salt)
       (.write iv)
       (.write ct))
      (.toByteArray))))

(defn- decrypt-bytes [pass all]
 (let [salt (java.util.Arrays/copyOfRange all 0 salt-bytes)
       iv (java.util.Arrays/copyOfRange all salt-bytes (+ salt-bytes iv-bytes))
       ct (java.util.Arrays/copyOfRange all (+ salt-bytes iv-bytes) (alength all))
       key (pbkdf2-key pass salt)
       c (doto (Cipher/getInstance "AES/GCM/NoPadding")
          (.init Cipher/DECRYPT_MODE key (GCMParameterSpec. tag-bits iv)))]
  (.doFinal c ct)))

(defn- read-encrypted-edn [path pass]
 (let [f (io/file path)]
  (when (.exists f)
   (let [bytes (with-open [in (io/input-stream f)]
                (.readAllBytes in))
         plain (String. ^bytes (decrypt-bytes pass bytes) "UTF-8")]
    (edn/read-string plain)))))

(defn write-encrypted-secrets!
 "Write EDN map `m` to `path` (e.g. \"~/secrets.edn.enc\") encrypted with SECRETS_PASSPHRASE."
 [path m]
 (let [pp (System/getenv "SECRETS_PASSPHRASE")]
  (when (or (nil? pp) (str/blank? pp))
   (throw (ex-info "SECRETS_PASSPHRASE is not set" {})))
  (let [edn-text (pr-str m)
        bytes (encrypt-bytes pp (.getBytes edn-text "UTF-8"))
        f (io/file (or (some-> path io/file .getPath) path))]
   (when-let [p (.getParentFile f)] (.mkdirs p))
   (with-open [out (io/output-stream f)]
    (.write out bytes))
   path)))

;; ---------- sources

(defn- home [rel] (str (System/getProperty "user.home") "/" rel))

(defn- env->secrets-map
 "Only consumes env vars prefixed with SECRET__.
  Example: SECRET__BRAVE__API_KEY -> [:brave :api-key]"
 []
 (let [env (System/getenv)]
  (reduce-kv
   (fn [m k v]
    (if (str/starts-with? k "SECRET__")
     (let [ks (->> (str/split (subs k (count "SECRET__")) #"__")
                   (map #(-> % str/lower-case (str/replace "_" "-") keyword))
                   vec)]
      (assoc-in m ks v))
     m))
   {} env)))

(defn- maybe-read-encrypted [path]
 (let [pp (System/getenv "SECRETS_PASSPHRASE")]
  (when (and (not (str/blank? pp)) (.exists (io/file path)))
   (read-encrypted-edn path pp))))

(defn- load-all-sources []
 (let [local-plain (load-edn-file "secrets.edn")
       local-enc (maybe-read-encrypted "secrets.edn.enc")
       home-plain (load-edn-file (home "secrets.edn"))
       home-enc (maybe-read-encrypted (home "secrets.edn.enc"))
       env-map (env->secrets-map)]
  ;; Priority: home → local → env (env wins overall)
  (deep-merge home-plain home-enc local-plain local-enc env-map)))

(def ^:private state
 (atom {:merged (load-all-sources)}))

(defn reload-secrets! []
 (swap! state assoc :merged (load-all-sources))
 (:merged @state))

(defn all-secrets []
 (:merged @state))

;; ---------- lookup

(defn- get-in* [m k-or-path]
 (if (vector? k-or-path) (get-in m k-or-path) (get m k-or-path)))

(defn get-secret
 "Lookup order:
  1) merged map (files + SECRET__* env)
  2) direct ENV fallback by conventional name (e.g. BRAVE_API_KEY or BRAVE_API_KEY for [:brave :api-key])"
 [k-or-path]
 (or
  (get-in* (:merged @state) k-or-path)
  (System/getenv (env-name k-or-path))))
