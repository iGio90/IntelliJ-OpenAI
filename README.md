## Intellij and OpenAI

straight in the code editor. 

works with any intelliJ powered IDE, including Android Studio.

### Setup

1) download the jar from the [release page](https://github.com/iGio90/IntelliJ-OpenAI/releases).
2) install as a regular plugin from disk ([read here](https://www.jetbrains.com/help/idea/managing-plugins.html#install_plugin_from_disk))
3) click the tool menu -> OpenAI Preferences and insert your api key

### Or build it yourself

```
./gradlew shadowJar

get the jar from build/libs/xxx.jar
```

----

### How to

comment in your editor with that format:

```python
# code a function that sum 2 numbers.

# code a class which has 2 static string named X and Y and its getter and setters.
```


and so on for all other languages. 

this will use different models according to the need.

----

### keywords:

#### generating code:
``code`` query ``.`` or ``generate code`` query

``add`` query ``.`` or ``create`` query ``.``

#### documenting classes and methods 
``document`` query ``.`` or ```generate doc``` 

right before the code to document

#### fixes for empty lines, indent, lint and code style
``apply lint`` or ``apply style`` 

**NOTE:** all the keywords listed together perform the same action

----