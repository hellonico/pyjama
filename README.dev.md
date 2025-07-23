
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
clj -M:native-image
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

```bash
clj -X:test :patterns '["morning.*"]'
```

# Command to upload a new modelfile to ollama form a gguf link

```bash
 clj -M -m pyjama.ollama.cli https://huggingface.co/lm-kit/qwen-2.5-math-7.6b-instruct-gguf/resolve/main/Qwen-2.5-Math-7.6B-Instruct-Q6_K.gguf hellonico 
```