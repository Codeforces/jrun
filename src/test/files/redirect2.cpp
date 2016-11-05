#include <iostream>
#include <cstdlib>
#include <cstdio>

using namespace std;

const int N = 65536;
char b[N];

int main(int argc, char* argv[])
{
    while (true)
    {
        size_t doneBytes = fread(b, sizeof(char), N, stdin);

        size_t writtenBytes = fwrite(b, sizeof(char), doneBytes, stdout);
        if (writtenBytes != doneBytes)
        {
            perror("IO error (2)!");
            return 1;
        }
        fflush(stdout);

        writtenBytes = fwrite(b, sizeof(char), doneBytes, stderr);
        if (writtenBytes != doneBytes)
        {
            perror("IO error (2)!");
            return 1;
        }
        fflush(stderr);

        
        if (doneBytes != sizeof(char) * N)
        {
            if (!feof(stdin))
            {
                perror("IO error (1)");
                return 1;
            }
            return 0;
        }
    }
    
    return 0;
}
