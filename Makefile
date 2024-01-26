
jdk21 ?= $(error Please specify the jdk21=... home path)

repl:
	JAVA_HOME=${jdk21} lein repl

.PHONY: test
test:
	JAVA_HOME=${jdk21} lein test

release:
	JAVA_HOME=${jdk21} lein release

toc-install:
	npm install --save markdown-toc

toc-build:
	node_modules/.bin/markdown-toc -i readme.md
