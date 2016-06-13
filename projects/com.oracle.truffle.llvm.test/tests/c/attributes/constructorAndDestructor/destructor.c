#include <stdio.h>

static const char* END = "This is the end";  

int main() {
    puts("hello world!");
}

__attribute__((destructor))
void destr() {
    puts("destructor called");
    puts(END);
}
