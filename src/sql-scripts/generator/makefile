CXX = g++
WXX = clang++
CXXFLAGS = -std=c++17 -Wall -Wextra -pedantic-errors -fsanitize=undefined,address -g

all: generator

windows: generator_w

generator: generator.o
		${CXX} ${CXXFLAGS} generator.o -o generate

generator_w: generator_w.o
		${WXX} ${CXXFLAGS} generator.o -o generate

generator.o: generator.cpp common.h
		${CXX} ${CXXFLAGS} -c generator.cpp

generator_w.o: generator.cpp common.h
		${WXX} ${CXXFLAGS} -c generator.cpp

run: generator
		./generate

.PHONY: clean
clean:
	rm -f *.o
	rm -f generate
	rm -f ../sql/Script.sql
	rm -f ../sql/Reset.sql