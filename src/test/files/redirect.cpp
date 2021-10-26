#include <iostream>
#include <cstdlib>
#include <cstdio>

using namespace std;

int main(int argc, char* argv[])
{
    char c;
    
    while ((c = getc(stdin)) != EOF)
    {
        putc(c, stdout);
        putc(c, stderr);
    }
    
    return 0;
}
