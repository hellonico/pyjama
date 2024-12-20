
# use in a different project

```clojure
{
 :deps
 {org.clojure/clojure {:mvn/version "1.11.0"}
 hellonico/pyjama {:git/url "https://github.com/hellonico/pyjama.git"
                   :sha "990b35deb81dd8ee1c6a2813ee9ea8e4651178c5"}
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