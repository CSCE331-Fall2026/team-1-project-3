CXX = g++
WXX = clang++
CXXFLAGS = -std=c++17 -Wall -Wextra -pedantic-errors -fsanitize=undefined,address -g

all: generator

windows: generator_w

security.o: 
		${CXX} ${CXXFLAGS} -c security.cpp -lcrypto

security_w.o:
		${WXX} ${CXXFLAGS} -c security.cpp -lcrypto

generator: generator.o
		${CXX} ${CXXFLAGS} generator.o -o generate -lcrypto

generator_w: generator_w.o
		${WXX} ${CXXFLAGS} generator.o -o generate -lcrypto

generator.o: generator.cpp common.h security.o
		${CXX} ${CXXFLAGS} -c generator.cpp -lcrypto

generator_w.o: generator.cpp common.h security_w.o
		${WXX} ${CXXFLAGS} -c generator.cpp -lcrypto

run: generator
		./generate

.PHONY: clean
clean:
	rm -f *.o
	rm -f generate
	rm -f ../sql/Script.sql
	rm -f ../sql/Reset.sql