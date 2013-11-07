# Solution for lab2

all: .compiled

JUNIT4 = /usr/share/java/junit4.jar
SOURCE := $(wildcard src/ewoks/*.java)

.compiled: $(SOURCE)
	@mkdir -p bin
	javac -d bin -Xlint:unchecked $(SOURCE)
	touch $@

test: force
	for f in test/*; do echo -n "$$f: "; runtest $$f; done

clean: force
	rm -f .compiled bin/ewoks/*.class

force:
