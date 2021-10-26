#include <iostream>
#include <cstdlib>
#include <cstdio>

using namespace std;

int main(int argc, char* argv[])
{
    if (argc != 4)
    {
        printf("Usage: gray <n> <outputGrayStringLevel> <errorGrayStringLevel>\n\n");
        return 1;
    }

    /* To slow down the program: 15 ~1 second, 15 ~4 seconds, and so on... */
    int n = atoi(argv[1]);
    
    int sum = 0;
    for (int i = 0; i < (1 << n); i++)
        for (int j = 0; j < (1 << n); j++)
            sum += (i & (j + 1)) * 2;
    if (sum % 2 == 1)
        return 1;
        
    int outputGrayStringLevel = atoi(argv[2]);
    {
        string s = "a";
        for (int i = 0; i < outputGrayStringLevel; i++)
            s = s + char('a' + i + 1) + s;
        fprintf(stdout, "%s", s.c_str());    
    }
    
    
    int errorGrayStringLevel = atoi(argv[3]);
    {
        string s = "a";
        for (int i = 0; i < errorGrayStringLevel; i++)
            s = s + char('a' + i + 1) + s;
        fprintf(stderr, "%s", s.c_str());    
    }
    
    return 0;
}
