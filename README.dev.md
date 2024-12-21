
# use in a different project

```clojure
{
 :deps
 {org.clojure/clojure {:mvn/version "1.11.0"}
 hellonico/pyjama {:git/url "https://github.com/hellonico/pyjama.git"
                   :sha "9b0647cf72462bcd690ec28a01a97486a1e886ec"}
                         }}
```

# Compile a generate client binary

```bash
# this needs the GRAAL VM to be installed
clj -A:native-image
```

Then

```bash
./pyjama --images resources/cute_cat.jpg -m "llava" -p "What is in the picture?"
```

or, if the ollama server is on a different machine:

```bash
./pyjama  -p "Why is the sky blue?" -u http://localhost:11435
```