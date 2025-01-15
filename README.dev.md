
# use in a different project

```clojure
{
 :deps
 {hellonico/pyjama {:git/url "https://github.com/hellonico/pyjama.git"
                   :sha "ec753eebffd422fd2d02eb1c93957a9f8adb0016"}}}
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

# Run the cool tests

```clojure
clj -X:test :patterns '["morning.query-test"]'
```