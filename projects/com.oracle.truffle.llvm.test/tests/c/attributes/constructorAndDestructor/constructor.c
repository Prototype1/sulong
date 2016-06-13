#include <stdio.h>

int main() {
    puts("hello world!");
}

__attribute__((constructor))
void constr() {
    puts("constructor called");
}

