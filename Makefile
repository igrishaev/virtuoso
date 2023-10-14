
jdk21 ?= $(error Please specify the jdk21=... home path)

repl:
	JDK21=${jdk21}/bin/java lein repl


.PHONY: test
test:
	lein test


release:
	lein release
