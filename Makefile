
jdk21 ?= $(error Please specify the jdk21=... home path)
jdk21bin = ${jdk21}/bin/java

repl:
	JDK21=${jdk21bin} lein repl

.PHONY: test
test:
	JDK21=${jdk21bin} lein test

release:
	JDK21=${jdk21bin} lein release

toc-install:
	npm install --save markdown-toc

toc-build:
	node_modules/.bin/markdown-toc -i readme.md
