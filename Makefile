check:
	mvn clean compile

test:
	mvn clean test -P coverage
